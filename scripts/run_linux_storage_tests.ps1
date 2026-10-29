$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$report = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'target/surefire-reports') -Filter 'TEST-*.xml' | Select-Object -First 1
if (-not $report) { throw 'Run the full Maven suite first' }
[xml]$document = Get-Content -LiteralPath $report.FullName -Raw
$classpath = ($document.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
if (-not $classpath) { throw 'The actual Surefire dependency classpath is missing' }
$relativeDirectory = 'var/linux-storage-' + [Guid]::NewGuid().ToString('N')
$outputDirectory = Join-Path $projectRoot $relativeDirectory
$libraryDirectory = Join-Path $outputDirectory 'libs'
New-Item -ItemType Directory -Path $libraryDirectory | Out-Null
$jars = $classpath.Split([System.IO.Path]::PathSeparator) | Where-Object { $_.EndsWith('.jar') }
if ($jars.Count -lt 5 -or $jars.Count -gt 500) { throw 'Unexpected Surefire dependency inventory' }
foreach ($jar in $jars) { Copy-Item -LiteralPath $jar -Destination $libraryDirectory }
# Surefire resolves the launcher as a provider dependency, outside java.class.path.
$engine = $jars | Where-Object { [System.IO.Path]::GetFileName($_) -match '^junit-platform-engine-(.+)\.jar$' } | Select-Object -First 1
if (-not $engine -or [System.IO.Path]::GetFileName($engine) -notmatch '^junit-platform-engine-(.+)\.jar$') { throw 'JUnit engine version is missing' }
$engineVersion = $Matches[1]
$platformDirectory = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($engine)))
$launcher = Join-Path $platformDirectory "junit-platform-launcher/$engineVersion/junit-platform-launcher-$engineVersion.jar"
if (-not (Test-Path -LiteralPath $launcher)) { throw 'Matching Surefire JUnit launcher is missing' }
Copy-Item -LiteralPath $launcher -Destination $libraryDirectory
& javac -cp (Join-Path $libraryDirectory '*') -d $outputDirectory (Join-Path $PSScriptRoot 'LinuxStorageTestLauncher.java')
if ($LASTEXITCODE -ne 0) { throw 'Linux filesystem launcher compilation failed' }
$containerDirectory = '/workspace/' + $relativeDirectory
& docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only `
    --user 10001:10001 --memory 384m --cpus 1 --pids-limit 64 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m `
    --mount "type=bind,source=$projectRoot,target=/workspace,readonly" `
    --entrypoint java eclipse-temurin:21-jre-jammy@sha256:e9aaf73145bbd1f9f6ec7f6867dd75a44f34b1a6c32a813504bf4129be2d09d7 `
    -cp "$containerDirectory`:$containerDirectory/libs/*:/workspace/target/classes:/workspace/target/test-classes" LinuxStorageTestLauncher
if ($LASTEXITCODE -ne 0) { throw 'Linux filesystem suite failed or skipped tests' }
