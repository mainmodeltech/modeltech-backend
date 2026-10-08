<#
.SYNOPSIS
  Lance le backend en local (JAR sur la machine) contre la base et MinIO de Docker, logs dans ce terminal.

.DESCRIPTION
  - lit .env.dev (DB, JWT, mail, Slack...) ;
  - pointe sur la base / MinIO publies par `docker compose` sur localhost ;
  - fait confiance aux certificats du magasin Windows (necessaire derriere un antivirus ou un proxy
    qui intercepte le TLS : sans cela, e-mails, Slack et reCAPTCHA echouent avec " PKIX path building failed ") ;
  - genere les liens des e-mails (paiement, invitation) vers le front local.

.PARAMETER Build
  Recompile le JAR avant de lancer (tests ignores).

.PARAMETER Port
  Port HTTP du backend (8081 par defaut, celui attendu par le front en dev).

.PARAMETER FrontendUrl
  URL du front, utilisee dans les liens envoyes par e-mail.

.EXAMPLE
  .\scripts\run-local.ps1
  .\scripts\run-local.ps1 -Build
#>
param(
    [switch]$Build,
    [int]$Port = 8081,
    [string]$FrontendUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- Java 17 (Temurin) ---------------------------------------------------------
if (-not $env:JAVA_HOME -or -not (Test-Path "$($env:JAVA_HOME)\bin\java.exe")) {
    $jdk = Get-ChildItem "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "jdk-?17" } | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) { throw "JDK 17 introuvable : definissez JAVA_HOME (Java 25 est incompatible avec Lombok)." }
    $env:JAVA_HOME = $jdk.FullName
}
$trust = @("-Djavax.net.ssl.trustStoreType=Windows-ROOT", "-Djavax.net.ssl.trustStore=NUL")

# --- Compilation (optionnelle) --------------------------------------------------
if ($Build -or -not (Test-Path "target\data-mastery-hub-0.0.1-SNAPSHOT.jar")) {
    Write-Host ">> Compilation du JAR..." -ForegroundColor Cyan
    $env:MAVEN_OPTS = ($trust -join " ")
    & .\mvnw.cmd -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "La compilation a echoue." }
}

# --- Variables d'environnement --------------------------------------------------
if (-not (Test-Path ".env.dev")) { throw ".env.dev introuvable (copiez .env.example puis renseignez-le)." }
Get-Content ".env.dev" | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $i = $line.IndexOf("=")
    if ($i -lt 1) { return }
    [Environment]::SetEnvironmentVariable($line.Substring(0, $i).Trim(), $line.Substring($i + 1), "Process")
}
$env:DB_URL            = "jdbc:postgresql://localhost:5432/$($env:DB_NAME)"
$env:MINIO_ENDPOINT    = "http://localhost:9000"
$env:SERVER_PORT       = "$Port"
$env:APP_FRONTEND_URL  = $FrontendUrl

Write-Host ">> Backend sur http://localhost:$Port (front attendu sur $FrontendUrl) - Ctrl+C pour arreter" -ForegroundColor Green
& "$($env:JAVA_HOME)\bin\java.exe" "-XX:MaxRAMPercentage=60" $trust[0] $trust[1] -jar "target\data-mastery-hub-0.0.1-SNAPSHOT.jar"
