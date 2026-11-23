# Manual de usuario — RankKings

## Acceso

1. Abra `http://localhost:5173`.
2. Seleccione **Iniciar sesión**.
3. Ingrese correo y contraseña de una cuenta verificada.
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

Las cuentas con rol `admin` pueden ingresar al panel de moderación y aprobar o rechazar publicaciones y reseñas pendientes.

## Solución de problemas

- Si una acción devuelve `401`, vuelva a iniciar sesión.
- Si una acción devuelve `403`, la cuenta no tiene permisos o el usuario no es administrador.
- Si el catálogo no carga, confirme que Gateway, Catalog e Identity estén iniciados.
- Si el correo no llega, revise la consola de Identity en modo local o configure Resend en producción.
