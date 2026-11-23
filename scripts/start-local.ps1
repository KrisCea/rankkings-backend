# Ejecutar en PowerShell de Windows desde cualquier directorio:
#   .\scripts\start-local.ps1
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$env:SPRING_PROFILES_ACTIVE = "local"
$env:INTERNAL_SERVICE_KEY = [Guid]::NewGuid().ToString("N")
$env:RANKINGS_JWT_SECRET = "rankkings-local-development-secret-2026-32bytes"
if ([string]::IsNullOrWhiteSpace($env:DEV_CAPTCHA_BYPASS)) {
  $env:DEV_CAPTCHA_BYPASS = "true"
}
if ([string]::IsNullOrWhiteSpace($env:DEV_VERIFICATION_LOGGING)) {
  $env:DEV_VERIFICATION_LOGGING = "true"
}
$env:COOKIE_SECURE = "false" # localhost HTTP
$services = @("catalog-service", "identity-service", "social-service", "api-gateway")
Write-Host "Bypass local de captcha: $env:DEV_CAPTCHA_BYPASS"
Write-Host "Verificación por log local: $env:DEV_VERIFICATION_LOGGING"
foreach ($svc in $services) {
  $cwd = Join-Path $root ("services\" + $svc)
  if ($svc -ne "api-gateway") {
    New-Item -ItemType Directory -Path (Join-Path $cwd "data") -Force | Out-Null
  }
  Write-Host "Iniciando $svc en una nueva ventana de PowerShell..."
  Start-Process -FilePath "powershell.exe" -WorkingDirectory $cwd -ArgumentList @("-NoExit", "-Command", ".\mvnw.cmd spring-boot:run")
  Start-Sleep -Seconds 2
}
Write-Host "Inicia el frontend aparte: cd <repo-frontend>\frontend; npm ci; npm run dev"
Write-Host "Para probar el registro local, usa el botón de captcha de prueba y abre el enlace mostrado en Identity."
