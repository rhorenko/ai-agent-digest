[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot 'docker\compose.yaml'
$composeArguments = @('--file', $composeFile)
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    $composeArguments += @('--env-file', $envFile)
}

Push-Location $projectRoot
try {
    & docker compose @composeArguments run --rm app @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
