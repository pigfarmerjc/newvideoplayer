$ErrorActionPreference = 'Continue'

$workspace = 'C:\Users\13326\Desktop\newvideoplayer'
$backupDir = Join-Path $workspace 'windows-repair-backup'
$logPath = Join-Path $backupDir 'step2.log'

New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
Start-Transcript -Path $logPath -Force

Write-Host 'Backing up crash dump configuration...'
reg.exe export 'HKLM\SYSTEM\CurrentControlSet\Control\CrashControl' (Join-Path $backupDir 'CrashControl.reg') /y

Write-Host 'Configuring small memory dumps...'
reg.exe add 'HKLM\SYSTEM\CurrentControlSet\Control\CrashControl' /v CrashDumpEnabled /t REG_DWORD /d 3 /f
reg.exe add 'HKLM\SYSTEM\CurrentControlSet\Control\CrashControl' /v MinidumpDir /t REG_EXPAND_SZ /d '%SystemRoot%\Minidump' /f
reg.exe add 'HKLM\SYSTEM\CurrentControlSet\Control\CrashControl' /v LogEvent /t REG_DWORD /d 1 /f
reg.exe add 'HKLM\SYSTEM\CurrentControlSet\Control\CrashControl' /v AutoReboot /t REG_DWORD /d 1 /f

Write-Host 'Running DISM RestoreHealth...'
DISM.exe /Online /Cleanup-Image /RestoreHealth
$dismExit = $LASTEXITCODE
Write-Host "DISM exit code: $dismExit"

Write-Host 'Running System File Checker...'
sfc.exe /scannow
$sfcExit = $LASTEXITCODE
Write-Host "SFC exit code: $sfcExit"

Write-Host 'Attempting Defender signature update...'
$mpCmdRun = Join-Path $env:ProgramFiles 'Windows Defender\MpCmdRun.exe'
if (Test-Path -LiteralPath $mpCmdRun) {
    & $mpCmdRun -SignatureUpdate
    Write-Host "MpCmdRun exit code: $LASTEXITCODE"
}

Write-Host 'Attempting Defender service start...'
sc.exe start WinDefend
Start-Sleep -Seconds 10
sc.exe queryex WinDefend

Stop-Transcript
