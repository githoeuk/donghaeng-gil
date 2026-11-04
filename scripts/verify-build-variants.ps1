param([switch]$SkipCompile)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
$checks = 0
function Assert-Variant([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
Push-Location -LiteralPath $projectPath
try {
    if (-not $SkipCompile) {
        $buildScript = Join-Path (Split-Path $projectPath -Parent) 'build-donghaeng.ps1'
        & $buildScript -Tasks @('compileStandardDebugJavaWithJavac','compileSubmissionDebugJavaWithJavac',
            'compileStandardReleaseJavaWithJavac','compileSubmissionReleaseJavaWithJavac',
            'processStandardDebugManifest','processSubmissionDebugManifest',
            'processStandardReleaseManifest','processSubmissionReleaseManifest')
    }
    $androidNamespace = 'http://schemas.android.com/apk/res/android'
    foreach ($flavor in @('standard','submission')) {
        foreach ($type in @('debug','release')) {
            $suffix = (Get-Culture).TextInfo.ToTitleCase($type)
            $variant = $flavor + $suffix
            $taskVariant = (Get-Culture).TextInfo.ToTitleCase($flavor) + $suffix
            $expectedDemo = $flavor -eq 'submission' -or $type -eq 'debug'
            $expectedDebug = $type -eq 'debug'
            $classes = "app/build/intermediates/javac/$variant/compile${taskVariant}JavaWithJavac/classes/io/demo/companion"
            $manifestFile = "app/build/intermediates/merged_manifests/$variant/process${taskVariant}Manifest/AndroidManifest.xml"
            $configFile = "app/build/generated/source/buildConfig/$flavor/$type/io/demo/companion/BuildConfig.java"
            Assert-Variant (Test-Path -LiteralPath "$classes/MainActivity.class") "$variant missing main class"
            Assert-Variant ((Test-Path -LiteralPath "$classes/SharingDemoActivity.class") -eq $expectedDemo) "$variant demo UI inclusion mismatch"
            Assert-Variant ((Test-Path -LiteralPath "$classes/MockSharingTransport.class") -eq $expectedDemo) "$variant mock transport inclusion mismatch"
            Assert-Variant ((Test-Path -LiteralPath "$classes/PleosDiagnosticsActivity.class") -eq $expectedDebug) "$variant diagnostic class inclusion mismatch"
            Assert-Variant ((Test-Path -LiteralPath "$classes/NotificationDemoActivity.class") -eq $expectedDebug) "$variant notification tool inclusion mismatch"
            [xml]$manifest = Get-Content -Raw -LiteralPath $manifestFile
            Assert-Variant ($manifest.manifest.package -eq 'io.demo.companion') "$variant applicationId changed"
            $application = $manifest.SelectSingleNode('/manifest/application')
            Assert-Variant (($application.GetAttribute('debuggable',$androidNamespace) -eq 'true') -eq $expectedDebug) "$variant debuggable mismatch"
            $demo = @($application.SelectNodes('activity') | Where-Object { $_.GetAttribute('name',$androidNamespace) -eq 'io.demo.companion.SharingDemoActivity' })
            Assert-Variant (($demo.Count -gt 0) -eq $expectedDemo) "$variant manifest demo inclusion mismatch"
            if ($expectedDemo) {
                Assert-Variant ($demo[0].GetAttribute('exported',$androidNamespace) -eq 'false') "$variant demo must not be exported"
                Assert-Variant ($demo[0].GetAttribute('permission',$androidNamespace) -eq '') "$variant demo must not require shell DUMP permission"
            }
            $diagnostics = @($application.SelectNodes('activity') | Where-Object { $_.GetAttribute('name',$androidNamespace) -match 'PleosDiagnosticsActivity|NotificationDemoActivity' })
            Assert-Variant (($diagnostics.Count -eq 2) -eq $expectedDebug) "$variant debug tools manifest mismatch"
            $permissions = @($manifest.SelectNodes('/manifest/uses-permission') | ForEach-Object { $_.GetAttribute('name',$androidNamespace) })
            Assert-Variant (-not ($permissions -contains 'android.permission.INTERNET')) "$variant added network permission"
            Assert-Variant (($permissions -contains 'pleos.car.permission.NAVI_ROUTE_SEARCH') -eq $expectedDebug) "$variant test route permission mismatch"
            $config = Get-Content -Raw -LiteralPath $configFile
            $flag = $expectedDemo.ToString().ToLowerInvariant()
            Assert-Variant ($config -match "HAS_SHARING_DEMO = $flag;") "$variant demo BuildConfig flag mismatch"
            if (-not $expectedDebug) {
                Assert-Variant ($config -match 'DEBUG = false;') "$variant Release DEBUG flag must be false"
            }
            Write-Output "$variant verified (demo=$expectedDemo, debug=$expectedDebug)"
        }
    }
    Write-Output "Passed $checks build-variant checks. No signing or APK/AAB packaging requested."
} finally { Pop-Location }
