# Identity y Social — implementación incremental

## Qué se agregó

- **Identity (8081)**: registro con validación de Turnstile o bypass explícito sólo local, PBKDF2-HMAC-SHA256, usuario persistido con H2, tokens de verificación de un uso, entrega de enlace mediante Resend o log local opt-in, login con cookie `rk_token` HttpOnly/SameSite=Lax, sesión aleatoria guardada sólo por SHA-256, logout, `GET /users/me`, perfiles y follows, contactos para compartir y endpoints internos de consulta.
- **Gateway (8080)**: elimina los encabezados `X-User-Id`, `X-Username`, `X-Internal-Key` entrantes; valida la sesión contra Identity y reinyecta el usuario autenticado y el secreto entre servicios; verifica `Origin` para peticiones modificadoras.
- **Social (8083)**: listado y detalle de posts, feed destacado por fecha, creación de publicaciones asociadas a un item existente, reseñas, votos, puntuación 1–10, compartir, comentarios, respuestas (un nivel), likes y post por autor.
- **Catalog (8082)**: filtro de confianza que sólo permite peticiones con `X-Internal-Key` del Gateway/otros servicios; conserva los modelos anteriores. Social consulta catálogo y replica las estadísticas de notas en la creación y eliminación de ratings de tipo item.
- **Frontend**: botón Publicar operativo; pantalla `/create-post` y petición `POST /posts`; captcha de desarrollo opt-in en Vite.

## Desarrollo local (Windows)

1. Instalar **JDK 25**, npm/Node compatible con Vite y conexión para descargar Maven la primera vez.
2. Abrir PowerShell en `rankkings-backend` y ejecutar `./scripts/start-local.ps1`: abre cuatro ventanas de servicio con un secreto común **aleatorio en memoria**. Por defecto omite Turnstile y registra el enlace de verificación **sólo localmente**. No cerrar esas ventanas.
3. Abrir otro PowerShell en el repositorio del frontend, `cd frontend`, ejecutar `npm ci` y `npm run dev`. Carga `.env.development` con la URL del Gateway y el captcha local de desarrollo.
4. Registrar un usuario. Copiar la URL de verificación que aparece en la **ventana de Identity** y abrirla en el navegador. Iniciar sesión.
5. Abrir `http://localhost:5173/create-post`, utilizar el item de prueba `item-1`, crear reseña y probar interacciones.
6. Opcional: desde backend ejecutar `./scripts/smoke-local.ps1` con las credenciales de una cuenta ya verificada. **Crea registros de prueba** en H2.

Los cuatro servicios deben compartir `INTERNAL_SERVICE_KEY` en el mismo entorno y sólo el Gateway debe exponerse públicamente. Si se ejecutan por separado, configurar la misma clave en los cuatro procesos.

### Configuración de correo real

Identity usa Resend para correo real. Configura `RESEND_API_KEY`, `VERIFICATION_FROM_EMAIL` (remitente verificado con Resend) y `VERIFICATION_URL` (URL pública del frontend terminada en `/verify-email`). En producción no se inventa una URL local: si falta la configuración, el registro falla con un error explícito.

Para probar el envío real desde `start-local.ps1`, define antes de ejecutarlo `DEV_VERIFICATION_LOGGING=false`, `RESEND_API_KEY`, `VERIFICATION_FROM_EMAIL` y `VERIFICATION_URL`. En el frontend configura también `VITE_DEV_VERIFICATION_LOGGING=false`. No compartas ni guardes las claves en el repositorio. Por defecto, `DEV_VERIFICATION_LOGGING=true` imprime el enlace en la consola de Identity sólo con el perfil `local`.

### Configuración de CAPTCHA real

Para usar Cloudflare Turnstile, configura `TURNSTILE_SECRET` en Identity y `VITE_TURNSTILE_SITE_KEY` en el frontend. Para ejecutar el frontend y backend locales con validación real, define `DEV_CAPTCHA_BYPASS=false` antes de iniciar el backend y `VITE_DEV_CAPTCHA_BYPASS=false` en el frontend. Sin una clave secreta el backend falla explícitamente; el bypass sólo se acepta con el perfil `local` o `test` y nunca con `prod`.

### Producción — trabajo pendiente obligatorio

El desarrollo es **un MVP**, no un sistema listo para exponer en internet. Antes de producción: implementar rate limiting de login/registro/verificación; protección adicional de CSRF según modelo de despliegue, pruebas de seguridad, observabilidad y alertas; TLS y `COOKIE_SECURE=true`; secretos externos como `RANKINGS_JWT_SECRET` e `INTERNAL_SERVICE_KEY`; firewall/red privada entre servicios; envío y reintento robustos de correos; protección de datos; estrategia transaccional eventual/outbox para sincronizar ratings entre Social y Catalog; paginación, moderación y búsqueda destacada real. JWT y Bearer están implementados, pero siguen pendientes los controles operativos y de seguridad anteriores.

El filtro de catálogo requiere `INTERNAL_SERVICE_KEY` incluso al llamar `localhost:8082` directamente. Es intencional: para pruebas HTTP utilizar `http://localhost:8080/api/...` y la sesión del navegador.

**Limitación funcional**: crear posts actualmente requiere un ítem existente (`itemId`); no hay carga de medios ni creación de obras desde la pantalla. `featured` muestra publicaciones recientes, no ranking editorial. Los datos H2 desaparecen al detener los servicios; `catalog-service` mantiene su esquema de `prod` propio.

**Pruebas en este paquete**: `tsc --noEmit` completó correctamente. No se pudieron compilar ni ejecutar pruebas Java en el entorno de elaboración: JDK disponible 21 frente al 25 declarado, Maven no instalado y Maven Wrapper sin acceso de descarga.
