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

### Datos ficticios para probar el feed

Al iniciar los servicios con `.\scripts\start-local.ps1`, el perfil `local` crea tres cuentas verificadas y hasta 21 publicaciones de prueba asociadas a obras del catálogo; varias son obras reales como *OK Computer*, *Interstellar*, *1984*, *Dune*, *Hollow Knight* y *The Legend of Zelda: Breath of the Wild*. También se agregan algunas reseñas, ratings y votos para probar los estados de interacción. La carga es idempotente y no reemplaza publicaciones existentes; no se ejecuta fuera del perfil `local`.

Puedes iniciar sesión desde el frontend con cualquiera de estas cuentas:

| Correo | Contraseña |
|---|---|
| `ana.demo@rankkings.test` | `DemoRankKings123!` |
| `mateo.demo@rankkings.test` | `DemoRankKings123!` |
| `camila.demo@rankkings.test` | `DemoRankKings123!` |

Estos usuarios y contraseñas son exclusivamente para desarrollo local; no los uses en otros entornos.

### Multimedia y reseñas en posts

Las reseñas nuevas se crean como comentarios del post del Rankable Item (`POST /posts/{postId}/reviews`); `POST /posts` rechaza reseñas independientes. Las imágenes se suben a un bucket privado de Amazon S3 mediante una URL firmada: configura `MEDIA_S3_BUCKET`, `AWS_REGION` y las credenciales de AWS del proveedor estándar (variables de entorno o perfil local). No guardes credenciales en el repositorio. La configuración CORS del bucket debe permitir `PUT` desde el origen del frontend y los encabezados `Content-Type` e `If-None-Match`. Configura además una regla de ciclo de vida para borrar objetos del prefijo `pending-post-media/` después de un día; no apliques esa regla al prefijo `post-media/`.

Límites iniciales: hasta cuatro adjuntos y 20 MiB por post; cada imagen puede pesar hasta 10 MiB y debe ser JPEG, PNG o WebP. La URL de subida vence en cinco minutos; después de subir el archivo se debe llamar al endpoint de confirmación, que verifica tamaño, MIME y firma del archivo. Los posts devuelven URLs firmadas de lectura que vencen en diez minutos. Los binarios quedan en S3 y sus metadatos en la base configurada para Social (H2 en desarrollo o la base indicada para el entorno); esto no configura una base AWS administrada. Los videos por ahora solo se admiten como enlaces HTTPS externos, no se suben hasta poder validar su duración y moderarlos. El backend no descarga ni incrusta esos enlaces, y aún no aplica moderación de contenido a las imágenes.

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

Para levantar las bases locales definidas por el ERS (PostgreSQL y MongoDB), ejecuta desde la raíz:

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL queda disponible en `localhost:5432` (base y usuario `rankkings`) y MongoDB en `localhost:27017` (base `rankkings_catalog`, autenticación `admin`). Las contraseñas predeterminadas (`rankkings_local`) son solo para desarrollo local; puedes cambiarlas con las variables `POSTGRES_PASSWORD` y `MONGO_ROOT_PASSWORD`, y configurar los puertos con `POSTGRES_PORT` y `MONGO_PORT`. Los puertos se enlazan únicamente a `127.0.0.1`; los datos persisten en volúmenes de Docker. `docker compose down` detiene los contenedores sin borrar esos datos; `docker compose down -v` los elimina.

Esta configuración levanta solo las bases: los servicios siguen usando H2 con el perfil `local` y todavía no se conectan a MongoDB. La integración de los servicios con PostgreSQL/MongoDB y las pruebas Testcontainers indicadas en el ERS quedan para una etapa posterior.

Para configurar envío real de verificación de correo se requieren `RESEND_API_KEY` y `VERIFICATION_FROM_EMAIL`. Consulta [docs/IDENTITY_SOCIAL_IMPLEMENTADOS.md](docs/IDENTITY_SOCIAL_IMPLEMENTADOS.md) para más detalles de configuración, contratos y limitaciones.

## Seguridad y límites

El Gateway valida la cookie de sesión opaca `rk_token`; no usa JWT. Los servicios internos requieren una clave compartida y deben permanecer detrás del Gateway. El perfil local desactiva la verificación real de captcha y muestra los enlaces de verificación en consola: no lo uses en producción. Antes de exponer la aplicación se requieren, entre otras medidas, rate limiting, TLS, migraciones de base de datos y pruebas de seguridad.
