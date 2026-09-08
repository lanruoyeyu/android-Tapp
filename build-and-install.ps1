# 一键清理缓存并重新构建安装
# 用法：在 VS Code 终端中执行 .\build-and-install.ps1

$ErrorActionPreference = "Continue"

$PROJECT_DIR = "e:\小程序\android-Tapp"
$JAVA_HOME = "F:\software\jdk17"
$ANDROID_HOME = "F:\software\android-sdk"
$GRADLE_USER_HOME = "C:\Users\Admin\.gradle"
$LOCALAPPDATA = "C:\Users\Admin\AppData\Local\Temp\la"
$GRADLE = "F:\software\gradle-8.5\bin\gradle.bat"
$ADB = "F:\software\android-sdk\platform-tools\adb.exe"
$APK = "F:\software\app-build\app\outputs\apk\debug\app-debug.apk"
$BUILD_DIR = "F:\software\app-build\app"

Write-Host "1. 清理构建缓存..." -ForegroundColor Cyan
$cleanPaths = @(
    "$BUILD_DIR\tmp",
    "$BUILD_DIR\kotlin",
    "$BUILD_DIR\intermediates\merged_res_blame_folder"
)
foreach ($p in $cleanPaths) {
    if (Test-Path $p) {
        Remove-Item -Recurse -Force $p -ErrorAction SilentlyContinue
        Write-Host "   已清理: $p"
    }
}

Write-Host "2. 构建并运行单元测试..." -ForegroundColor Cyan
$env:JAVA_HOME = $JAVA_HOME
$env:ANDROID_HOME = $ANDROID_HOME
$env:GRADLE_USER_HOME = $GRADLE_USER_HOME
$env:LOCALAPPDATA = $LOCALAPPDATA

& $GRADLE -p $PROJECT_DIR :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败！请检查上方错误信息。" -ForegroundColor Red
    exit 1
}
Write-Host "构建成功！" -ForegroundColor Green

Write-Host "3. 安装到设备..." -ForegroundColor Cyan
$devices = & $ADB devices
if ($devices -match "emulator|device\b" -and $devices -notmatch "no devices") {
    & $ADB install -r $APK
    Write-Host "安装完成！" -ForegroundColor Green
} else {
    Write-Host "未检测到设备/模拟器，跳过安装。请先启动模拟器。" -ForegroundColor Yellow
}
