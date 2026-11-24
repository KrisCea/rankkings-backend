# Manual de usuario — RankKings

## Acceso

1. Abra `http://localhost:5173`.
2. Seleccione **Iniciar sesión**.
3. Ingrese correo y contraseña de una cuenta con el correo confirmado.
4. Use **Cerrar sesión** para finalizar la sesión.

## Registro y verificación

1. Seleccione **Registrarse**.
2. Complete usuario, nombre visible, correo y contraseña.
3. Complete el captcha.
4. Verifique el correo mediante el enlace enviado o mostrado por el servicio Identity en modo local.
5. Inicie sesión después de la verificación.

## Navegación pública

Puede consultar el feed, publicaciones, perfiles públicos y fichas del catálogo sin iniciar sesión.

## Biblioteca

1. Abra una obra.
2. Pulse **Guardar** para añadirla a la biblioteca.
3. Desde **Mi biblioteca**, consulte y quite elementos guardados.

## Publicaciones y reseñas

1. Inicie sesión.
2. Abra **Crear publicación**.
3. Seleccione una obra del catálogo.
4. Escriba el texto y, si corresponde, agregue imágenes o enlaces externos.
5. Para reseñar una obra, use el flujo **Reseña** desde la ficha o publicación asociada.

## Interacciones

Los usuarios autenticados pueden votar, puntuar, compartir, comentar y dar like a comentarios. Solo el autor puede eliminar su propio contenido, salvo que el usuario tenga rol `admin`.

## Moderación

Confirmar el correo no otorga la insignia de cuenta verificada. Desde **Verificación de cuenta**, cualquier cuenta personal o de empresa puede solicitarla con un enlace HTTPS y una explicación. Un administrador revisa la solicitud y puede aprobarla o rechazarla con un motivo; una solicitud rechazada se puede volver a enviar.

Solo los Rankable Items nuevos de cuentas sin insignia quedan pendientes para revisión. Si la cuenta del autor está verificada, el Rankable Item se aprueba y verifica automáticamente. Las reseñas, comentarios y adjuntos nuevos se publican sin aprobación previa. Los registros que ya estaban pendientes antes de este cambio conservan su estado.

Las cuentas con rol `admin` pueden abrir **Moderación** desde el menú del perfil para revisar Rankable Items y los registros antiguos que ya estuvieran pendientes.

Para dar el rol `admin` a otra cuenta, abre **Gestión de usuarios** desde el mismo menú e ingresa el correo de una cuenta registrada y con el correo confirmado. La cuenta promovida debe iniciar sesión o recargar la aplicación para ver las opciones de administración.

## Solución de problemas

- Si una acción devuelve `401`, vuelva a iniciar sesión.
- Si una acción devuelve `403`, la cuenta no tiene permisos o el usuario no es administrador.
- Si el catálogo no carga, confirme que Gateway, Catalog e Identity estén iniciados.
- Si el correo no llega, revise la consola de Identity en modo local o configure Resend en producción.
