param(
    [string]$CaddyPath = 'D:\projects\tools\caddy-2.11.9\caddy.exe',
    [string]$NodePath = 'D:\node.exe',
    [string]$OpenSslPath = 'C:\Program Files\Git\usr\bin\openssl.exe',
    [int]$HttpsPort = 8443,
    [int]$RelayPort = 8787,
    [switch]$RenewCertificates,
    [switch]$CertificatesOnly
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$state=Join-Path $repo '.gradle/local-https'
New-Item -ItemType Directory -Path $state -Force | Out-Null
foreach($tool in $(if($CertificatesOnly){@($OpenSslPath)}else{@($CaddyPath,$NodePath,$OpenSslPath)})){if(!(Test-Path -LiteralPath $tool)){throw "Missing tool: $tool"}}
foreach($port in $(if($CertificatesOnly){@()}else{@($HttpsPort,$RelayPort)})){if(@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count){throw "Port $port is already in use"}}
function Ssl([string[]]$Arguments){
    & $OpenSslPath @Arguments 2>> (Join-Path $state 'certificate-setup.log') | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Local TLS certificate command failed; see Git-excluded certificate-setup.log'}
}
$ca=Join-Path $state 'root.crt';$cert=Join-Path $state 'server.crt';$key=Join-Path $state 'server.key'
if($RenewCertificates -or !(Test-Path $ca) -or !(Test-Path $cert) -or !(Test-Path $key)){
    Ssl @('req','-x509','-newkey','rsa:2048','-noenc','-keyout',(Join-Path $state 'root.key'),'-out',$ca,'-days','7','-subj','/CN=Donghaeng Local HTTPS Test CA','-addext','basicConstraints=critical,CA:TRUE','-addext','keyUsage=critical,keyCertSign,cRLSign')
    Ssl @('req','-newkey','rsa:2048','-noenc','-keyout',$key,'-out',(Join-Path $state 'server.csr'),'-subj','/CN=localhost')
    @('[v3_server]','basicConstraints=critical,CA:FALSE','keyUsage=critical,digitalSignature,keyEncipherment','extendedKeyUsage=serverAuth','subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2') | Set-Content (Join-Path $state 'server.ext') -Encoding ascii
    Ssl @('x509','-req','-in',(Join-Path $state 'server.csr'),'-CA',$ca,'-CAkey',(Join-Path $state 'root.key'),'-CAcreateserial','-out',$cert,'-days','7','-extfile',(Join-Path $state 'server.ext'),'-extensions','v3_server')
}
Ssl @('verify','-CAfile',$ca,$cert)
Ssl @('x509','-checkend','3600','-noout','-in',$cert)
if($CertificatesOnly){Write-Output "Local public CA: $ca ; server TLS certificate/key prepared without starting services.";return}
$values=@{HOST='127.0.0.1';PORT="$RelayPort";LOCAL_TLS_PORT="$HttpsPort";LOCAL_TLS_STORAGE=(Join-Path $state 'storage').Replace('\','/');LOCAL_TLS_CERT=$cert.Replace('\','/');LOCAL_TLS_KEY=$key.Replace('\','/');RELAY_UPSTREAM="127.0.0.1:$RelayPort";NODE_EXTRA_CA_CERTS=$ca;RELAY_HEALTH_URL="https://127.0.0.1:$HttpsPort/health"}
$previous=@{};foreach($name in $values.Keys){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$values[$name],'Process')}
$nodeProcess=$null;$caddyProcess=$null
Push-Location -LiteralPath $repo
try{
    & $CaddyPath validate --config 'relay/deploy/Caddyfile.local' --adapter caddyfile
    if($LASTEXITCODE -ne 0){throw 'Local Caddy configuration invalid'}
    $nodeProcess=Start-Process -FilePath $NodePath -ArgumentList 'relay/server.mjs' -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $state 'relay.log') -RedirectStandardError (Join-Path $state 'relay-error.log')
    $caddyProcess=Start-Process -FilePath $CaddyPath -ArgumentList 'run --config relay/deploy/Caddyfile.local --adapter caddyfile' -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $state 'caddy.log') -RedirectStandardError (Join-Path $state 'caddy-error.log')
    $ready=$false
    for($attempt=0;$attempt -lt 10;$attempt++){
        Start-Sleep -Seconds 1
        & $NodePath 'relay/healthcheck.mjs' 2>> (Join-Path $state 'healthcheck.log')
        if($LASTEXITCODE -eq 0){$ready=$true;break}
    }
    if(!$ready){throw 'Local HTTPS health check failed'}
    @{nodePid=$nodeProcess.Id;caddyPid=$caddyProcess.Id;nodePath=$NodePath;caddyPath=$CaddyPath;repo=$repo;httpsPort=$HttpsPort;relayPort=$RelayPort}|ConvertTo-Json|Set-Content (Join-Path $state 'processes.json') -Encoding utf8
    Write-Output "Local HTTPS ready: https://10.0.2.2:$HttpsPort ; public CA: $ca"
} catch {
    foreach($p in @($nodeProcess,$caddyProcess)){if($p -and (Get-Process -Id $p.Id -ErrorAction SilentlyContinue)){Stop-Process -Id $p.Id -ErrorAction SilentlyContinue}}
    throw
} finally {
    Pop-Location
    foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}
