# Preparación para despliegue en AWS

Este repositorio ya contiene los artefactos necesarios para construir y ejecutar los cuatro servicios como contenedores. AWS no se modifica ni se despliega desde este repositorio hasta proporcionar credenciales y recursos.

## Arquitectura recomendada

- ECS Fargate para los cuatro servicios.
- Application Load Balancer público delante de `api-gateway`.
- Service Discovery o balanceadores internos para Identity, Catalog y Social.
- RDS PostgreSQL con una base por servicio.
- S3 privado para medios de Social.
- CloudFront y S3 o Amplify para el frontend.
- Secrets Manager o Parameter Store para secretos.
- ECR para las imágenes.

## Contenedores

Crear las imágenes localmente:

```powershell
.\scripts\build-images.ps1
```

Para crear y subir a un registro ECR:

```powershell
.\scripts\build-images.ps1 -Registry <account-id>.dkr.ecr.<region>.amazonaws.com/rankkings
```

Cada Dockerfile ejecuta Maven, crea un JAR ejecutable y usa Java 25 como usuario no administrador.

## Recursos AWS pendientes

Crear antes del primer despliegue:

1. Una VPC con subredes públicas y privadas.
2. Tres bases RDS PostgreSQL o una instancia RDS con tres bases separadas.
3. Un bucket S3 privado para `MEDIA_S3_BUCKET`.
4. Un repositorio ECR por servicio o un repositorio común.
5. Tres roles IAM:
   - acceso mínimo de ejecución ECS;
   - acceso mínimo de lectura/escritura S3 solo para Social;
   - acceso opcional a Secrets Manager.
6. Secrets para las contraseñas RDS, la clave interna, Turnstile, Resend y otros valores sensibles.
7. Un Application Load Balancer y registros DNS HTTPS.
8. Un certificado ACM para el dominio público.

## Variables por servicio

Todas las claves pueden entregarse mediante variables de entorno de ECS o secretos referenciados desde task definitions.

### Gateway

- `SPRING_PROFILES_ACTIVE=prod`
- `SERVER_PORT=8080`
- `IDENTITY_SERVICE_URL`
- `CATALOG_SERVICE_URL`
- `SOCIAL_SERVICE_URL`
- `INTERNAL_SERVICE_KEY`
- `RANKINGS_JWT_SECRET`
- `FRONTEND_ORIGINS`

### Identity

- `SPRING_PROFILES_ACTIVE=prod`
- `SERVER_PORT=8081`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `INTERNAL_SERVICE_KEY`
- `COOKIE_SECURE=true`
- `TURNSTILE_SECRET`
- `RESEND_API_KEY`
- `VERIFICATION_FROM_EMAIL`
- `VERIFICATION_URL`
- `DEV_CAPTCHA_BYPASS=false`
- `DEV_VERIFICATION_LOGGING=false`

### Catalog

- `SPRING_PROFILES_ACTIVE=prod`
- `SERVER_PORT=8082`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `INTERNAL_SERVICE_KEY`
- `SEED_CATALOG=false`

### Social

- `SPRING_PROFILES_ACTIVE=prod`
- `SERVER_PORT=8083`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `INTERNAL_SERVICE_KEY`
- `IDENTITY_SERVICE_URL`
- `CATALOG_SERVICE_URL`
- `MEDIA_S3_BUCKET`
- `AWS_REGION`

## Migraciones

Cada servicio aplica sus migraciones Flyway al iniciar. Los archivos están en `src/main/resources/db/migration` de cada servicio.

Antes del despliegue, comprobar que RDS permita conexiones desde ECS y que el usuario tenga permiso para crear tablas. Las migraciones iniciales crean el esquema completo para Identity, Catalog y Social.

No se debe cambiar `ddl-auto=validate` ni ejecutar datos demo en producción.

## S3

Configurar el bucket en modo privado. El task role de Social necesita como mínimo:

- `s3:PutObject`
- `s3:GetObject`
- `s3:DeleteObject`
- `s3:CopyObject`
- `s3:HeadObject`

Configurar CORS para permitir `PUT` desde el origen exacto del frontend y los encabezados `Content-Type` e `If-None-Match`. Configurar el ciclo de vida para borrar objetos bajo `pending-post-media/` después de un día.

## Health checks

Los servicios exponen:

```text
/actuator/health/liveness
/actuator/health/readiness
```

El gateway expone `/actuator/health`.

## Preflight local

Copiar `.env.aws.example` a `.env.aws`, reemplazar valores y probar contenedores:

```powershell
docker compose --env-file .env.aws -f docker-compose.aws.yml up --build
```

Ese compose requiere bases PostgreSQL accesibles según las URLs configuradas.

## Estado de preparación

Implementado en el código:
- JWT, Spring Security, CSRF y límites de solicitudes en el Gateway.
- Métricas Prometheus y identificador `X-Request-Id`.
- Perfiles de producción con `ddl-auto=validate` y Flyway.
- Dockerfiles y Compose previo a AWS.

Pendiente externo al repositorio:
- Docker Desktop activo para ejecutar PostgreSQL y comprobar las tres migraciones.
- Credenciales reales de RDS, S3, Turnstile y Resend.
- Recursos ECS/Fargate, ALB, ACM, DNS y Secrets Manager.
