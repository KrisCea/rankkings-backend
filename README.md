# RankKings Backend

Backend de RankKings organizado como cuatro microservicios Spring Boot independientes. Este repositorio contiene el API Gateway y los servicios; el frontend se ejecuta desde su propio repositorio.

## Requisitos

- JDK 25 instalado y `JAVA_HOME` apuntando a esa instalación.
- Windows PowerShell.
- Conexión a Internet la primera vez que se ejecuta Maven Wrapper, para descargar Maven y dependencias.
- Para probar la interfaz: Node.js/npm y el repositorio del frontend.

Verifica Java antes de empezar:

```powershell
java -version
$env:JAVA_HOME
```

## Iniciar el entorno local

Desde la raíz de este repositorio, ejecuta:

```powershell
.\scripts\start-local.ps1
```

El script abre una ventana de PowerShell por servicio. Déjalas abiertas mientras pruebas:

| Servicio | URL local | Puerto |
|---|---|---:|
| API Gateway | `http://localhost:8080` | 8080 |
| Identity | `http://localhost:8081` | 8081 |
| Catalog | `http://localhost:8082` | 8082 |
| Social | `http://localhost:8083` | 8083 |

El script activa el perfil `local`, genera una clave interna aleatoria compartida y habilita el bypass de captcha y el registro local del enlace de verificación. Son opciones exclusivas para desarrollo. En Identity, el enlace de verificación aparece en la consola; no publiques estas opciones ni expongas directamente los puertos de los servicios.

## Arquitectura

Identity y Social separan las responsabilidades en paquetes `controller`, `service`, `repository`, `model`, `dto` y `client`:

- **Controllers** reciben y validan el formato HTTP, delegando el caso de uso.
- **Services** contienen las reglas de negocio y límites transaccionales.
- **Repositories** encapsulan consultas y persistencia Spring Data JPA.
- **Clients** concentran las llamadas entre microservicios: Identity consulta Social para el contador de publicaciones; Social consulta Identity y Catalog.

Catalog mantiene su propia separación de controller, service y repository. API Gateway conserva filtros y rutas, sin repositorio ni base de datos propios.

### Ejecutar el frontend

En otra terminal, desde el repositorio del frontend:

```powershell
cd .\frontend
npm ci
npm run dev
```

Abre `http://localhost:5173`, registra una cuenta con el captcha de prueba y abre el enlace que se imprime en la ventana de Identity. Después inicia sesión. El frontend no forma parte de este repositorio.

## Probar el catálogo

Catalog carga automáticamente las obras de [catalogo-ejemplo.csv](services/catalog-service/src/main/resources/data/catalogo-ejemplo.csv). La carga añade registros faltantes sin reemplazar los existentes: incluye 58 obras de ocho categorías y conserva las relaciones de álbumes y canciones. Los identificadores, como `item-101` (Random Access Memories), `item-201` (Interstellar), `item-301` (1984) e `item-401` (Hollow Knight), se pueden usar para crear publicaciones y reseñas.

Con los cuatro servicios iniciados, abre una terminal en la raíz y ejecuta la verificación automática:

```powershell
.\scripts\verificar-catalogo.ps1
```

También puedes consultar el catálogo y buscar por título a través del Gateway:

```powershell
Invoke-RestMethod http://localhost:8080/api/items
Invoke-RestMethod "http://localhost:8080/api/items?query=Hollow"
Invoke-RestMethod http://localhost:8080/api/items/item-101
Invoke-RestMethod http://localhost:8080/api/items/item-1/children
```

## Probar Identity y Social

1. Registra una cuenta desde el frontend y verifica el correo abriendo el enlace local de la ventana Identity.
2. Inicia sesión en el frontend.
3. Abre `http://localhost:5173/create-post`, selecciona una obra existente (por ejemplo, `item-101`) y crea una publicación o reseña.
4. Prueba votar, puntuar, comentar y dar like a un comentario desde la interfaz.

También hay una prueba de integración por terminal que ejecuta login, consulta de sesión, creación de reseña, voto, rating, comentario, like y logout:

```powershell
.\scripts\smoke-local.ps1
```

El script solicita el correo y la contraseña de una cuenta ya verificada y **crea registros de prueba**. Los datos se guardan en H2 local; para limpiar la base de pruebas, detén los servicios y elimina manualmente los archivos `data` del servicio correspondiente.

## Ejecutar las pruebas automatizadas

Desde la raíz, ejecuta las pruebas de cada módulo:

```powershell
Push-Location .\services\identity-service
.\mvnw.cmd test
Pop-Location

Push-Location .\services\social-service
.\mvnw.cmd test
Pop-Location

Push-Location .\services\catalog-service
.\mvnw.cmd test
Pop-Location

Push-Location .\services\api-gateway
.\mvnw.cmd test
Pop-Location
```

Cada servicio se construye de forma independiente; la raíz no es un proyecto Maven multimódulo.

## Persistencia y configuración

Con el perfil `local`, Identity, Catalog y Social usan archivos H2 en `services\<servicio>\data\`. Los datos sobreviven al reinicio mientras no borres esos archivos. El catálogo de ejemplo se carga automáticamente al iniciar Catalog. El perfil `prod` usa PostgreSQL y no importa datos de ejemplo; configura sus credenciales y esquema antes de iniciarlo.

Para configurar envío real de verificación de correo se requieren `RESEND_API_KEY` y `VERIFICATION_FROM_EMAIL`. Consulta [docs/IDENTITY_SOCIAL_IMPLEMENTADOS.md](docs/IDENTITY_SOCIAL_IMPLEMENTADOS.md) para más detalles de configuración, contratos y limitaciones.

## Seguridad y límites

El Gateway valida la cookie de sesión opaca `rk_token`; no usa JWT. Los servicios internos requieren una clave compartida y deben permanecer detrás del Gateway. El perfil local desactiva la verificación real de captcha y muestra los enlaces de verificación en consola: no lo uses en producción. Antes de exponer la aplicación se requieren, entre otras medidas, rate limiting, TLS, migraciones de base de datos y pruebas de seguridad.
