$ErrorActionPreference = 'Stop'

$platform = if ($IsWindows) { 'Windows' } elseif ($IsMacOS) { 'macOS' } elseif ($IsLinux) { 'Linux' } else { 'Unknown' }
if ($platform -eq 'Unknown') {
    throw 'Unsupported platform.'
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker is required but was not found in PATH.'
}

$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$imageName = if ($env:IMAGE_NAME) { $env:IMAGE_NAME } else { 'authz-policy-engine:local' }
$port = if ($env:PORT) { $env:PORT } else { '8080' }

Write-Host "Building Docker image: $imageName"
& docker build -t $imageName $rootDir

Write-Host "Starting REST API on http://localhost:$port"
Write-Host "Health check: curl http://localhost:$port/health"
Write-Host "Permission check example: curl -X POST http://localhost:$port/v1/permission-checks -H 'Content-Type: application/json' --data @examples/rest/scenario-1-can-view.json"

& docker run --rm -p "${port}:8080" $imageName
