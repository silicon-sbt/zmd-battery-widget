# 构建前下载 HarmonyOS Sans SC 字体到 res/font（字体不进 git，避免仓库过大）
$ErrorActionPreference = "Stop"
$base = "https://raw.githubusercontent.com/ajacocks/harmonyos-sans-font/main/HarmonyOS_Sans_SC/"
$dest = Join-Path $PSScriptRoot "app\src\main\res\font"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$map = @{
  "HarmonyOS_Sans_SC_Regular.ttf" = "harmonyos_sans_sc_regular.ttf"
  "HarmonyOS_Sans_SC_Medium.ttf"  = "harmonyos_sans_sc_medium.ttf"
  "HarmonyOS_Sans_SC_Bold.ttf"    = "harmonyos_sans_sc_bold.ttf"
}
foreach ($k in $map.Keys) {
  Invoke-WebRequest -Uri ($base + $k) -OutFile (Join-Path $dest $map[$k])
  Write-Output ("fetched " + $map[$k])
}
Write-Output "done - fonts ready"
