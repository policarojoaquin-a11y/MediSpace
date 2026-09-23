# Lista de verificación — fixes del 2026-09-23

Para cada uno: **qué pasaba antes**, **qué pasa ahora**, y cómo probarlo vos mismo (UI y/o API).
La app ya está corriendo con el código nuevo. Los datos QA (`QA_Dr_Cardio`, `QA-1`, `QA-3`, etc.)
siguen en la base — **todavía no corrí la limpieza (Fase 9)** para que puedas verificar con los
mismos datos que encontraron los bugs.

**Credenciales**: ningún valor real en este archivo — copiá `qa/.env.qa.example` a `.env.qa` en
la raíz del repo, completalo, y cada snippet de PowerShell de abajo arranca con
`. .\qa\load-env.ps1` para cargarlas como variables de entorno (`$env:MEDISPACE_GERENTE_EMAIL`,
etc.). `.env.qa` está en `.gitignore` (vía `.env.*`) — nunca se commitea.

---

## 1. RN-022 — Ya no se puede contratar un consultorio no disponible

**Antes**: se podía crear un contrato de arrendamiento sobre un consultorio "En mantenimiento" y
generaba turnos reservables ahí.
**Ahora**: rechaza con `RN-022`.

### Por UI
1. Entrá como Gerente.
2. Módulo **Consultorios** → confirmá que `QA-3` está en estado "En mantenimiento".
3. Módulo **Arrendamientos** → "Nuevo contrato" → médico `QA_Dr_Cardio`, consultorio `QA-3`,
   cualquier día/horario válido (≥4hs) → Guardar.
4. **Esperado**: error visible `RN-022: El consultorio no está Disponible...`. El contrato NO se
   crea.

### Por API (PowerShell)
```powershell
. .\qa\load-env.ps1
$tok = (Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{email=$env:MEDISPACE_GERENTE_EMAIL;password=$env:MEDISPACE_GERENTE_PASSWORD}|ConvertTo-Json)).token
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/arrendamientos" -Headers @{Authorization="Bearer $tok"} -ContentType "application/json" -Body '{"idMedico":9,"idConsultorio":10,"fechaInicio":"2026-11-02","fechaFin":"2026-11-30","diaSemana":"LUNES","horaInicio":"09:00:00","horaFin":"13:00:00","duracionTurnoMin":30,"cupoMaximoDiario":8,"porcentajeConsultorio":30,"porcentajeMedico":70}'
```
**Esperado**: error 400 con `"message":"RN-022: ..."` (no un contrato creado).

---

## 2. RN-023 — Un médico ya no puede tener 2 contratos superpuestos en consultorios distintos

**Antes**: un médico podía terminar con un segundo contrato "exitoso" (201, Estado ACTIVO) que
generaba 0 turnos reales, sin ningún aviso.
**Ahora**: rechaza con `RN-023` al intentar crear el segundo contrato.

### Por API (PowerShell)
`QA_Dr_Cardio` ya tiene un contrato Lunes 9-13 en `QA-1` (2026-09-28 a 2026-10-11). Probá crear
otro contrato para él, mismo día/horario, en `QA-2` (otro consultorio), **con fechas que se
superpongan**:
```powershell
. .\qa\load-env.ps1
$tok = (Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{email=$env:MEDISPACE_GERENTE_EMAIL;password=$env:MEDISPACE_GERENTE_PASSWORD}|ConvertTo-Json)).token
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/arrendamientos" -Headers @{Authorization="Bearer $tok"} -ContentType "application/json" -Body '{"idMedico":9,"idConsultorio":9,"fechaInicio":"2026-09-28","fechaFin":"2026-10-11","diaSemana":"LUNES","horaInicio":"09:00:00","horaFin":"13:00:00","duracionTurnoMin":30,"cupoMaximoDiario":8,"porcentajeConsultorio":30,"porcentajeMedico":70}'
```
**Esperado**: error 400 con `"message":"RN-023: ..."`.

**Importante**: las fechas tienen que superponerse con el contrato existente (2026-09-28 a
2026-10-11) para que dispare. Si usás fechas de otro mes que no se solapan, es correcto que lo
permita (no hay conflicto real de horario).

---

## 3. BUG-003 — Liquidación/Cierre/Reportes ya no cuentan facturas anuladas

**Antes**: sumaban `Importe_Total` de TODAS las facturas del período, incluidas las `ANULADO`.
**Ahora**: las excluyen (mismo criterio que ya usaba el Dashboard Gerencial).

### Por UI
1. Entrá como Gerente → **Reportes** → Facturación por médico, filtrá por hoy.
2. Buscá la fila de `QA_Dr_Cardio`. El `totalFacturado` tiene que dar **$100.000** (5 facturas
   pagadas × $20.000), no $140.000 (que incluiría 2 facturas anuladas de más).

### Por API
```powershell
. .\qa\load-env.ps1
$tok = (Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{email=$env:MEDISPACE_GERENTE_EMAIL;password=$env:MEDISPACE_GERENTE_PASSWORD}|ConvertTo-Json)).token
$hoy = (Get-Date).ToString("yyyy-MM-dd")
Invoke-RestMethod -Method GET -Uri "http://localhost:8080/api/reportes/facturacion/medico?desde=$hoy&hasta=$hoy" -Headers @{Authorization="Bearer $tok"} | Where-Object { $_.idMedico -eq 9 }
```
**Esperado**: `totalFacturado: 100000` (antes del fix daba `140000`).

Lo mismo aplica a **Liquidaciones** (`POST /api/liquidaciones/generar` para `QA_Dr_Cardio`, hoy)
y a **Cierre Diario** (`POST /api/arrendamientos/cierre-diario`) — ambos deberían dar
`totalFacturado`/`totalFacturadoDia` = 100000, no 140000.

---

## 4. RN-024 — Ya no se puede liquidar el mismo período dos veces

**Antes**: se podía generar una liquidación diaria y otra semanal que la incluyera, ambas
`EMITIDA`, duplicando lo que se le pagaría al médico.
**Ahora**: rechaza con `RN-024` si el período se superpone con una liquidación `EMITIDA` existente
del mismo médico.

### Por UI
1. Entrá como Gerente → **Liquidaciones** → confirmá que ya existe una liquidación `EMITIDA` de
   `QA_Dr_Cardio` para hoy (quedó de la sesión anterior).
2. "Nueva liquidación" → mismo médico, un rango que incluya hoy (ej. últimos 7 días) → Generar.
3. **Esperado**: error `RN-024: Ya existe una liquidación EMITIDA...`.

### Por API
```powershell
. .\qa\load-env.ps1
$tok = (Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{email=$env:MEDISPACE_GERENTE_EMAIL;password=$env:MEDISPACE_GERENTE_PASSWORD}|ConvertTo-Json)).token
$hoy = (Get-Date).ToString("yyyy-MM-dd"); $ayer = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd")
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/liquidaciones/generar" -Headers @{Authorization="Bearer $tok"} -ContentType "application/json" -Body "{`"idMedico`":9,`"fechaDesde`":`"$ayer`",`"fechaHasta`":`"$hoy`"}"
```
**Esperado**: error 400 con `"message":"RN-024: Ya existe una liquidación EMITIDA..."`.

---

## 5. RN-010 — GERENTE ya no puede editar/anular evoluciones clínicas ajenas

**Antes**: `PUT /api/historias-clinicas/evoluciones/{id}` y
`DELETE /api/historias-clinicas/evoluciones/{id}/anular` permitían GERENTE (contra la tabla de
permisos de la propuesta original, que dice "ningún otro rol" además del médico tratante).
**Ahora**: solo MEDICO.

### Por API
```powershell
. .\qa\load-env.ps1
$tok = (Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" -ContentType "application/json" -Body (@{email=$env:MEDISPACE_GERENTE_EMAIL;password=$env:MEDISPACE_GERENTE_PASSWORD}|ConvertTo-Json)).token
Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/historias-clinicas/evoluciones/6" -Headers @{Authorization="Bearer $tok"} -ContentType "application/json" -Body '{"motivoConsulta":"x","diagnostico":"x","tratamiento":"x","indicaciones":"x","estudiosSolicitados":"x","observaciones":"x"}'
```
**Esperado**: error 403 (antes: 200, permitía editar).

También verificado en vivo (2026-09-23) que un médico QA que **sí** está vinculado al mismo
paciente (por tener otro turno propio con él) sigue sin poder editar/anular una evolución
**de otro médico** sobre ese paciente — sigue dando `RN-010`, como corresponde.

---

## Regresión general
```
mvn test
```
**Esperado**: `Tests run: 135, Failures: 0, Errors: 0` (125 originales + 10 nuevos de esta
sesión: 8 de los fixes de arrendamiento/liquidación + 2 de la corrección RN-010, ver
`docs/ANEXO_CAMBIOS_POST_ENTREGA.md` §K.6/§K.7).

## Archivos tocados
- `src/main/java/com/medispace/app/service/impl/ArrendamientoServiceImpl.java` (RN-022, RN-023)
- `src/main/java/com/medispace/app/repository/ArrendamientoModuloRepository.java` (nuevo método `countSuperposicionesMedico`)
- `src/main/java/com/medispace/app/service/impl/LiquidacionServiceImpl.java` (RN-024, filtro BUG-003)
- `src/main/java/com/medispace/app/repository/LiquidacionMedicaRepository.java` (nuevo método `findSuperpuestas`)
- `src/main/java/com/medispace/app/service/impl/CierreDiarioServiceImpl.java` (filtro BUG-003)
- `src/main/java/com/medispace/app/service/impl/ReporteServiceImpl.java` (filtro BUG-003 en 3 métodos)
- `src/main/java/com/medispace/app/controller/HistoriaClinicaController.java` (RN-010: GERENTE ya no puede editar/anular evoluciones)
- `qa/qa_cleanup.sql` (bug de doble conteo en `@ExpectedUsuarios` corregido + modo dry-run)
- Tests nuevos/corregidos: `ArrendamientoServiceTest`, `LiquidacionServiceTest`, `CierreDiarioServiceTest`, `ReporteServiceTest`, `HistoriaClinicaControllerTest`
- Documentación: `docs/spec.md` (RN-022 a RN-024), `docs/ANEXO_CAMBIOS_POST_ENTREGA.md` (Parte K)

## Pendiente
La limpieza de datos QA (Fase 9) sigue sin correr — avisame cuando quieras que la ejecute (borra
todo lo `QA_*`/`@qa.test` de la base real, dentro de una transacción verificada, ya probada en
modo dry-run).
