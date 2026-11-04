param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
if ($JdkHome) {
    $javacPath = Join-Path $JdkHome 'bin\javac.exe'
    $javaPath = Join-Path $JdkHome 'bin\java.exe'
} else {
    $javacPath = (Get-Command javac.exe -ErrorAction Stop).Source
    $javaPath = Join-Path (Split-Path $javacPath -Parent) 'java.exe'
}
Push-Location -LiteralPath $projectPath
try {
    $outputPath = Join-Path $projectPath '.gradle\state-tests'
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    $sourceRoot = 'app/src/main/java/io/demo/companion/'
    $sources = @('JourneyState','DestinationAddressResolver','NavigationSession','SharingModels','SharingStore','SharingTransport','ParkingState','JourneyPublisher','RelayEndpoint') | ForEach-Object { $sourceRoot + $_ + '.java' }
    $sources += 'app/src/demo/java/io/demo/companion/MockSharingTransport.java'
    $tests = @('JourneyStateTest','DestinationAddressResolverTest','NavigationSessionTest','SharingTest','ParkingStateTest','RelayTest')
    $sources += $tests | Where-Object { $_ -ne 'SharingTest' } | ForEach-Object { 'app/src/test/java/io/demo/companion/' + $_ + '.java' }
    $sources += 'app/src/testDebug/java/io/demo/companion/SharingTest.java'
    & $javacPath -encoding UTF-8 -d $outputPath @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java test compilation failed' }
    foreach ($test in $tests) {
        & $javaPath -cp $outputPath ('io.demo.companion.' + $test)
        if ($LASTEXITCODE -ne 0) { throw "Test failed: $test" }
    }
} finally { Pop-Location }
