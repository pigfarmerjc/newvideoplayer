$ErrorActionPreference = 'Continue'

$workspace = 'C:\Users\13326\Desktop\newvideoplayer'
$backupDir = Join-Path $workspace 'windows-repair-backup'
$logPath = Join-Path $backupDir 'step1.log'

New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
Start-Transcript -Path $logPath -Force

Write-Host 'Backing up service registry keys...'
reg.exe export 'HKLM\SYSTEM\CurrentControlSet\Services\SmartDispatcher' (Join-Path $backupDir 'SmartDispatcher.reg') /y
reg.exe export 'HKLM\SYSTEM\CurrentControlSet\Services\GGProtect64' (Join-Path $backupDir 'GGProtect64.reg') /y

Write-Host 'Disabling Wondershare services...'
sc.exe config SmartDispatcher start= disabled
sc.exe stop SmartDispatcher
sc.exe config GGProtect64 start= disabled
sc.exe stop GGProtect64

Write-Host 'Current Wondershare service state:'
sc.exe qc SmartDispatcher
sc.exe queryex SmartDispatcher
sc.exe qc GGProtect64
sc.exe queryex GGProtect64

Write-Host 'Attempting to start Microsoft Defender...'
sc.exe start WinDefend
Start-Sleep -Seconds 8
sc.exe queryex WinDefend

Stop-Transcript
