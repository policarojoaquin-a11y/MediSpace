<#
Carga .env.qa (raiz del repo) como variables de entorno de la sesion actual. Los scripts/
snippets de qa/ lo dot-sourcean (". qa\load-env.ps1") en vez de hardcodear credenciales.
#>
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".env.qa"
if (-not (Test-Path $envFile)) {
    throw "No se encontro .env.qa en la raiz del repo. Copialo desde qa/.env.qa.example y completa las credenciales."
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $idx = $line.IndexOf("=")
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        Set-Item -Path "env:$key" -Value $value
    }
}
