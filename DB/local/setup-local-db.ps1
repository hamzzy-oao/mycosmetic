# 로컬 MySQL(MySQL80)에 mycosmetic, mycosmetic_test DB와 mycosmetic 계정을 만든다.
# 실행: powershell -ExecutionPolicy Bypass -File DB\local\setup-local-db.ps1
# MySQL root 비밀번호를 물어본다. mycosmetic 계정 비밀번호는 .env의 MYSQL_PASSWORD를 쓴다.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path $envFile)) { throw ".env 파일이 없습니다: $envFile" }

$line = Get-Content $envFile -Encoding UTF8 | Where-Object { $_ -match '^MYSQL_PASSWORD=' } | Select-Object -First 1
$password = if ($line) { ($line -replace '^MYSQL_PASSWORD=', '').Trim() } else { '' }
if (-not $password) { throw '.env에 MYSQL_PASSWORD 값이 없습니다.' }

$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "mysql.exe를 찾을 수 없습니다: $mysql" }

$sql = (Get-Content (Join-Path $PSScriptRoot 'init-local.sql') -Raw -Encoding UTF8).Replace('__MYSQL_PASSWORD__', $password.Replace("'", "''"))
# SQL을 표준입력으로 넘기면 mysql -p가 비밀번호 대신 SQL 첫 줄을 읽어 버린다.
# 그래서 root 비밀번호는 여기서 받아 임시 설정 파일로 넘기고, 끝나면 바로 지운다.
$secure = Read-Host 'MySQL root 비밀번호를 입력하세요' -AsSecureString
$rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
$cnf = Join-Path $env:TEMP ("mycosmetic-root-" + [guid]::NewGuid() + ".cnf")
try {
    $bs = [string][char]92
    $escaped = $rootPassword.Replace($bs, $bs + $bs).Replace('"', $bs + '"')
    Set-Content -Path $cnf -Encoding ASCII -Value @('[client]', 'user=root', ('password="' + $escaped + '"'))
    $sql | & $mysql "--defaults-extra-file=$cnf" --default-character-set=utf8mb4
    $exitCode = $LASTEXITCODE
} finally {
    Remove-Item $cnf -Force -ErrorAction SilentlyContinue
}
if ($exitCode -ne 0) { throw 'DB 만들기에 실패했습니다. 위 ERROR 메시지를 확인하세요(1045면 root 비밀번호가 틀림).' }
Write-Host '완료: mycosmetic, mycosmetic_test DB와 mycosmetic 계정을 만들었습니다.'
