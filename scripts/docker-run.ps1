$ErrorActionPreference = 'Stop'

$platform = if ($IsWindows) { 'Windows' } elseif ($IsMacOS) { 'macOS' } elseif ($IsLinux) { 'Linux' } else { 'Unknown' }
if ($platform -eq 'Unknown') {
    throw 'Unsupported platform.'
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker is required but was not found in PATH.'
}

$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$port = if ($env:PORT) { $env:PORT } else { '8080' }
$demoToken = if ($env:DEMO_TOKEN) { $env:DEMO_TOKEN } else { 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1MSIsImlhdCI6MTc3NDkxMTYwMCwiZXhwIjo0MTAyNDQ0ODAwfQ.CamontEZemyd1HDeWeh9HoMgkEzaHMO4WP0GIYwgBXE' }

Write-Host "Starting PostgreSQL and REST API on http://localhost:$port"
Write-Host "Health check: curl http://localhost:$port/health"
Write-Host "Demo JWT for user u1:"
Write-Host $demoToken
Write-Host "Permission check example: curl -H ""Authorization: Bearer $demoToken"" http://localhost:$port/v1/documents/d1/permissions/can_view"
Write-Host "Create user example: curl -X POST -H ""Content-Type: application/json"" --data @examples/write/create-user-u3.json http://localhost:$port/v1/users"
Write-Host "Create document example: curl -X POST -H ""Content-Type: application/json"" --data @examples/write/create-document-d7.json http://localhost:$port/v1/documents"

Push-Location $rootDir
try {
    & docker compose up --build
} finally {
    Pop-Location
}
