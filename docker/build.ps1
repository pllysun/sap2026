<#
.SYNOPSIS
    Build and push CSUFTSAP Docker image

.DESCRIPTION
    Supports proxy configuration (default 127.0.0.1:7890), custom image name/tag, and push to DockerHub.

.EXAMPLE
    .\docker\build.ps1
    .\docker\build.ps1 -Push
    .\docker\build.ps1 -NoProxy
    .\docker\build.ps1 -ImageName "myuser/sap" -Tag "v1.0" -Push
#>

param(
    [string]$ImageName = "pllysun/sap",
    [string]$Tag = "latest",
    [string]$Proxy = "http://host.docker.internal:7890",
    [switch]$Push,
    [switch]$NoProxy
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  CSUFTSAP Docker Builder" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

$fullImageName = "${ImageName}:${Tag}"
Write-Host "[INFO] Image: $fullImageName" -ForegroundColor Green

# ===== Proxy =====
$buildArgs = @()
if (-not $NoProxy) {
    Write-Host "[INFO] Proxy: $Proxy" -ForegroundColor Yellow
    $buildArgs += "--build-arg", "HTTP_PROXY=$Proxy"
    $buildArgs += "--build-arg", "HTTPS_PROXY=$Proxy"
    $buildArgs += "--build-arg", "http_proxy=$Proxy"
    $buildArgs += "--build-arg", "https_proxy=$Proxy"
} else {
    Write-Host "[INFO] No proxy" -ForegroundColor Yellow
}

# ===== Build =====
Write-Host ""
Write-Host "[BUILD] Building Docker image..." -ForegroundColor Cyan
Write-Host ""

$buildCmd = @("docker", "build", "-t", $fullImageName, "-f", "docker/Dockerfile")
$buildCmd += $buildArgs
$buildCmd += "."

Write-Host "Command: $($buildCmd -join ' ')" -ForegroundColor DarkGray
Write-Host ""

& $buildCmd[0] $buildCmd[1..($buildCmd.Length - 1)]

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[ERROR] Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[BUILD] Build succeeded!" -ForegroundColor Green
Write-Host "[BUILD] Image: $fullImageName" -ForegroundColor Green

# ===== Image info =====
Write-Host ""
Write-Host "[INFO] Image size:" -ForegroundColor Cyan
docker image ls $ImageName --format "  {{.Repository}}:{{.Tag}}  {{.Size}}"

# ===== Push =====
if ($Push) {
    Write-Host ""
    Write-Host "[PUSH] Pushing to DockerHub..." -ForegroundColor Cyan

    if (-not $NoProxy) {
        $env:HTTP_PROXY = $Proxy
        $env:HTTPS_PROXY = $Proxy
        Write-Host "[PUSH] Push proxy: $Proxy" -ForegroundColor Yellow
    }

    docker push $fullImageName

    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "[ERROR] Push failed! Please run 'docker login' first." -ForegroundColor Red
        exit 1
    }

    if (-not $NoProxy) {
        Remove-Item Env:\HTTP_PROXY -ErrorAction SilentlyContinue
        Remove-Item Env:\HTTPS_PROXY -ErrorAction SilentlyContinue
    }

    Write-Host ""
    Write-Host "[PUSH] Push succeeded!" -ForegroundColor Green
}

# ===== Usage =====
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Usage" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "# Default (H2 embedded database):" -ForegroundColor White
Write-Host "docker run -d -p 80:80 \" -ForegroundColor DarkGray
Write-Host "  -v sap-data:/app/data \" -ForegroundColor DarkGray
Write-Host "  -v sap-logs:/app/logs \" -ForegroundColor DarkGray
Write-Host "  -v sap-uploads:/app/uploads \" -ForegroundColor DarkGray
Write-Host "  $fullImageName" -ForegroundColor DarkGray
Write-Host ""
Write-Host "# MySQL mode:" -ForegroundColor White
Write-Host "docker run -d -p 80:80 \" -ForegroundColor DarkGray
Write-Host "  -e MYSQL_URL=jdbc:mysql://host:3306/sap \" -ForegroundColor DarkGray
Write-Host "  -e MYSQL_USER=root \" -ForegroundColor DarkGray
Write-Host "  -e MYSQL_PASSWORD=your_password \" -ForegroundColor DarkGray
Write-Host "  -v sap-logs:/app/logs \" -ForegroundColor DarkGray
Write-Host "  -v sap-uploads:/app/uploads \" -ForegroundColor DarkGray
Write-Host "  $fullImageName" -ForegroundColor DarkGray
Write-Host ""
Write-Host "# HTTPS mode (with SSL certificate):" -ForegroundColor White
Write-Host "docker run -d -p 80:80 -p 443:443 \" -ForegroundColor DarkGray
Write-Host "  -e DOMAIN=sap.example.com \" -ForegroundColor DarkGray
Write-Host "  -v sap-data:/app/data \" -ForegroundColor DarkGray
Write-Host "  -v sap-logs:/app/logs \" -ForegroundColor DarkGray
Write-Host "  -v sap-ssl:/etc/letsencrypt \" -ForegroundColor DarkGray
Write-Host "  $fullImageName" -ForegroundColor DarkGray
Write-Host ""
Write-Host "# Access:" -ForegroundColor White
Write-Host "  User:  http://localhost/" -ForegroundColor DarkGray
Write-Host "  Admin: http://localhost/admin/" -ForegroundColor DarkGray
Write-Host ""
