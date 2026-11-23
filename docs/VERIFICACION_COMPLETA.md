# Verificación completa — estado actual

## Pruebas automatizadas

- Identity: `BUILD SUCCESS`, 6 pruebas.
- Catalog: `BUILD SUCCESS`, 8 pruebas.
- Social: `BUILD SUCCESS`, 36 pruebas.
- API Gateway: `BUILD SUCCESS`, 4 pruebas.
- Frontend: build de producción aprobado.

## Integración local

Validación ejecutada contra los cuatro servicios activos:

- Gateway: `/actuator/health` respondió `UP`.
- CSRF: endpoint `/api/v1/csrf` emitió `XSRF-TOKEN`.
- Catálogo: `/api/v1/items` respondió 59 elementos.
- Login demo: `ana_demo`, rol `user`.
- Sesión: `/api/v1/users/me` respondió `ana_demo`.
- Feed: `/api/v1/posts` respondió 23 publicaciones.
- Logout: completado.

## Seguridad agregada

- JWT en cookie HttpOnly y `Authorization: Bearer`.
- Validación de firma y rol en el Gateway.
- CSRF con cookie XSRF y cabecera `X-XSRF-TOKEN`.
- Límites de solicitudes: 20 autenticaciones por minuto y 300 solicitudes generales por minuto por IP.
- `X-Request-Id` generado o propagado por el Gateway.
- Prometheus habilitado en los cuatro `/actuator/prometheus`.

## PostgreSQL

Las migraciones Flyway existen para Identity, Catalog y Social. No fue posible ejecutar PostgreSQL en esta máquina porque el daemon Linux de Docker Desktop no estaba activo y el ejecutable de Docker Desktop no estaba disponible. La ejecución reproducible queda pendiente como preflight externo al ejecutar Docker o AWS.
