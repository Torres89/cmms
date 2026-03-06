param(
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Load-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*$' -or $_ -match '^\s*#') { return }
        $parts = $_ -split '=', 2
        if ($parts.Length -eq 2) {
            [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
        }
    }
}

Write-Host "Loading environment files..."
Load-EnvFile "$PSScriptRoot\.env"
Load-EnvFile "$PSScriptRoot\api\.env.local"

if (-not (Test-Path "$PSScriptRoot\frontend\.env")) {
    Copy-Item "$PSScriptRoot\frontend\.env.example" "$PSScriptRoot\frontend\.env"
}

Write-Host "Starting Docker infrastructure (postgres + minio)..."
docker compose stop api frontend | Out-Null
docker compose up -d postgres minio | Out-Null

function Stop-ProcessOnPort {
    param([int]$Port)
    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $listeners) { return }
    $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($pid in $pids) {
        if ($pid -and $pid -ne $PID) {
            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Host "Stopped process $pid using port $Port"
            } catch {
                Write-Warning "Could not stop process $pid on port $Port"
            }
        }
    }
}

Stop-ProcessOnPort -Port 3000
Stop-ProcessOnPort -Port 8080

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Source) {
        $javaBin = Split-Path -Path $javaCmd.Source -Parent
        $candidate = Split-Path -Path $javaBin -Parent
        if (Test-Path "$candidate\bin\java.exe") {
            return $candidate
        }
    }

    $commonJdks = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java"
    )
    foreach ($base in $commonJdks) {
        if (-not (Test-Path $base)) { continue }
        $jdk = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($jdk) { return $jdk.FullName }
    }

    throw "JDK not found. Install Java 17+ or set JAVA_HOME."
}

$javaHome = Resolve-JavaHome

$backendCmd = @"
`$ErrorActionPreference = 'Stop'
Set-Location '$PSScriptRoot\api'
function Load-EnvFile {
    param([string]`$Path)
    if (-not (Test-Path `$Path)) { return }
    Get-Content `$Path | ForEach-Object {
        if (`$_ -match '^\s*$' -or `$_ -match '^\s*#') { return }
        `$parts = `$_ -split '=', 2
        if (`$parts.Length -eq 2) {
            [System.Environment]::SetEnvironmentVariable(`$parts[0], `$parts[1], 'Process')
        }
    }
}
Load-EnvFile '$PSScriptRoot\.env'
Load-EnvFile '$PSScriptRoot\api\.env.local'
`$env:JAVA_HOME = '$javaHome'
`$env:Path = `$env:JAVA_HOME + '\bin;' + `$env:Path
.\mvnw.cmd spring-boot:run
"@

$skipInstallLiteral = if ($SkipInstall.IsPresent) { '$true' } else { '$false' }

$frontendCmd = @"
`$ErrorActionPreference = 'Stop'
Set-Location '$PSScriptRoot\frontend'
`$skipInstall = $skipInstallLiteral
if (-not (Test-Path 'node_modules') -and -not `$skipInstall) {
    npm install --legacy-peer-deps
}
npm start
"@

Write-Host "Launching backend terminal..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-Command", $backendCmd
)

Write-Host "Launching frontend terminal..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-Command", $frontendCmd
)

Write-Host ""
Write-Host "Done."
Write-Host "Frontend: http://localhost:3000"
Write-Host "Backend:  http://localhost:8080"
Write-Host "Swagger:  http://localhost:8080/swagger-ui/index.html"
