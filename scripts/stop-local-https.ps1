$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$file=Join-Path $repo '.gradle/local-https/processes.json'
if(!(Test-Path -LiteralPath $file)){Write-Output 'No recorded local HTTPS processes.';return}
$state=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
foreach($item in @(@($state.caddyPid,$state.caddyPath,'relay/deploy/Caddyfile.local'),@($state.nodePid,$state.nodePath,'relay/server.mjs'))){
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($item[0])"
    if(!$process){continue}
    if($process.ExecutablePath -ne $item[1] -or $process.CommandLine -notlike "*$($item[2])*" -or $state.repo -ne $repo){throw 'Recorded PID no longer matches local test process; refusing to stop it.'}
    Stop-Process -Id $item[0]
    Wait-Process -Id $item[0] -Timeout 10 -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $file
Write-Output 'Local HTTPS proxy and relay stopped. Test certificates remain Git-excluded.'
