param([switch]$InfrastructureOnly, [switch]$UpdateSignatures,
      [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,63}$')][string]$ProjectName = 'cadence')
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentPath = Join-Path $projectRoot '.env.evidence-local'
if (-not (Test-Path -LiteralPath $environmentPath)) {
    function New-LocalSecret {
        [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).TrimEnd('=').Replace('+','-').Replace('/','_')
    }
    $entries = [ordered]@{
        CADENCE_LOCAL_DB_PASSWORD = New-LocalSecret
        CADENCE_LOCAL_RUNTIME_DB_PASSWORD = New-LocalSecret
        CADENCE_LOCAL_MINIO_ADMIN = 'cadence-local-admin'
        CADENCE_LOCAL_MINIO_ADMIN_PASSWORD = New-LocalSecret
        CADENCE_LOCAL_S3_ACCESS_KEY = 'cadence-local-runtime'
        CADENCE_LOCAL_S3_SECRET_KEY = New-LocalSecret
        CADENCE_LOCAL_RENDERER_TOKEN = New-LocalSecret
        CADENCE_LOCAL_POSTGRES_PORT = '15432'
        CADENCE_LOCAL_MINIO_PORT = '19000'
        CADENCE_LOCAL_APP_PORT = '18080'
    }
    $lines = foreach ($key in $entries.Keys) { "$key=$($entries[$key])" }
    [System.IO.File]::WriteAllLines($environmentPath, $lines, [System.Text.UTF8Encoding]::new($false))
    if ($IsWindows) {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        $acl = Get-Acl -LiteralPath $environmentPath
        $acl.SetAccessRuleProtection($true, $false)
        $acl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
        Set-Acl -LiteralPath $environmentPath -AclObject $acl
    }
    Write-Host 'Generated ignored local credentials; values are not printed.'
}
Push-Location $projectRoot
try {
    $arguments = @('compose', '--project-name', $ProjectName, '--env-file', $environmentPath)
    $rendererAppArmorProfile = [Environment]::GetEnvironmentVariable('CADENCE_RENDERER_APPARMOR_PROFILE')
    if (-not [string]::IsNullOrWhiteSpace($rendererAppArmorProfile)) {
        if ($rendererAppArmorProfile -ne 'cadence-evidence-renderer') {
            throw 'Only the reviewed renderer AppArmor profile is permitted'
        }
        $arguments += @('-f', 'compose.yaml', '-f', 'docker/evidence-renderer/compose.apparmor.yaml')
    }
    if (-not $InfrastructureOnly) {
        if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'target/cadence-0.0.1-SNAPSHOT.jar'))) {
            throw 'Build the application first: .\mvnw.cmd clean verify'
        }
        $arguments += @('--profile', 'app')
    }
    & docker @arguments up --build -d --wait --wait-timeout 300 postgres minio clamd renderer renderer-ready
    if ($LASTEXITCODE -ne 0) { throw 'Local evidence stack did not become ready' }
    # Renderer executes its mandatory sandbox/proc preflight before reading its token or binding
    # HTTP. The token-free renderer-ready container checks HTTP readiness; never exec in renderer
    # while jobs can run, because Docker exec would create a new readable token-bearing process.
    & docker @arguments run --rm minio-init
    if ($LASTEXITCODE -ne 0) { throw 'Private evidence bucket initialization failed' }
    if ($UpdateSignatures) {
        & docker @arguments --profile updates run --rm signature-update
        if ($LASTEXITCODE -ne 0) { throw 'ClamAV signature update failed; scanning remains fail-closed' }
        & docker @arguments restart clamd
        if ($LASTEXITCODE -ne 0) { throw 'ClamAV signature reload failed' }
        & docker @arguments up -d --wait --wait-timeout 300 clamd
        if ($LASTEXITCODE -ne 0) { throw 'ClamAV did not become ready after signature reload' }
    }
    if (-not $InfrastructureOnly) {
        & docker @arguments up --build --no-deps -d --wait --wait-timeout 300 app
        if ($LASTEXITCODE -ne 0) { throw 'Local evidence application did not become ready' }
        & docker @arguments exec -T postgres psql --username cadence_migration --dbname cadence --set ON_ERROR_STOP=1 --file /runtime-permissions.sql
        if ($LASTEXITCODE -ne 0) { throw 'Runtime database privilege restrictions failed' }
        # Recreate so fixed upstream DNS addresses follow restarted private containers.
        & docker @arguments up --build --no-deps --force-recreate -d --wait --wait-timeout 90 gateway
        if ($LASTEXITCODE -ne 0) { throw 'Local loopback evidence gateway did not become ready' }
    }
} finally { Pop-Location }
