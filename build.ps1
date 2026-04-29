$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT must be set" }

$buildTools = Join-Path $sdk "build-tools\34.0.0"
$jdk = "C:\Program Files\Microsoft\jdk-11.0.26.4-hotspot"
if (Test-Path $jdk) {
  $env:JAVA_HOME = $jdk
  $env:Path = (Join-Path $jdk "bin") + ";" + $env:Path
}
$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
$platformJar = Join-Path $sdk "platforms\android-34\android.jar"
$aapt2 = Join-Path $buildTools "aapt2.exe"
$d8 = Join-Path $buildTools "d8.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$manifest = "app\src\main\AndroidManifest.xml"
$resDir = "app\src\main\res"
$srcDir = "app\src\main\java"
$out = "build"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\compiled", "$out\generated", "$out\classes", "$out\dex" | Out-Null

& $aapt2 compile --dir $resDir -o "$out\compiled\res.zip"
& $aapt2 link -I $platformJar --manifest $manifest -o "$out\unsigned.apk" --java "$out\generated" --auto-add-overlay -R "$out\compiled\res.zip"

$sources = Get-ChildItem $srcDir, "$out\generated" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -g:none -encoding UTF-8 --release 8 -classpath $platformJar -d "$out\classes" $sources
& jar cf "$out\classes.jar" -C "$out\classes" .
& $d8 --no-desugaring --min-api 23 --lib $platformJar --output "$out\dex" "$out\classes.jar"

Copy-Item "$out\unsigned.apk" "$out\with-dex.apk"
& jar uf "$out\with-dex.apk" -C "$out\dex" classes.dex
& $zipalign -f 4 "$out\with-dex.apk" "$out\aligned.apk"

$keyDir = Join-Path $env:USERPROFILE ".android"
$keyStore = Join-Path $keyDir "debug.keystore"
if (-not (Test-Path $keyStore)) {
  New-Item -ItemType Directory -Force $keyDir | Out-Null
  & keytool -genkeypair -keystore $keyStore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
}

& $apksigner sign --ks $keyStore --ks-pass pass:android --key-pass pass:android --out "$out\lightui-release.apk" "$out\aligned.apk"
& $apksigner verify "$out\lightui-release.apk"

Write-Host "Built $out\lightui-release.apk"
