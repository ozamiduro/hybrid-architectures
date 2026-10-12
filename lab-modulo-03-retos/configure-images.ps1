param(
  [string]$EcrRegistry = "123456789012.dkr.ecr.us-east-1.amazonaws.com",
  [string]$ProductRepo = "product-service",
  [string]$Tag1 = "1.0.0",
  [string]$Tag2 = "2.0.0"
)
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
Get-ChildItem "$base\k8s\advanced" -Recurse -Filter *.yaml | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace('PRODUCT_IMAGE_BLUE', "$EcrRegistry/$ProductRepo`:$Tag1")
  $content = $content.Replace('PRODUCT_IMAGE_GREEN', "$EcrRegistry/$ProductRepo`:$Tag2")
  $content = $content.Replace('PRODUCT_IMAGE_STABLE', "$EcrRegistry/$ProductRepo`:$Tag1")
  $content = $content.Replace('PRODUCT_IMAGE_CANARY', "$EcrRegistry/$ProductRepo`:$Tag2")
  $content = $content.Replace('PRODUCT_IMAGE_V2', "$EcrRegistry/$ProductRepo`:$Tag2")
  $content = $content.Replace('PRODUCT_IMAGE', "$EcrRegistry/$ProductRepo`:$Tag1")
  Set-Content $_.FullName $content -NoNewline
}
Write-Host 'Imágenes configuradas en k8s/advanced/'
