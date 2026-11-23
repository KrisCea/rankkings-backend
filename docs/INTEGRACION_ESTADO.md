# RankKings — Estado de integración

## Arquitectura y seguridad

- El API Gateway escucha en `8080` y enruta a Identity `8081`, Catalog `8082` y Social `8083`.
- Las sesiones se representan con JWT firmados y se transportan en la cookie HttpOnly `rk_token` o como `Authorization: Bearer`.
- El Gateway valida la firma y expiración del JWT, elimina cabeceras de identidad aportadas por el cliente y reinyecta `X-User-Id`, `X-Username` y `X-User-Role`.
- Las rutas administrativas requieren el rol `admin` en el JWT.
- Los registros públicos crean siempre rol `user`; el rol se lee desde Identity y no se acepta desde el navegador.
- Los tres servicios HTTP tienen Spring Security con filtro de aplicación; Catalog y Social mantienen además el filtro de confianza del Gateway.
- En producción, `RANKINGS_JWT_SECRET` e `INTERNAL_SERVICE_KEY` son obligatorios. Nunca usar secretos de desarrollo fuera de local.

## Versionado

La API nueva usa el prefijo `/api/v1`. Se conservan las rutas `/api/**` originales por compatibilidad.

| Dominio | Ruta v1 |
|---|---|
| Identidad | `/api/v1/auth/**`, `/api/v1/users/**` |
| Catálogo | `/api/v1/items/**`, `/api/v1/users/me/saved` |
| Social | `/api/v1/posts/**`, `/api/v1/comments/**`, `/api/v1/admin/moderation/**`, `/api/v1/users/*/posts`, `/api/v1/items/*/reviews` |

## Documentación OpenAPI

- Catalog: `http://localhost:8082/swagger-ui.html`
- Identity: `http://localhost:8081/swagger-ui.html`
- Social: `http://localhost:8083/swagger-ui.html`
- Especificaciones fuente: `contracts/openapi/*.yaml`

## Verificación ejecutada

- Identity: `mvnw test` — pruebas automatizadas aprobadas.
- Catalog: `mvnw test` — 8 pruebas aprobadas.
- Social: `mvnw test` — 36 pruebas aprobadas.
- API Gateway: `mvnw test` — prueba de contexto aprobada.
- Frontend: `npm run build` — build de producción aprobado.
- Frontend: `npm run lint` — lint aprobado.

## Pendiente operativo

- Capturas reales de login, catálogo, creación de contenido y moderación.
- Prueba E2E con los cuatro procesos activos.
- Certificados y secretos de producción.
