# RankKings — estado de integración local

## Cambios de esta iteración

- El API Gateway escucha en `8080` (configurable con `SERVER_PORT`).
- `identity-service`, `catalog-service` y `social-service` quedan asignados a `8081`, `8082` y `8083`.
- El Gateway publica el prefijo `/api`, lo elimina al reenviar y enruta las rutas del frontend por dominio.
- `/api/users/me/saved` se dirige al catálogo; `/api/users/{username}/posts` y `/api/items/{itemId}/reviews` se dirigen a Social antes de las rutas genéricas.
- CORS admite por defecto los orígenes Vite locales y credenciales. En otros entornos definir `FRONTEND_ORIGINS` con orígenes explícitos.
- `frontend/.env.example` apunta al Gateway local (`http://localhost:8080/api`). El `.env` privado existente no se modifica.

## Matriz de rutas del Gateway

| Ruta pública | Servicio destino | Estado real |
|---|---|---|
| `/api/auth/**` | Identity `8081` | Enrutamiento configurado; controladores pendientes |
| `/api/users/me/saved` | Catalog `8082` | Implementado en Catalog; requiere identidad validada |
| `/api/users/{username}/posts` | Social `8083` | Enrutamiento configurado; controladores pendientes |
| `/api/users/**` | Identity `8081` | Enrutamiento configurado; controladores pendientes |
| `/api/items/{itemId}/reviews` | Social `8083` | Enrutamiento configurado; controlador pendiente |
| `/api/items/**` | Catalog `8082` | Lectura, hijos y guardados implementados parcialmente |
| `/api/posts/**`, `/api/comments/**` | Social `8083` | Enrutamiento configurado; controladores pendientes |

## Importante: seguridad aún no terminada

El Gateway todavía **no valida** la cookie `rk_token`, no crea una identidad autenticada ni inyecta `X-User-Id`. No se debe confiar en un `X-User-Id` recibido desde Internet. Los endpoints protegidos de Catalog responderán `401` hasta implementar la validación de sesión y el reenvío interno seguro. No se ha añadido un filtro que simule autenticación.

CORS no sustituye CSRF. Antes de producción hay que implementar protección CSRF para operaciones mutables, cookies `HttpOnly`/`Secure`/`SameSite`, límites de tasa, secretos externos y autorización por recurso.

## Verificación de esta iteración

- `tsc -b` del frontend: correcto tras tipar explícitamente los genéricos de `Tabs` en `ItemPage` y `Profile`.
- El bundle Vite no se pudo verificar en este entorno: la instalación disponible carece del binding nativo Linux de Rolldown. Reinstalar dependencias en Linux con `npm ci` y ejecutar `npm run build`.
- No se pudo ejecutar Maven aquí: el entorno tiene Java 21, mientras que los cuatro `pom.xml` conservan Java 25, y no hay una distribución Maven disponible localmente. No se rebajó la versión Java del proyecto para no alterar una decisión existente.
