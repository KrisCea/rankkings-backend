# Ejecutar en PowerShell de Windows desde cualquier directorio:
#   .\scripts\start-local.ps1
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$env:SPRING_PROFILES_ACTIVE = "local"
$env:INTERNAL_SERVICE_KEY = [Guid]::NewGuid().ToString("N")
$env:DEV_CAPTCHA_BYPASS = "true" # Sólo para development; NO usar en producción
$env:DEV_VERIFICATION_LOGGING = "true" # Token de confirmación visible en ventana Identity
$env:COOKIE_SECURE = "false" # localhost HTTP
$services = @("catalog-service", "identity-service", "social-service", "api-gateway")
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
