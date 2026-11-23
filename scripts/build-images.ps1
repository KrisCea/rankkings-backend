param(
    [string]$Registry = "",
    [string]$Tag = "latest"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

foreach ($service in @("api-gateway", "identity-service", "catalog-service", "social-service")) {
    $image = "rankkings/${service}:${Tag}"
    if (-not [string]::IsNullOrWhiteSpace($Registry)) {
        $image = "$($Registry.TrimEnd('/'))/${service}:${Tag}"
    }

    Write-Host "Construyendo $image"
    docker build -t $image (Join-Path $root "services\$service")
    if ($LASTEXITCODE -ne 0) { throw "Falló la compilación de $service" }

    if (-not [string]::IsNullOrWhiteSpace($Registry)) {
        docker push $image
        if ($LASTEXITCODE -ne 0) { throw "Falló el push de $image" }
    }
}
