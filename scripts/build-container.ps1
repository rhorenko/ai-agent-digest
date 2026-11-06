[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot 'docker\compose.yaml'

Push-Location $projectRoot
try {
    $composeArgs = @('--file', $composeFile)
    $envFile = Join-Path $projectRoot '.env'
    if (Test-Path -LiteralPath $envFile) { $composeArgs += @('--env-file', $envFile) }
    & docker compose @composeArgs build @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
