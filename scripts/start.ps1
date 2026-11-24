param([switch]$NoBrowser)

. (Join-Path $PSScriptRoot 'launcher.ps1')

function Find-Jdk {
    $candidates = @($env:JAVA_HOME)
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { $candidates += Split-Path -Parent (Split-Path -Parent $javac.Source) }
    foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto')) {
        $directory = Join-Path $env:ProgramFiles $vendor
        $candidates += @(Get-ChildItem -LiteralPath $directory -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
    foreach ($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $compiler = Join-Path $candidate 'bin\javac.exe'
        if (!(Test-Path -LiteralPath $compiler -PathType Leaf) -or !(Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe') -PathType Leaf)) { continue }
        $ErrorActionPreference = 'Continue'
        $version = & $compiler -version 2>&1
        $ErrorActionPreference = 'Stop'
        if ($LASTEXITCODE -eq 0 -and "$version" -match '^javac 17(?:\.|$)') {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'JDK 17 was not found. Install it or set JAVA_HOME to a JDK 17 directory.'
}

function Find-Node {
    $candidates = @($env:NODE_HOME, (Join-Path $env:ProgramFiles 'nodejs'))
    foreach ($name in @('node.exe', 'npm.cmd')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { $candidates = @(Split-Path -Parent $command.Source) + $candidates }
    }
    $jetBrains = Join-Path $env:LOCALAPPDATA 'JetBrains'
    foreach ($ide in (Get-ChildItem -LiteralPath $jetBrains -Directory -Filter 'IntelliJIdea*' -ErrorAction SilentlyContinue)) {
        $runtimes = Join-Path $ide.FullName 'acp-agents\.runtimes\node'
        $candidates += @(Get-ChildItem -LiteralPath $runtimes -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
    foreach ($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $node = Join-Path $candidate 'node.exe'
        if (!(Test-Path -LiteralPath $node -PathType Leaf) -or !(Test-Path -LiteralPath (Join-Path $candidate 'npm.cmd') -PathType Leaf)) { continue }
        $ErrorActionPreference = 'Continue'
        $version = & $node --version 2>$null
        $ErrorActionPreference = 'Stop'
        if ($LASTEXITCODE -eq 0 -and $version -match '^v(\d+)\.(\d+)\.') {
            if ([int]$Matches[1] -eq 24 -or ([int]$Matches[1] -eq 22 -and [int]$Matches[2] -ge 12)) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }
    throw 'Compatible Node.js and npm.cmd were not found. Install Node.js 24 or 22.12+ and add it to PATH.'
}

function Ensure-FrontendDependencies($frontend) {
    $stamp = Join-Path $frontend 'node_modules\.rootme-lock.sha256'
    $hash = (Get-FileHash -LiteralPath (Join-Path $frontend 'package-lock.json')).Hash
    $package = Get-Content -LiteralPath (Join-Path $frontend 'package.json') -Raw | ConvertFrom-Json
    $missing = @($package.dependencies.PSObject.Properties.Name) + @($package.devDependencies.PSObject.Properties.Name) |
        Where-Object { !(Test-Path -LiteralPath (Join-Path $frontend "node_modules/$_/package.json")) }
    if ($missing -or !(Test-Path -LiteralPath (Join-Path $frontend 'node_modules/vite/bin/vite.js')) -or
        !(Test-Path -LiteralPath $stamp) -or (Get-Content -LiteralPath $stamp -Raw).Trim() -ne $hash) {
        Write-Host 'Installing frontend dependencies (npm ci)...'
        Run-Logged 'npm-ci' $env:ComSpec '/d /s /c "npm.cmd ci"' $frontend 300
        [IO.File]::WriteAllText($stamp, $hash)
    }
}

function Get-Port([string]$value, [int]$default, [string]$name) {
    if (!$value) { return $default }
    $port = 0
    if (![int]::TryParse($value, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
        throw "$name must be a valid TCP port."
    }
    return $port
}

$lock = $null
$state = $null
try {
    $lock = Lock-Launcher
    $previous = Read-State
    if ($previous) {
        $previousUrl = "http://127.0.0.1:$($previous.frontendPort)"
        $back = Get-ManagedProcess $previous.backend
        $front = Get-ManagedProcess $previous.frontend
        if ($back -and $front -and (Get-LauncherStatus $previous).pid -eq $back.Id -and (Test-Http "$previousUrl/api/v1/persons?size=1")) {
            Write-Host "RootMe is already running at $previousUrl."
            if (!$NoBrowser) { Open-RootMe $previousUrl }
            return
        }
        if ($back -or $front) {
            throw 'A previously launched component is still running. Run stop.bat before restarting.'
        }
    }

    Write-Host 'Checking Docker, JDK 17 and Node.js...'
    $script:DockerExe = Get-Docker
    $env:JAVA_HOME = Find-Jdk
    $nodeHome = Find-Node
    $env:Path = "$nodeHome;$env:Path"
    $backendPort = Get-Port $env:SERVER_PORT 8080 'SERVER_PORT'
    $frontendPort = Get-Port $env:ROOTME_FRONTEND_PORT 5173 'ROOTME_FRONTEND_PORT'
    if ($backendPort -eq $frontendPort) { throw 'Use different TCP ports for the backend and frontend.' }
    $frontendUrl = "http://127.0.0.1:$frontendPort"
    Assert-PortFree $backendPort 'Backend'
    Assert-PortFree $frontendPort 'Frontend'

    if (!(Invoke-Docker @('info', '--format', '{{.ServerVersion}}') -Quiet)) {
        $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
        if ((Test-Path -LiteralPath $desktop -PathType Leaf) -and !(Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue)) {
            Write-Host 'Starting Docker Desktop...'
            Start-Process -FilePath $desktop -WindowStyle Hidden
        }
    }
    Write-Host 'Waiting for Docker engine...'
    Wait-Ready 'Docker engine' 150 { Invoke-Docker @('info', '--format', '{{.ServerVersion}}') -Quiet }
    if (!$env:DOCKER_HOST) { $script:DockerContext = Invoke-Docker @('context', 'show') }

    $directoryName = Split-Path -Leaf $RepoRoot
    $dotenv = Join-Path $RepoRoot '.env'
    $hasProject = (Test-Path -LiteralPath $dotenv -PathType Leaf) -and
        (Select-String -LiteralPath $dotenv -Pattern '^\s*(export\s+)?COMPOSE_PROJECT_NAME\s*=' -Quiet)
    if (!$env:COMPOSE_PROJECT_NAME -and !($directoryName -match '[a-zA-Z0-9]') -and
        !$hasProject) {
        $pathBytes = [Text.Encoding]::UTF8.GetBytes($RepoRoot.ToLowerInvariant())
        $hasher = [Security.Cryptography.SHA256]::Create()
        try { $suffix = ([BitConverter]::ToString($hasher.ComputeHash($pathBytes))).Replace('-', '').Substring(0, 12).ToLowerInvariant() }
        finally { $hasher.Dispose() }
        $env:COMPOSE_PROJECT_NAME = "rootme-$suffix"
    }
    $configuration = Invoke-Docker (Compose-Args '' @('config', '--format', 'json')) | Out-String | ConvertFrom-Json
    $project = $configuration.name
    $dbPorts = @($configuration.services.postgres.ports | Where-Object { $_.target -eq 5432 })
    if (!$project -or $dbPorts.Count -ne 1 -or !$dbPorts[0].published) { throw 'Could not determine the RootMe Compose project and PostgreSQL port.' }
    if ($previous -and ($previous.project -ne $project -or $previous.dockerContext -ne $script:DockerContext -or [string]$previous.dockerHost -ne [string]$env:DOCKER_HOST)) {
        throw 'The Docker project or connection changed. Run stop.bat with the previous settings first.'
    }
    $dbPort = [int]$dbPorts[0].published
    if ($dbPort -in @($backendPort, $frontendPort)) { throw 'PostgreSQL must use a separate port.' }
    $postgres = Find-Postgres $project
    $running = $postgres -and $postgres.State.Running
    $published = $false
    if ($running) {
        $published = @($postgres.NetworkSettings.Ports.'5432/tcp' | Where-Object { [int]$_.HostPort -eq $dbPort }).Count -gt 0
    }
    if (!$published) { Assert-PortFree $dbPort 'PostgreSQL' }
    if (!$env:DB_URL) { $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($dbPort)/rootme" }
    if (!$env:DB_PASSWORD) { $env:DB_PASSWORD = $configuration.services.postgres.environment.POSTGRES_PASSWORD }

    $state = @{
        version = 1; token = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        project = $project; dockerContext = $script:DockerContext; dockerHost = [string]$env:DOCKER_HOST
        backendPort = $backendPort; frontendPort = $frontendPort; backend = $null; frontend = $null
        database = $true; databaseWasRunning = [bool]$running
    }
    Save-State $state

    Write-Host "Starting PostgreSQL on port $dbPort..."
    $arguments = Docker-Args (Compose-Args $project @('up', '-d', '--wait', '--wait-timeout', '90', 'postgres'))
    Run-Logged 'postgres-start' $script:DockerExe (Quote-Arguments $arguments) $RepoRoot 120
    Wait-Ready 'PostgreSQL' 30 { Invoke-Docker (Compose-Args $project @('exec', '-T', 'postgres', 'pg_isready', '-U', 'rootme', '-d', 'rootme')) -Quiet }

    Write-Host 'Building backend with the Gradle Wrapper...'
    $gradle = '/d /s /c "gradlew.bat --no-daemon "-Dorg.gradle.java.home=%JAVA_HOME%" bootJar"'
    Run-Logged 'gradle' $env:ComSpec $gradle $RepoRoot 360
    $jar = Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'build/libs') -Filter 'rootme-*.jar' -File |
        Where-Object { $_.Name -notlike '*-plain.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (!$jar) { throw 'The Gradle Wrapper did not produce a runnable RootMe JAR.' }

    Write-Host "Starting backend on port $backendPort..."
    $env:ROOTME_LAUNCHER_ENABLED = 'true'
    $env:ROOTME_LAUNCHER_TOKEN = $state.token
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $backend = Start-Logged 'backend' $java (Quote-Arguments @('-jar', $jar.FullName)) $RepoRoot
    $state.backend = Process-Identity $backend
    Save-State $state
    Remove-Item Env:ROOTME_LAUNCHER_TOKEN
    Remove-Item Env:ROOTME_LAUNCHER_ENABLED
    Wait-Ready 'Backend' 90 {
        (Get-LauncherStatus $state).pid -eq $state.backend.id -and
            (Test-Http "http://127.0.0.1:$backendPort/api/v1/persons?size=1")
    } $state.backend

    $frontendDirectory = Join-Path $RepoRoot 'frontend'
    Ensure-FrontendDependencies $frontendDirectory
    Write-Host 'Starting frontend...'
    $vite = Join-Path $frontendDirectory 'node_modules/vite/bin/vite.js'
    $front = Start-Logged 'frontend' (Join-Path $nodeHome 'node.exe') (Quote-Arguments @($vite)) $frontendDirectory
    $state.frontend = Process-Identity $front
    Save-State $state
    Wait-Ready 'Frontend' 45 { (Test-Http "$FrontendUrl/") -and (Test-Http "$FrontendUrl/api/v1/persons?size=1") } $state.frontend
    Write-Host "RootMe is ready at $frontendUrl."
    if (!$NoBrowser) { Open-RootMe $frontendUrl }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    if ($state) {
        try {
            Stop-ManagedComponents $state -FailedStartup
            Remove-Item -LiteralPath $StateFile -ErrorAction SilentlyContinue
        } catch {
            [Console]::Error.WriteLine("Startup cleanup is incomplete: $($_.Exception.Message)")
        }
    }
    exit 1
} finally {
    if ($lock) { $lock.Dispose() }
}
