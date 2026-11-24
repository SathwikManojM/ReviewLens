# ReviewLens 2.0 - starts the Python NLP engine, the Spring Boot backend and the React frontend in separate windows.
#   .\start-all.ps1
# On first run a .env file is created from .env.example with a random JWT_SECRET.
$root = $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path $envFile)) {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $secret = [Convert]::ToBase64String($bytes) -replace '[+/=]', 'x'
    (Get-Content (Join-Path $root '.env.example')) -replace '^JWT_SECRET=.*', "JWT_SECRET=$secret" | Set-Content $envFile -Encoding ascii
    Write-Host "Created .env with a random JWT_SECRET." -ForegroundColor Yellow
    Write-Host "Set ADMIN_EMAIL and ADMIN_PASSWORD (min. 12 chars) in .env to create the first administrator." -ForegroundColor Yellow
}

$cfg = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; $cfg[$k.Trim()] = $v.Trim() }
$uri = $cfg['MONGODB_URI']
if (-not $uri -or $uri -match 'localhost|127\.0\.0\.1') {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $ok = $tcp.ConnectAsync('localhost', 27017).Wait(1500)
    $tcp.Dispose()
    if (-not $ok) {
        Write-Host "MongoDB is not reachable on localhost:27017. Start the MongoDB service (services.msc > MongoDB) or set MONGODB_URI to MongoDB Atlas in .env." -ForegroundColor Red
    } else {
        Write-Host "MongoDB reachable on localhost:27017" -ForegroundColor Green
    }
}

# 1. Python NLP engine (port 8000)
$nlp = Join-Path $root 'nlp-engine'
if (-not (Test-Path "$nlp\.venv")) {
    python -m venv "$nlp\.venv"
    & "$nlp\.venv\Scripts\python.exe" -m pip install -r "$nlp\requirements.txt"
}
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$nlp'; .\.venv\Scripts\python.exe -m uvicorn app:app --port 8000"

# 2. Spring Boot backend (port 8080) - reads ..\.env automatically
$backend = Join-Path $root 'backend'
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$backend'; .\mvnw.cmd spring-boot:run"

# 3. React frontend (port 5173)
$frontend = Join-Path $root 'frontend'
if (-not (Test-Path "$frontend\node_modules")) { Push-Location $frontend; npm install; Pop-Location }
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$frontend'; npm run dev"

Write-Host "Starting... open http://localhost:5173 (workspace) or http://localhost:5173/admin/login (admin portal) once the backend shows 'Started ReviewlensApplication'."
