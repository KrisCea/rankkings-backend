# RankKings Backend

Backend de RankKings organizado como un conjunto de microservicios Spring Boot. El repositorio contiene el gateway y los servicios de identidad, catálogo y social. Cada servicio Java se construye de forma independiente desde su propio directorio.

## Tecnologías

- Java 25 (LTS)
- Spring Boot 4.1.2
- Maven Wrapper incluido en cada servicio
- Spring Data JPA e Hibernate
- H2 para desarrollo local y PostgreSQL para el perfil `prod`
- Springdoc OpenAPI en `catalog-service`

Instala un JDK 25 y configura `JAVA_HOME` antes de compilar o ejecutar. Los cuatro POM declaran Java 25 como versión de compilación.

## Estructura del repositorio

```text
services/
  api-gateway/       Gateway Spring Cloud
  catalog-service/   Catálogo de obras y biblioteca personal
  identity-service/  Servicio de identidad
  social-service/    Servicio social
contracts/
  openapi/           Contratos OpenAPI
infra/               Recursos de infraestructura
```

Los servicios tienen POM y Maven Wrapper propios; la raíz no es un proyecto Maven multimódulo. El gateway, identidad y social incluyen actualmente la estructura base de Spring Boot. La funcionalidad de catálogo descrita en este documento está implementada en `catalog-service`.

## Ejecutar `catalog-service`

En Windows PowerShell, desde la raíz del repositorio:

```powershell
cd services\catalog-service
.\mvnw.cmd spring-boot:run
```

El servicio escucha por defecto en `http://localhost:8082`. Para compilar y ejecutar sus pruebas:

```powershell
.\mvnw.cmd test
```

También se puede empaquetar el servicio con `.\mvnw.cmd package`.

### Base de datos local

Sin perfil activo, `application.yml` configura una base H2 en memoria llamada `catalog_db`, en modo de compatibilidad PostgreSQL. Hibernate actualiza el esquema automáticamente (`ddl-auto: update`) y `DatosIniciales` agrega obras de ejemplo si la tabla está vacía. La base en memoria se pierde al detener el proceso.

La consola H2 se configura en:

```text
http://localhost:8082/h2-console
```

Usa JDBC URL `jdbc:h2:mem:catalog_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`, usuario `sa` y contraseña vacía.
Es una herramienta de desarrollo: no expongas `/h2-console` en producción.

### Perfil PostgreSQL

El perfil `prod` configura PostgreSQL y cambia Hibernate a `ddl-auto: validate`, por lo que la base debe tener previamente un esquema compatible. Configura las variables de entorno antes de iniciar la aplicación:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/catalog_db"
$env:SPRING_DATASOURCE_USERNAME = "rankkings"
$env:SPRING_DATASOURCE_PASSWORD = "<contraseña>"
.\mvnw.cmd spring-boot:run
```

La contraseña no tiene un valor por defecto en la configuración de producción; debe suministrarse mediante `SPRING_DATASOURCE_PASSWORD`. No guardes credenciales reales en el repositorio. La inicialización de datos de ejemplo está pensada para desarrollo, no como mecanismo de migración de producción.

## API de catálogo

La especificación OpenAPI y Swagger UI están disponibles cuando el servicio está en ejecución:

- Swagger UI: `http://localhost:8082/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

| Método | Ruta | Uso | Autenticación esperada |
|---|---|---|---|
| `GET` | `/items/{itemId}` | Obtener una obra y su estado de guardado para el usuario | No requiere usuario |
| `GET` | `/items/{itemId}/children` | Obtener las obras hijas ordenadas por posición | No requiere usuario |
| `PUT` | `/items/{itemId}/save` | Guardar una obra en la biblioteca del usuario | `X-User-Id` obligatorio |
| `DELETE` | `/items/{itemId}/save` | Quitar una obra de la biblioteca | `X-User-Id` obligatorio |
| `GET` | `/users/me/saved` | Listar las obras guardadas por el usuario | `X-User-Id` obligatorio |
| `PUT` | `/internal/items/{itemId}/rating-stats` | Actualizar estadísticas de rating | Endpoint interno; debe restringirse a servicios de confianza |

Ejemplos locales:

```powershell
curl.exe http://localhost:8082/items/item-1
curl.exe http://localhost:8082/items/item-1/children
curl.exe -H "X-User-Id: user-1" http://localhost:8082/users/me/saved
curl.exe -X PUT -H "X-User-Id: user-1" http://localhost:8082/items/item-1/save
curl.exe -X DELETE -H "X-User-Id: user-1" http://localhost:8082/items/item-1/save
curl.exe -X PUT -H "Content-Type: application/json" `
  -d '{"averageRating":9.2,"ratingsCount":20}' `
  http://localhost:8082/internal/items/item-1/rating-stats
```

Las obras de ejemplo usan `item-1` para el álbum y `item-2` y `item-3` para sus canciones hijas. El encabezado `X-User-Id` identifica al usuario que consume las operaciones de biblioteca; en un despliegue integrado, un componente de confianza debe establecerlo y evitar que el cliente pueda suplantarlo. El endpoint de actualización de ratings está destinado a comunicación interna y no implementa autenticación propia.

### Modelos y comportamiento

- **`RankableItem`** representa una obra catalogable. Incluye categoría, título, descripción, creador, identificadores externos, enlaces, ratings y campos de jerarquía (`parentId`, `position`).
- **`SavedItem`** representa una obra guardada por un usuario. Una restricción única impide duplicar la combinación usuario/obra.
- Guardar y quitar una obra son operaciones idempotentes. `savesCount` se recalcula al cambiar el estado de guardado.
- Las respuestas de obra se serializan mediante `RankableItemDto`; incluyen `savedByCurrentUser`. La lista personal devuelve `SavedItemDto` con la obra y la fecha `savedAt`.
- Al actualizar el rating de una obra hija, el servicio recalcula el promedio de rating de sus obras hermanas calificadas y lo actualiza en la obra padre.
- Las obras inexistentes devuelven `404` con un objeto `{ "code": "RESOURCE_NOT_FOUND", "message": "..." }`. Las operaciones de biblioteca sin `X-User-Id` devuelven `401`.

## Ejecutar otros servicios

Cada servicio tiene su propio wrapper. Por ejemplo, para ejecutar el gateway desde PowerShell:

```powershell
cd services\api-gateway
.\mvnw.cmd spring-boot:run
```

Sustituye `api-gateway` por `identity-service` o `social-service` para iniciar los otros proyectos. Revisa el `application.properties` y el POM de cada módulo para su configuración. No se declaran puertos específicos en sus archivos de propiedades actuales.

Para ejecutar pruebas en cualquier módulo, entra a su directorio y ejecuta `.\mvnw.cmd test`.
