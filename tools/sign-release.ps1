param([Parameter(Mandatory=$true)][string]$OutputApk)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$signingPath = Join-Path $repoPath '.signing'
$keyPath = Join-Path $signingPath 'drawanywhere-simple.p12'
$passwordPath = Join-Path $signingPath 'password.dpapi'
$unsignedApk = Join-Path $repoPath 'app/build/outputs/apk/release/app-release-unsigned.apk'
if (!(Test-Path -LiteralPath $unsignedApk)) { throw 'Build :app:assembleRelease first.' }
New-Item -ItemType Directory -Path $signingPath -Force | Out-Null
if ((Test-Path -LiteralPath $passwordPath) -ne (Test-Path -LiteralPath $keyPath)) {
    throw 'Incomplete signing identity. Restore the existing key/password pair; do not silently generate a replacement key.'
}
if (!(Test-Path -LiteralPath $passwordPath)) {
    if (Test-Path -LiteralPath $keyPath) { throw 'Existing signing key has no password file; do not replace it.' }
    $randomBytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $securePassword = ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
    ConvertFrom-SecureString $securePassword | Set-Content -LiteralPath $passwordPath -Encoding UTF8
}
$securePassword = ConvertTo-SecureString (Get-Content -LiteralPath $passwordPath -Encoding UTF8 -Raw).Trim()
$env:DRAWANYWHERE_SIGNING_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
try {
    if (!(Test-Path -LiteralPath $keyPath)) {
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $keyPath -storetype PKCS12 -alias drawanywhere-simple -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=DrawAnywhere Simple, OU=Local Release, O=kylindebug' -storepass:env DRAWANYWHERE_SIGNING_PASSWORD -keypass:env DRAWANYWHERE_SIGNING_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed.' }
    }
    $buildToolsPath = Join-Path $env:ANDROID_HOME 'build-tools/37.0.0'
    & "$buildToolsPath/zipalign.exe" -c -P 16 -v 4 $unsignedApk | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment check failed.' }
    & "$buildToolsPath/apksigner.bat" sign --ks $keyPath --ks-key-alias drawanywhere-simple --ks-pass env:DRAWANYWHERE_SIGNING_PASSWORD --key-pass env:DRAWANYWHERE_SIGNING_PASSWORD --v4-signing-enabled false --out $OutputApk $unsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & "$buildToolsPath/apksigner.bat" verify --verbose --print-certs $OutputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    & "$env:JAVA_HOME/bin/keytool.exe" -exportcert -rfc -keystore $keyPath -alias drawanywhere-simple -storepass:env DRAWANYWHERE_SIGNING_PASSWORD -file (Join-Path $signingPath 'release-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Public certificate export failed.' }
    Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256
} finally {
    Remove-Item Env:DRAWANYWHERE_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}
