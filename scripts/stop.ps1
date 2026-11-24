. (Join-Path $PSScriptRoot 'launcher.ps1')

$lock = $null
try {
    $lock = Lock-Launcher
    $state = Read-State
    if ($state) {
        Stop-ManagedComponents $state
        Remove-Item -LiteralPath $StateFile
        Write-Host 'RootMe is stopped.'
    } else {
        Write-Host 'No RootMe instance started by the launcher is running.'
    }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
} finally {
    if ($lock) { $lock.Dispose() }
}
