# RankKings Backend

Backend de RankKings, compuesto por cuatro aplicaciones Spring Boot independientes. El frontend se desarrolla en un repositorio separado.

## Inicio rápido (Windows)

Requisitos: JDK 25, PowerShell y conexión a Internet la primera vez que se use Maven Wrapper.

Desde la raíz:

```powershell
java -version
.\scripts\start-local.ps1
```

El script abre una ventana por servicio. Manténlas abiertas mientras trabajas. Inicia el frontend desde su propio repositorio con `npm ci` y `npm run dev`.

| Componente | Dirección local |
|---|---|
| Gateway (entrada de API) | `http://localhost:8080` |
| Identity | `http://localhost:8081` |
| Catalog | `http://localhost:8082` |
| Social | `http://localhost:8083` |
| Frontend | `http://localhost:5173` |

El entorno local activa opciones de desarrollo para captcha y verificación de correo. No uses esas opciones ni los datos de demostración en producción.

## Documentación

- [Guía para desarrolladores](docs/DEVELOPER_GUIDE.md): arquitectura, funciones, código, configuración, seguridad, pruebas y límites conocidos.
- [Manual de usuario](docs/MANUAL_USUARIO.md): uso de las funciones desde la interfaz.
- [Preparación para AWS](docs/AWS_DEPLOYMENT_PREPARATION.md): contenedores, variables y recursos pendientes de despliegue.
- [Contratos OpenAPI](contracts/openapi/): esquemas de referencia de las APIs.

## Pruebas y compilación

Cada servicio se prueba de forma independiente desde su carpeta:

```powershell
foreach ($service in @("api-gateway", "identity-service", "catalog-service", "social-service")) {
  Push-Location ".\services\$service"
  try {
    .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas de $service" }
  } finally {
    Pop-Location
  }
}
```

`.\scripts\smoke-local.ps1` ejecuta un flujo integrado contra servicios ya levantados y crea datos de prueba. `.\scripts\build-images.ps1` construye las imágenes Docker de los cuatro servicios.
