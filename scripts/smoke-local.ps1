# Prueba de integración contra cuatro servicios ya levantados; requiere una cuenta verificada.
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$email = Read-Host "Email verificado"
$secret = Read-Host "Contraseña" -AsSecureString
$password = [System.Net.NetworkCredential]::new("", $secret).Password
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$payload = @{email=$email; password=$password} | ConvertTo-Json
$login = Invoke-RestMethod -WebSession $session -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body $payload
if (-not $login.isAuthenticated) { throw 'Login falló' }
$me = Invoke-RestMethod -WebSession $session -Uri "$base/users/me" -Method Get
if ($me.id -ne $login.id) { throw 'Sesión inconsistente' }
$post = Invoke-RestMethod -WebSession $session -Uri "$base/posts" -Method Post -ContentType 'application/json' -Body (@{ itemId='item-1'; kind='review'; caption='Reseña de prueba de integración'; authorScore=8.5 } | ConvertTo-Json)
if (-not $post.id) { throw 'No se creó la publicación' }
$voted = Invoke-RestMethod -WebSession $session -Uri "$base/posts/$($post.id)/vote" -Method Post
if (-not $voted.votedByCurrentUser) { throw 'No se registró el voto' }
$rating = Invoke-RestMethod -WebSession $session -Uri "$base/posts/$($post.id)/rating" -Method Put -ContentType 'application/json' -Body (@{score=9} | ConvertTo-Json)
if ($rating.currentUserRating -ne 9) { throw 'No se registró la puntuación' }
$comment = Invoke-RestMethod -WebSession $session -Uri "$base/posts/$($post.id)/comments" -Method Post -ContentType 'application/json' -Body (@{text='Comentario de prueba'} | ConvertTo-Json)
if (-not $comment.id) { throw 'No se creó el comentario' }
$liked = Invoke-RestMethod -WebSession $session -Uri "$base/comments/$($comment.id)/like" -Method Post
if (-not $liked.likedByCurrentUser) { throw 'No se registró el like' }
$comments = Invoke-RestMethod -WebSession $session -Uri "$base/posts/$($post.id)/comments"
if ($comments.Count -lt 1) { throw 'No se recuperaron comentarios' }
Invoke-RestMethod -WebSession $session -Uri "$base/auth/logout" -Method Post | Out-Null
Write-Host "OK: login, sesión, creación de reseña, voto, rating, comentario, like y logout. Post $($post.id)"
