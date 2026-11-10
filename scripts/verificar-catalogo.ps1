param(
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd("/")
$csvPath = Join-Path $PSScriptRoot "..\services\catalog-service\src\main\resources\data\catalogo-ejemplo.csv"
$seedItems = @(Import-Csv $csvPath)
$items = @(Invoke-RestMethod -Uri "$base/api/items" -Method Get)

if ($seedItems.Count -ne 58) {
  throw "El archivo de semilla debería contener 58 obras y contiene $($seedItems.Count)."
}

$expectedCategories = @{
  music = 31
  movies = 6
  books = 6
  videogames = 6
  podcasts = 3
  art = 2
  boardgames = 2
  concerts = 2
}
foreach ($category in $expectedCategories.Keys) {
  $actual = @($seedItems | Where-Object { $_.categoria -eq $category }).Count
  if ($actual -ne $expectedCategories[$category]) {
    throw "La semilla de '$category' debería tener $($expectedCategories[$category]) obras, pero tiene $actual."
  }
}

$itemsById = @{}
foreach ($item in $items) {
  $itemsById[$item.id] = $item
}
foreach ($seed in $seedItems) {
  if (-not $itemsById.ContainsKey($seed.itemId)) {
    throw "No se encontró la obra sembrada '$($seed.itemId)'."
  }
  if ($itemsById[$seed.itemId].category -ne $seed.categoria) {
    throw "La categoría de '$($seed.itemId)' no coincide con la semilla."
  }
}

$expectedIds = @("item-101", "item-110", "item-160", "item-201", "item-301", "item-401")
foreach ($id in $expectedIds) {
  if (-not $itemsById.ContainsKey($id)) {
    throw "No se encontró el identificador de ejemplo '$id'."
  }
}

$children = @(Invoke-RestMethod -Uri "$base/api/items/item-1/children" -Method Get)
$childIds = @($children | ForEach-Object { $_.id })
if (($childIds -join ",") -ne "item-2,item-3,item-4,item-5") {
  throw "Las canciones hijas de item-1 no coinciden con la semilla esperada."
}

Write-Host "Catálogo correcto: las 58 obras, sus categorías, identificadores y relaciones fueron verificadas."
