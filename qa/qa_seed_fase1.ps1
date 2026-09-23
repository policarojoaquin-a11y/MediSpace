<#
MediSpace QA — Fase 1: alta de datos de referencia QA vía la API real (no INSERT directo),
para que se ejerciten las mismas reglas de negocio que usaria un usuario real.
Requiere la app corriendo en http://localhost:8080 (mvn spring-boot:run).
Usa las credenciales REALES existentes (Gerente/Administrativo) solo para autenticarse — los
datos que crea están todos identificados con prefijo/rango QA (ver docs del protocolo).
Guarda los IDs creados en qa/qa_seed_ids.json para que qa_cleanup.sql y las fases siguientes
los puedan referenciar sin tener que re-consultar por nombre.
#>

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$qaDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $qaDir "load-env.ps1")

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body = $null
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Method  = $Method
        Uri     = "$base$Path"
        Headers = $headers
        ContentType = "application/json; charset=utf-8"
    }
    if ($Body -ne $null) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 10)
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $errBody = $reader.ReadToEnd()
            Write-Host "API ERROR [$Method $Path]: $($resp.StatusCode.value__) $errBody" -ForegroundColor Red
        }
        throw
    }
}

Write-Host "=== Login GERENTE / ADMINISTRATIVO (credenciales reales existentes, desde .env.qa) ===" -ForegroundColor Cyan
$gerenteLogin = Invoke-Api -Method POST -Path "/auth/login" -Body @{ email = $env:MEDISPACE_GERENTE_EMAIL; password = $env:MEDISPACE_GERENTE_PASSWORD }
$adminLogin   = Invoke-Api -Method POST -Path "/auth/login" -Body @{ email = $env:MEDISPACE_ADMIN_EMAIL; password = $env:MEDISPACE_ADMIN_PASSWORD }
$tokGerente = $gerenteLogin.token
$tokAdmin   = $adminLogin.token
Write-Host "OK - tokens obtenidos"

$ids = [ordered]@{}

Write-Host "=== Especialidades QA ===" -ForegroundColor Cyan
# Nombres con tildes construidos via [char] (codepoints Unicode) en vez de literales acentuados:
# Windows PowerShell 5.1 puede leer el .ps1 con el codepage ANSI del sistema si el archivo no
# tiene BOM, lo que corrompe (mojibake) cualquier caracter no-ASCII escrito literalmente.
$iAcute = [char]0x00ED  # í
$especialidades = @(
    "QA_Cardiolog${iAcute}a",
    "QA_Pediatr${iAcute}a",
    "QA_Cl${iAcute}nica"
)
$ids.especialidades = @{}
foreach ($nombre in $especialidades) {
    $r = Invoke-Api -Method POST -Path "/especialidades" -Token $tokGerente -Body @{ nombre = $nombre }
    $ids.especialidades[$nombre] = $r.idEspecialidad
    Write-Host "  $nombre -> id $($r.idEspecialidad)"
}

Write-Host "=== Obras Sociales QA ===" -ForegroundColor Cyan
$ids.obrasSociales = @{}
$osOsde = Invoke-Api -Method POST -Path "/obras-sociales" -Token $tokGerente -Body @{ nombre = "QA_OSDE"; codigoSigla = "QAOSDE"; requiereBono = $false; observaciones = "QA - sin bono" }
$ids.obrasSociales["QA_OSDE"] = $osOsde.idObraSocial
$osIoma = Invoke-Api -Method POST -Path "/obras-sociales" -Token $tokGerente -Body @{ nombre = "QA_IOMA"; codigoSigla = "QAIOMA"; requiereBono = $true; observaciones = "QA - con bono" }
$ids.obrasSociales["QA_IOMA"] = $osIoma.idObraSocial
$osPami = Invoke-Api -Method POST -Path "/obras-sociales" -Token $tokGerente -Body @{ nombre = "QA_PAMI"; codigoSigla = "QAPAMI"; requiereBono = $false; observaciones = "QA" }
$ids.obrasSociales["QA_PAMI"] = $osPami.idObraSocial
Write-Host "  QA_OSDE -> id $($osOsde.idObraSocial) | QA_IOMA -> id $($osIoma.idObraSocial) | QA_PAMI -> id $($osPami.idObraSocial)"

Write-Host "=== Consultorios QA ===" -ForegroundColor Cyan
$ids.consultorios = @{}
$c1 = Invoke-Api -Method POST -Path "/consultorios" -Token $tokGerente -Body @{ numeroConsultorio = "QA-1"; descripcion = "Consultorio QA 1"; equipamiento = "QA"; ubicacion = "QA" }
$ids.consultorios["QA-1"] = $c1.idConsultorio
$c2 = Invoke-Api -Method POST -Path "/consultorios" -Token $tokGerente -Body @{ numeroConsultorio = "QA-2"; descripcion = "Consultorio QA 2"; equipamiento = "QA"; ubicacion = "QA" }
$ids.consultorios["QA-2"] = $c2.idConsultorio
$c3 = Invoke-Api -Method POST -Path "/consultorios" -Token $tokGerente -Body @{ numeroConsultorio = "QA-3"; descripcion = "Consultorio QA 3"; equipamiento = "QA"; ubicacion = "QA" }
$ids.consultorios["QA-3"] = $c3.idConsultorio
Write-Host "  QA-1 -> id $($c1.idConsultorio) | QA-2 -> id $($c2.idConsultorio) | QA-3 -> id $($c3.idConsultorio)"
# QA-3 pasa a "En mantenimiento" (el DTO espera el codigo de enum, no el texto de display)
$c3upd = Invoke-Api -Method PUT -Path "/consultorios/$($c3.idConsultorio)/estado" -Token $tokGerente -Body @{ estado = "EN_MANTENIMIENTO" }
Write-Host "  QA-3 estado -> $($c3upd.estado)"

Write-Host "=== Médicos QA (alta vía flujo real del Gerente) ===" -ForegroundColor Cyan
$ids.medicos = @{}
$drCardio = Invoke-Api -Method POST -Path "/medicos" -Token $tokGerente -Body @{
    nombre = "QA_Dr"; apellido = "Cardio"; matricula = "QA-1001";
    idEspecialidad = $ids.especialidades["QA_Cardiología"];
    importeConsulta = 20000; fechaInicioActividad = (Get-Date).ToString("yyyy-MM-dd");
    email = "qa.dr.cardio@qa.test"
}
$ids.medicos["QA_Dr_Cardio"] = $drCardio.idMedico
Write-Host "  QA_Dr_Cardio -> id $($drCardio.idMedico) (usuario: qa.dr.cardio@qa.test / password = su matricula -- ver .env.qa)"

$draPedia = Invoke-Api -Method POST -Path "/medicos" -Token $tokGerente -Body @{
    nombre = "QA_Dra"; apellido = "Pedia"; matricula = "QA-1002";
    idEspecialidad = $ids.especialidades["QA_Pediatría"];
    importeConsulta = 15000; fechaInicioActividad = (Get-Date).ToString("yyyy-MM-dd");
    email = "qa.dra.pedia@qa.test"
}
$ids.medicos["QA_Dra_Pedia"] = $draPedia.idMedico
Write-Host "  QA_Dra_Pedia -> id $($draPedia.idMedico) (usuario: qa.dra.pedia@qa.test / password = su matricula -- ver .env.qa)"

$drClinico = Invoke-Api -Method POST -Path "/medicos" -Token $tokGerente -Body @{
    nombre = "QA_Dr"; apellido = "Clinico"; matricula = "QA-1003";
    idEspecialidad = $ids.especialidades["QA_Clínica"];
    importeConsulta = 12000; fechaInicioActividad = (Get-Date).ToString("yyyy-MM-dd");
    email = "qa.dr.clinico@qa.test"
}
$ids.medicos["QA_Dr_Clinico"] = $drClinico.idMedico
Write-Host "  QA_Dr_Clinico -> id $($drClinico.idMedico) (usuario: qa.dr.clinico@qa.test / password = su matricula -- ver .env.qa; sin contrato todavia)"

Write-Host "=== Cartilla de obras sociales de los médicos QA ===" -ForegroundColor Cyan
$mosCardio = Invoke-Api -Method POST -Path "/medicos/$($ids.medicos['QA_Dr_Cardio'])/obras-sociales" -Token $tokGerente -Body @{
    idObraSocial = $ids.obrasSociales["QA_OSDE"]; importeCoseguro = 5000
}
Write-Host "  QA_Dr_Cardio + QA_OSDE (coseguro 5000) -> id $($mosCardio.idMedicoObraSocial)"
$mosPedia = Invoke-Api -Method POST -Path "/medicos/$($ids.medicos['QA_Dra_Pedia'])/obras-sociales" -Token $tokGerente -Body @{
    idObraSocial = $ids.obrasSociales["QA_IOMA"]; importeCoseguro = 0
}
Write-Host "  QA_Dra_Pedia + QA_IOMA -> id $($mosPedia.idMedicoObraSocial)"

Write-Host "=== Usuarios QA (Gerente / Administrativo adicionales) ===" -ForegroundColor Cyan
$ids.usuarios = @{}
$uGerente = Invoke-Api -Method POST -Path "/usuarios" -Token $tokGerente -Body @{ email = "qa.gerente@qa.test"; password = $env:QA_USERS_PASSWORD; rol = "GERENTE" }
$ids.usuarios["qa.gerente@qa.test"] = $uGerente.idUsuario
$uAdmin1 = Invoke-Api -Method POST -Path "/usuarios" -Token $tokGerente -Body @{ email = "qa.admin1@qa.test"; password = $env:QA_USERS_PASSWORD; rol = "ADMINISTRATIVO" }
$ids.usuarios["qa.admin1@qa.test"] = $uAdmin1.idUsuario
$uAdmin2 = Invoke-Api -Method POST -Path "/usuarios" -Token $tokGerente -Body @{ email = "qa.admin2@qa.test"; password = $env:QA_USERS_PASSWORD; rol = "ADMINISTRATIVO" }
$ids.usuarios["qa.admin2@qa.test"] = $uAdmin2.idUsuario
Write-Host "  qa.gerente@qa.test -> id $($uGerente.idUsuario)"
Write-Host "  qa.admin1@qa.test -> id $($uAdmin1.idUsuario)"
Write-Host "  qa.admin2@qa.test -> id $($uAdmin2.idUsuario)"

Write-Host "=== Pacientes QA (vía Administrativo) ===" -ForegroundColor Cyan
$ids.pacientes = @{}
$pacientesDef = @(
    @{ key = "QA_P1"; nombre = "QA_P1"; apellido = "Particular"; dni = "99000001"; idObraSocial = $null;                              nota = "particular" }
    @{ key = "QA_P2"; nombre = "QA_P2"; apellido = "Osde";       dni = "99000002"; idObraSocial = $ids.obrasSociales["QA_OSDE"];       nota = "QA_OSDE" }
    @{ key = "QA_P3"; nombre = "QA_P3"; apellido = "Ioma";       dni = "99000003"; idObraSocial = $ids.obrasSociales["QA_IOMA"];       nota = "QA_IOMA" }
    @{ key = "QA_P4"; nombre = "QA_P4"; apellido = "NoAsistio";  dni = "99000004"; idObraSocial = $null;                              nota = "para No Asistio" }
    @{ key = "QA_P5"; nombre = "QA_P5"; apellido = "Cancela";    dni = "99000005"; idObraSocial = $null;                              nota = "para cancelaciones" }
    @{ key = "QA_P6"; nombre = "QA_P6"; apellido = "BajaReact";  dni = "99000006"; idObraSocial = $null;                              nota = "para baja/reactivacion" }
)
foreach ($p in $pacientesDef) {
    $body = @{
        nombre = $p.nombre; apellido = $p.apellido; dni = $p.dni; telefono = "QA-0000000";
        idObraSocial = $p.idObraSocial; numeroCredencial = $(if ($p.idObraSocial) { "QA-CRED-$($p.dni)" } else { $null });
        direccion = "QA"; planOs = $(if ($p.idObraSocial) { "QA-Plan" } else { $null });
        fechaNacimiento = "1990-01-01"
    }
    $r = Invoke-Api -Method POST -Path "/pacientes" -Token $tokAdmin -Body $body
    $ids.pacientes[$p.key] = $r.idPaciente
    Write-Host "  $($p.key) ($($p.nota)) -> id $($r.idPaciente)"
}

$ids | ConvertTo-Json -Depth 10 | Out-File -FilePath "$qaDir\qa_seed_ids.json" -Encoding utf8
Write-Host ""
Write-Host "=== Fase 1 completa. IDs guardados en qa/qa_seed_ids.json ===" -ForegroundColor Green
