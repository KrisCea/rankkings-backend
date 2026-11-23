# Guía para desarrolladores

## Alcance del repositorio

Este repositorio contiene el backend de RankKings. El frontend no está incluido aquí. Cada servicio es un proyecto Maven independiente; no hay un `pom.xml` agregador en la raíz.

Tecnologías principales: Java 25, Spring Boot, Spring Cloud Gateway (Gateway), Spring MVC (servicios), Spring Data JPA, H2 en desarrollo, PostgreSQL con Flyway en producción y S3 para imágenes. Usa el Maven Wrapper de cada módulo; no es necesario instalar Maven globalmente.

## Estructura del repositorio

```text
contracts/openapi/          Contratos OpenAPI de referencia
docs/                       Documentación de desarrollador, usuario y despliegue
infra/                      Carpeta reservada para infraestructura (sin IaC de despliegue actualmente)
scripts/                    Inicio local, smoke test y build de imágenes
services/
  api-gateway/               Entrada HTTP, rutas y filtros transversales
  identity-service/          Cuentas, autenticación, sesiones y perfiles
  catalog-service/           Rankable Items, biblioteca y estadísticas
  social-service/            Publicaciones, reseñas, interacciones y medios
docker-compose.yml          PostgreSQL y MongoDB locales (solo infraestructura)
docker-compose.aws.yml      Compose de preflight de los cuatro servicios
.env.aws.example            Ejemplo de variables para el preflight AWS
```

En cada servicio, el código está bajo `src/main/java/cl/rankkings/...`; configuración, datos de ejemplo y migraciones están bajo `src/main/resources`; pruebas, bajo `src/test/java`. Identity y Social separan `controller`, `service`, `repository`, `model`, `dto`, `client` y/o `security`/`config`. Catalog usa `controller`, `service`, `repository`, `model`, `dto` y `config`. Gateway agrupa filtros y configuración.

Responsabilidades habituales:

- **Controller**: rutas HTTP, validación de entrada y códigos de respuesta; no agregues reglas de negocio aquí.
- **Service**: casos de uso, autorización, transacciones y coordinación.
- **Repository**: consultas y persistencia JPA.
- **Model**: entidades persistidas.
- **DTO**: contratos de entrada/salida y validación.
- **Client**: llamadas HTTP a otro servicio.
- **Config/security/filter**: configuración transversal, seguridad y filtros.

## Arquitectura y flujo de solicitudes

El Gateway es la entrada pública y escucha en `8080`. Enruta solicitudes a:

| Servicio | Puerto local | Responsabilidad |
|---|---:|---|
| Identity | 8081 | Registro, correo verificado, login/logout, sesiones, perfiles y follows |
| Catalog | 8082 | Obras (Rankable Items), búsqueda, jerarquía y elementos guardados |
| Social | 8083 | Feed, publicaciones, reseñas, comentarios, ratings, votos, compartir, medios y moderación |

Flujo típico: frontend → Gateway → servicio dueño del recurso. Social consulta Identity para resolver usuarios/permisos y Catalog para obras; Identity consulta Social para contar publicaciones; Social también sincroniza rating, moderación y portadas con Catalog. Las llamadas entre servicios utilizan `INTERNAL_SERVICE_KEY`. Mantén la propiedad de cada dato en su servicio; no conectes un servicio a la base de otro.

## Funcionalidades y rutas

El Gateway admite rutas con `/api/v1` (preferidas para clientes nuevos) y conserva `/api` por compatibilidad. Quita el prefijo antes de enviar la solicitud al servicio. Las rutas `/internal/**` son de servicio a servicio, no de cliente.

### Identity

| Ruta | Función |
|---|---|
| `POST /auth/register` | Crear cuenta, validar CAPTCHA y emitir verificación de correo |
| `POST /auth/login`, `POST /auth/logout` | Crear o revocar sesión |
| `POST /auth/verify-email`, `POST /auth/resend-verification` | Confirmar o reenviar verificación |
| `GET /users/me`, `GET /users/me/share-contacts` | Consultar la sesión y contactos para compartir |
| `GET /users/{username}`, `POST /users/{username}/follow` | Consultar perfil y alternar follow |
| `GET /internal/session`, `/internal/users/{id}`, `/internal/usernames/{username}` | Validación de sesión y consultas internas |

### Catalog

| Ruta | Función |
|---|---|
| `GET /items?query=...`, `GET /items/{itemId}`, `GET /items/{itemId}/children` | Buscar, consultar obras y recorrer obras hijas |
| `PUT` / `DELETE /items/{itemId}/save`, `GET /users/me/saved` | Guardar, quitar y listar elementos de la biblioteca |
| `/internal/items/**` | Creación/consulta de obras y sincronización de ratings, portada, moderación y borrado con servicios internos |

### Social

| Ruta | Función |
|---|---|
| `GET /posts`, `/posts/featured`, `/posts/{id}`, `/posts/by-item/{itemId}` | Feed, selección destacada, detalle y post asociado a una obra |
| `GET /users/{username}/posts?kind=...`, `/items/{itemId}/reviews` | Publicaciones por autor y reseñas por obra |
| `POST /posts` | Crear una publicación vinculada a un `itemId` existente o enviar `rankableItem` para crear la obra y publicación |
| `POST /posts/{id}/reviews` | Crear reseña asociada a un post de obra; una reseña no es un post independiente |
| `/posts/{id}/vote`, `PUT` / `DELETE /posts/{id}/rating`, `/posts/{id}/share` | Votar, puntuar (1–10), retirar rating y compartir |
| `GET` / `POST /posts/{id}/comments`, `POST /comments/{id}/like` | Leer/crear comentarios y alternar like; las respuestas admiten un nivel |
| `DELETE /posts/{id}`, `/posts/{postId}/reviews/{reviewId}`, `/comments/{id}` | Eliminar contenido según autoría o rol de administración |
| `/posts/{id}/media/**` | Preparar y confirmar carga de imagen, leer mediante URL firmada, añadir enlace externo y borrar adjuntos |
| `/admin/moderation/**` | Cola y decisión de moderación para posts, reseñas, comentarios y medios |
| `GET /internal/users/{id}/posts/count` | Conteo interno de publicaciones |

Usa los YAML en `contracts/openapi/` como referencia de esquemas y ejemplos, pero contrasta rutas y comportamiento con los controladores: los contratos son archivos mantenidos en el repositorio y deben actualizarse junto con el código.

## Modelo de datos principal

- **Identity**: `Account`, `EmailVerification`, `LoginSession`, `FollowRelation`.
- **Catalog**: `RankableItem` y `SavedItem`. Un item puede tener `parentId`/`position` para relaciones jerárquicas, identificadores, detalles, creador, ratings y enlaces externos.
- **Social**: `SocialPost`, `PostComment` (comentarios y reseñas), `PostVote`, `PostRating`, `PostShare`, `CommentLike`, `PostMedia`.

Los IDs de usuario se comparten entre servicios como referencias, no como relaciones JPA entre bases. Las métricas del catálogo derivadas de ratings y algunas referencias entre Catalog/Social se sincronizan mediante HTTP; no existe una transacción distribuida.

## Desarrollo local

1. Instala JDK 25 y verifica `java -version`.
2. Desde la raíz, ejecuta `.\scripts\start-local.ps1`. Se abren cuatro ventanas de PowerShell; el script configura perfil `local`, puertos y clave interna temporal.
3. En el repositorio del frontend, ejecuta `npm ci` y `npm run dev`.
4. Para registrar una cuenta, el CAPTCHA de desarrollo está habilitado localmente; el enlace de verificación se escribe en la consola de Identity. Inicia sesión desde el frontend.

URLs locales: Gateway `http://localhost:8080`, Identity `:8081`, Catalog `:8082`, Social `:8083`. Swagger está disponible en `/swagger-ui.html` en Identity, Catalog y Social.

### Perfiles y persistencia

- `local`: archivos H2 bajo `services\<service>\data\`. Los datos sobreviven al reinicio; borrar esos archivos los elimina.
- Sin `local` ni `prod`: la configuración base puede usar H2 en memoria o `update`, según el servicio; usa el script para el flujo local reproducible.
- `prod`: PostgreSQL, Flyway y `ddl-auto=validate`; el catálogo demo y la consola H2 se desactivan.
- `docker-compose.yml` levanta PostgreSQL y MongoDB solamente. Los servicios locales siguen en H2; MongoDB aún no está conectado al flujo de aplicación.
- Catalog importa `data/catalogo-ejemplo.csv` de forma idempotente fuera de `prod` cuando `SEED_CATALOG=true`.

## Pruebas y comandos de trabajo

Ejecuta cada suite tras cambiar el módulo correspondiente:

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

Otros comandos:

```powershell
.\scripts\start-local.ps1
.\scripts\verificar-catalogo.ps1
.\scripts\smoke-local.ps1
.\scripts\build-images.ps1
```

`smoke-local.ps1` requiere una cuenta verificada, ejecuta login/sesión, crea una obra/post, reseña e interacciones y deja datos en H2. No es una prueba de solo lectura.

## Seguridad y configuración sensible

- Identity emite un JWT de siete días en `rk_token` (HttpOnly, SameSite=Lax y `Secure` según configuración). A través del Gateway, el cliente también puede enviar el token como Bearer. Identity conserva el hash de sesión en base de datos; Gateway consulta `/internal/session`, así logout puede revocar tokens antes de su expiración.
- Gateway descarta las cabeceras de usuario/secreto recibidas del cliente, valida la sesión con Identity e inyecta `X-User-Id`, `X-Username`, `X-User-Role` y `X-Internal-Key` al reenviar. Una caída de Identity no debe convertirse en autenticación anónima.
- Catalog y Social rechazan tráfico sin la clave interna. Identity valida la misma clave para endpoints internos. No expongas servicios internos directamente.
- Mutaciones requieren cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`; obtén el token desde `GET /api/v1/csrf`. El Gateway también restringe el `Origin` de métodos modificadores cuando se envía.
- El rate limiter del Gateway usa límites de 20 solicitudes de autenticación y 300 generales por IP cada 60 segundos. Actualmente es local a cada proceso, no compartido entre instancias. Detrás de ALB usa el último valor de `X-Forwarded-For`; la red debe asegurar que el Gateway solo recibe tráfico del balanceador y que el ALB agrega esa cabecera.
- Registro público crea rol `user`. El rol `admin` se comprueba en el backend para moderación y no se obtiene desde el frontend.
- Nunca guardes secretos en Git. En producción deshabilita CAPTCHA bypass/logs de verificación, usa `COOKIE_SECURE=true` y proporciona `RANKINGS_JWT_SECRET`, `INTERNAL_SERVICE_KEY`, configuración de correo y credenciales AWS mediante gestor de secretos o roles IAM.

Variables frecuentes: `INTERNAL_SERVICE_KEY`, `RANKINGS_JWT_SECRET`, `COOKIE_SECURE`, `FRONTEND_ORIGINS`, `IDENTITY_SERVICE_URL`, `CATALOG_SERVICE_URL`, `SOCIAL_SERVICE_URL`, `TURNSTILE_SECRET`, `RESEND_API_KEY`, `VERIFICATION_FROM_EMAIL`, `VERIFICATION_URL`, `MEDIA_S3_BUCKET`, `AWS_REGION` y las variables de base de datos. Para el mapa de variables por tarea, perfiles y preflight consulta [Preparación para AWS](AWS_DEPLOYMENT_PREPARATION.md).

## Medios e integración externa

Social guarda metadatos de medios en su base y binarios en un bucket S3 privado. El cliente solicita una URL firmada para subir, hace `PUT` directo a S3 y confirma la carga; el servidor comprueba tamaño, tipo MIME y firma del archivo. Se aceptan JPEG, PNG y WebP, hasta 10 MiB por imagen, cuatro adjuntos y 20 MiB por post. Las URLs de carga/lectura expiran; los enlaces de video son externos HTTPS y no se descargan ni incrustan. Configura CORS exacto del bucket y ciclo de vida para objetos pendientes según la guía AWS.

Identity usa Turnstile y Resend cuando están configurados. Los bypasses y la salida de enlaces en log solo se admiten para desarrollo/pruebas; no los actives en producción.

## Límites conocidos del producto

- El endpoint `featured` presenta publicaciones recientes; no es un ranking editorial.
- La paginación del feed y moderación completa del contenido siguen siendo áreas de evolución; la cola de moderación sí admite cursor.
- La sincronización de ratings entre Social y Catalog usa llamadas HTTP y no tiene outbox/transacción distribuida.
- Los videos se manejan como enlaces externos. Hay una cola de moderación para medios, pero no se debe asumir que el análisis o la decisión de moderación son automáticos.
- El preflight Compose no crea recursos AWS. La arquitectura, seguridad de red, observabilidad, dominio/TLS y recursos administrados requieren configuración externa.

## Documentos

- [Manual de usuario](MANUAL_USUARIO.md): recorridos de interfaz.
- [Preparación para AWS](AWS_DEPLOYMENT_PREPARATION.md): tareas, variables, recursos y preflight de despliegue.
- [OpenAPI](../contracts/openapi/): contratos de servicios.
