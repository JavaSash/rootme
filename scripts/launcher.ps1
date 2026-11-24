$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $RepoRoot '.rootme-run'
$Logs = Join-Path $RepoRoot 'logs'
$StateFile = Join-Path $RunDir 'processes.json'
$script:DockerExe = $null
$script:DockerContext = $null

function Lock-Launcher {
    [void][IO.Directory]::CreateDirectory($RunDir)
    try {
        return [IO.File]::Open((Join-Path $RunDir 'launcher.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch [IO.IOException] {
        throw 'Another RootMe start or stop is already in progress.'
    }
}

function Read-State {
    if (!(Test-Path -LiteralPath $StateFile)) { return $null }
    try {
        $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        if ($state.version -ne 1 -or !$state.project -or !$state.token -or !$state.backendPort -or !$state.frontendPort) { throw 'Invalid state' }
        return $state
    } catch {
        throw "Cannot read launcher state at $StateFile. Check the running RootMe processes before removing it."
    }
}

function Save-State($state) {
    $temporary = "$StateFile.tmp"
    [IO.File]::WriteAllText($temporary, ($state | ConvertTo-Json -Depth 5))
    Move-Item -LiteralPath $temporary -Destination $StateFile -Force
}

function Process-Identity($process) {
    return @{ id = $process.Id; started = $process.StartTime.ToUniversalTime().Ticks.ToString() }
}

function Get-ManagedProcess($identity) {
    if (!$identity) { return $null }
    $process = Get-Process -Id $identity.id -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $identity.started) {
        return $process
    }
}

function Get-Docker {
    $command = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidate = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    throw 'Docker CLI was not found. Install Docker Desktop or add docker.exe to PATH.'
}

function Docker-Args([string[]]$arguments) {
    if ($script:DockerContext) { return @('--context', $script:DockerContext) + $arguments }
    return $arguments
}

function Invoke-Docker([string[]]$arguments, [switch]$Quiet) {
    $effective = Docker-Args $arguments
    $ErrorActionPreference = 'Continue'
    $output = & $script:DockerExe @effective 2>$null
    if ($LASTEXITCODE -ne 0) {
        if ($Quiet) { return $null }
        throw "Docker command failed: docker $($arguments[0]). Check Docker Desktop and the logs."
    }
    return $output
}

function Compose-Args([string]$project, [string[]]$arguments) {
    $options = @('compose', '--project-directory', $RepoRoot, '-f', (Join-Path $RepoRoot 'docker-compose.yml'))
    if ($project) { $options += @('-p', $project) }
    return $options + $arguments
}

function Find-Postgres([string]$project) {
    $id = Invoke-Docker (Compose-Args $project @('ps', '--all', '--quiet', 'postgres'))
    if (!$id) { return $null }
    if (@($id).Count -ne 1) { throw 'Multiple PostgreSQL containers match the RootMe Compose project.' }
    $container = (Invoke-Docker @('inspect', $id) | Out-String | ConvertFrom-Json)[0]
    $owner = $container.Config.Labels.'com.docker.compose.project.working_dir'
    $separators = [char[]]'\/'
    if (!$owner -or $owner.TrimEnd($separators) -ine $RepoRoot.TrimEnd($separators)) {
        throw "The Compose project $project belongs to another directory."
    }
    return $container
}

function Assert-PortFree([int]$port, [string]$component) {
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if ($listeners.Port -contains $port) {
        $owners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
        $suffix = if ($owners.Count) { " (PID $($owners -join ', '))" } else { '' }
        throw "$component cannot start: port $port is occupied$suffix."
    }
}

function Start-Logged([string]$name, [string]$exe, [string]$arguments, [string]$workingDirectory) {
    [void][IO.Directory]::CreateDirectory($Logs)
    $parameters = @{
        FilePath = $exe; WorkingDirectory = $workingDirectory; WindowStyle = 'Hidden'; PassThru = $true
        RedirectStandardOutput = Join-Path $Logs "$name.log"
        RedirectStandardError = Join-Path $Logs "$name.error.log"
    }
    if ($arguments) { $parameters.ArgumentList = $arguments }
    return Start-Process @parameters
}

function Quote-Arguments([string[]]$arguments) {
    return ($arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
}

function Run-Logged([string]$name, [string]$exe, [string]$arguments, [string]$workingDirectory, [int]$seconds) {
    $process = Start-Logged $name $exe $arguments $workingDirectory
    # Retain the native handle so Windows PowerShell can read the exit code after the process exits.
    $null = $process.Handle
    if (!$process.WaitForExit($seconds * 1000)) {
        & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F *> $null
        throw "$name timed out. See logs/$name.log and logs/$name.error.log."
    }
    if ($process.ExitCode -ne 0) { throw "$name failed with exit code $($process.ExitCode). See logs/$name.log and logs/$name.error.log." }
}

function Wait-Ready([string]$name, [int]$seconds, [scriptblock]$check, $identity = $null) {
    $deadline = [Diagnostics.Stopwatch]::StartNew()
    do {
        if ($identity -and !(Get-ManagedProcess $identity)) { throw "$name exited before becoming ready. Check the logs." }
        if (& $check) { return }
        Start-Sleep -Seconds 2
    } while ($deadline.Elapsed.TotalSeconds -lt $seconds)
    throw "$name did not become ready within $seconds seconds. Check the logs."
}

function Get-LauncherStatus($state) {
    try {
        $uri = "http://127.0.0.1:$($state.backendPort)/api/v1/local/launcher"
        return Invoke-RestMethod -Uri $uri -TimeoutSec 3 -Headers @{ 'X-RootMe-Launcher' = $state.token }
    } catch { return $null }
}

function Test-Http([string]$uri) {
    try { return (Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 }
    catch { return $false }
}

function Stop-ManagedComponents($state, [switch]$FailedStartup) {
    $front = Get-ManagedProcess $state.frontend
    if ($front) {
        Write-Host 'Stopping frontend...'
        Stop-Process -InputObject $front
        if (!$front.WaitForExit(10000)) { throw 'Frontend is still running. PostgreSQL was left running.' }
    }

    $backend = Get-ManagedProcess $state.backend
    if ($backend) {
        Write-Host 'Stopping backend gracefully...'
        if ((Get-LauncherStatus $state).pid -eq $backend.Id) {
            try {
                $uri = "http://127.0.0.1:$($state.backendPort)/api/v1/local/launcher/shutdown"
                Invoke-WebRequest -UseBasicParsing -Method Post -TimeoutSec 5 -Uri $uri -Headers @{ 'X-RootMe-Launcher' = $state.token } | Out-Null
            } catch { Write-Host 'Waiting for the backend shutdown to finish...' }
        } elseif ($FailedStartup) {
            Stop-Process -InputObject $backend
        }
        if (!$backend.WaitForExit(90000)) {
            throw 'Backend is still running. PostgreSQL was left running. Check backend logs and run stop again.'
        }
    }

    if ($state.database -and (!$FailedStartup -or !$state.databaseWasRunning)) {
        if (!$backend -and !$FailedStartup) { Assert-PortFree $state.backendPort 'PostgreSQL shutdown while an unmanaged backend is listening' }
        Write-Host 'Stopping RootMe PostgreSQL...'
        if ([string]$env:DOCKER_HOST -ne [string]$state.dockerHost) { throw 'DOCKER_HOST changed. Restore the original setting before stopping PostgreSQL.' }
        $script:DockerExe = Get-Docker
        $script:DockerContext = $state.dockerContext
        $postgres = Find-Postgres $state.project
        if ($postgres) {
            $arguments = Docker-Args (Compose-Args $state.project @('down'))
            Run-Logged 'postgres-stop' $script:DockerExe (Quote-Arguments $arguments) $RepoRoot 120
        }
    }
}

function Open-RootMe([string]$url) {
    try { Start-Process $url }
    catch { Write-Warning "Could not open the browser. Visit $url." }
}
