# SPDX-License-Identifier: GPL-3.0-only
[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'https://www.jumpflix.tv',
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$nativeRoot = Split-Path -Parent $PSScriptRoot
$toolCache = Join-Path $env:USERPROFILE '.codex\cache\jumpflix-android-toolchain'
if (!$env:JAVA_HOME) {
    $localJdk = Join-Path $toolCache 'jdk\jdk-17.0.16+8'
    if (Test-Path -LiteralPath (Join-Path $localJdk 'bin\java.exe')) { $env:JAVA_HOME = $localJdk }
}
if (!$env:ANDROID_HOME) {
    foreach ($sdkCandidate in @($env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), (Join-Path $toolCache 'sdk'))) {
        if ($sdkCandidate -and (Test-Path -LiteralPath (Join-Path $sdkCandidate 'platform-tools\adb.exe'))) {
            $env:ANDROID_HOME = $sdkCandidate
            break
        }
    }
}
if (!$env:JAVA_HOME -or !$env:ANDROID_HOME) { throw 'Set JAVA_HOME to JDK 17 and ANDROID_HOME to your Android SDK.' }
Push-Location $nativeRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug "-PjumpflixApiBaseUrl=$ApiBaseUrl"
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)." }
    $apkPath = Join-Path $nativeRoot 'app\build\outputs\apk\debug\app-debug.apk'
    Write-Output "APK: $apkPath"
    if ($Install) {
        $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
        & $adb install -r $apkPath
        if ($LASTEXITCODE -ne 0) { throw 'ADB install failed. Connect and authorize one Android device, then retry.' }
        & $adb shell am start -n tv.jumpflix.android/tv.jumpflix.MainActivity
        if ($LASTEXITCODE -ne 0) { throw 'The APK installed, but ADB could not start it.' }
    }
} finally { Pop-Location }
