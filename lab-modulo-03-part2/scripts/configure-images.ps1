param(
  [string]$AwsRegion = "us-east-1",
  [string]$Tag = "1.1.0"
)

$AccountId = (aws sts get-caller-identity --query Account --output text).Trim()
$RegistryUri = "$AccountId.dkr.ecr.$AwsRegion.amazonaws.com"

$files = @{
  "k8s/05-product-service.yaml" = "$RegistryUri/product-service:$Tag"
  "k8s/06-order-service.yaml"   = "$RegistryUri/order-service:$Tag"
  "k8s/07-web-app.yaml"         = "$RegistryUri/web-app:$Tag"
}

foreach ($entry in $files.GetEnumerator()) {
  $path = $entry.Key
  $image = $entry.Value
  $content = Get-Content $path -Raw
  $content = [regex]::Replace($content, '(?m)^(\s*)image:.*$', "`$1image: $image")
  Set-Content -Path $path -Value $content
}

Write-Host "Imágenes configuradas con TAG=$Tag"
Get-ChildItem k8s/05-product-service.yaml,k8s/06-order-service.yaml,k8s/07-web-app.yaml | Select-String -Pattern 'image:'
