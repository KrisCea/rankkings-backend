# Entrega separada — RankKings backend

Este directorio corresponde exclusivamente al repositorio `rankkings-backend`. Contiene cuatro proyectos Maven separados en `services/`, independientes del repositorio React.

## Requisitos

- JDK 25 configurado mediante `JAVA_HOME` (los `pom.xml` originales solicitan Java 25).
- Conexión para que Maven Wrapper descargue Maven y dependencias si no están en caché.
- Windows PowerShell: ejecutar cada microservicio en una terminal diferente.

```powershell
cd services\catalog-service
.\mvnw.cmd spring-boot:run     # localhost:8082
```

Repetir el comando en `services\identity-service` (8081), `services\social-service` (8083) y `services\api-gateway` (8080). El Gateway enruta prefijos `/api` hacia cada servicio local.

## Estado real

- Gateway: rutas y CORS para desarrollo configurados; se elimina `X-User-Id` recibido del cliente para impedir una suplantación trivial vía Gateway.
- Catalog: endpoints de lectura y biblioteca personal parcialmente implementados.
- Identity: todavía no implementa registro, login, sesión ni perfiles (solo estructura Spring Boot).
- Social: todavía no implementa posts, votos ni comentarios (solo estructura Spring Boot).
- El Gateway aún no valida la sesión ni genera una identidad interna confiable: operaciones privadas deben permanecer inaccesibles por el Gateway hasta incorporar autenticación.
- No se ha comprobado una ejecución extremo a extremo ni un build Java 25 en esta entrega.

El catálogo posee un endpoint `/internal/...` sin autenticación y requiere aislamiento de red antes de despliegue. No publiques puertos internos ni uses los servicios así en producción.

## Integración de repositorios

Frontend: `VITE_API_URL=http://localhost:8080/api`. API Gateway: variables `IDENTITY_SERVICE_URL`, `CATALOG_SERVICE_URL`, `SOCIAL_SERVICE_URL`, `FRONTEND_ORIGINS`, `SERVER_PORT`. El código frontend no se guarda aquí.

El ZIP omite `.git` e incluye todo el código fuente y cambios existentes en el paquete integrado previo. Aplica los cambios sobre tu checkout existente y revisa `git diff` para conservar también tu historial local.
