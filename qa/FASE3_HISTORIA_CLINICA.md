# Fase 3 — Historia clínica y descargas

Generado: 2026-09-23. Pruebas por API directa sobre datos QA (`QA_P1`, evoluciones de
`QA_Dr_Cardio`).

## Resumen

| Ítem del enunciado | Resultado |
|---|---|
| Consulta por nombre | **PARCIAL** — ver nota |
| Consulta por DNI | **PARCIAL** — ver nota |
| Consulta por médico | **NO IMPLEMENTADA** |
| Consulta por fecha | **NO IMPLEMENTADA** |
| Descarga de la HC completa (PDF u otro formato) | **NO IMPLEMENTADA** |
| Descarga de adjuntos idéntica a lo subido | **PASS** — hash SHA-256 idéntico |
| RN-010 (acceso solo a pacientes vinculados) | **PASS** |
| RN-011 (anular con motivo obligatorio, sin motivo se rechaza) | **PASS** |
| Auditoría de accesos y modificaciones | **NO IMPLEMENTADA** |
| Permisos por rol | **PASS** |

## Búsqueda — PARCIAL (no es un endpoint de backend, es front-end sobre la lista completa)
`historias.js` (`buscarHistoriaClinica()`) no llama a ningún endpoint de búsqueda: trae **todos**
los pacientes (`Api.getPacientes()`) y hace `Array.find()` client-side sobre nombre+apellido o
DNI, devolviendo el **primer** match — no una lista de resultados. No hay filtro por médico ni
por fecha en ningún lado (ni `PacienteController.listarPacientes()` acepta query params, ni
existe un endpoint de historias por médico/fecha). Si dos pacientes calzan con el texto
buscado, el segundo es inalcanzable por este buscador.

## Descarga de HC completa — NO IMPLEMENTADA
No hay ningún endpoint que genere/exporte una historia clínica completa (PDF u otro formato).
`HistoriaClinicaController` solo expone lectura por paciente (JSON) y descarga de adjuntos
individuales. No hay ninguna librería de generación de PDF en `pom.xml` (iText/OpenPDF/etc.).

## Descarga de adjuntos — PASS
Subí un PNG a una evolución nueva (id 6, turno 1840), lo descargué vía
`GET /api/historias-clinicas/adjuntos/{id}/descargar` y comparé SHA-256 contra el archivo
original: **hash idéntico** (`431ced69...`).

Nota lateral (no bug): al anular una evolución (RN-011, soft-delete `Visible=0` vía
`@SQLDelete`), sus adjuntos dejan de ser descargables (`404 Adjunto no encontrado`) aunque el
archivo físico sigue en disco — el filtro de visibilidad de Hibernate se propaga. Es un
comportamiento razonable (no se puede bajar el adjunto de una evolución retractada) pero no está
mencionado explícitamente en la propuesta; lo marco como observación, no como bug.

## RN-010 — PASS
`QA_Dra_Pedia` (no vinculada a `QA_P1`, que solo tiene turnos con `QA_Dr_Cardio`) recibe
`400 RN-010: No tenés acceso a la historia clínica de un paciente que no atendés` tanto al leer
la HC como al intentar crear una evolución.

## RN-011 — PASS
- Anular sin motivo (`motivoAnulacion: null` y `motivoAnulacion: "   "`) → `400 RN-011: Se
  requiere un motivo obligatorio para anular la evolución.`
- Anular con motivo → `200`, la evolución queda con `Observaciones` original + traza
  `[ANULADA - Motivo: ...]`, soft-delete (no hay DELETE físico).

## Auditoría de accesos y modificaciones — NO IMPLEMENTADA
No existe ninguna tabla ni mecanismo de auditoría en todo el esquema (`docs/schema.sql` y el
esquema vivo verificado por `INFORMATION_SCHEMA.COLUMNS` en Fase 0 no tienen ninguna tabla tipo
`Auditoria`/`Log`/`AuditTrail`), ni ningún `AuditLog`/interceptor en el código Java. Ni los
accesos de lectura ni las modificaciones (crear/editar/anular evolución, agregar adjunto) quedan
registrados en ningún lado más allá de lo que ya guardan las propias tablas de negocio
(`Fecha_Creacion`, `Fecha_Carga`, etc., que no identifican **quién** hizo la acción salvo por la
FK a `ID_Medico`/`ID_Usuario` del propio registro).

## Permisos por rol — PASS
- **GERENTE**: `403` en `GET /historias-clinicas/paciente/{id}` — sin acceso, coincide con la
  sección 4.5 de la propuesta.
- **ADMINISTRATIVO**: ve la HC completa (200), puede adjuntar (201), **no puede** crear
  evolución (`403`, el endpoint es `hasRole('MEDICO')` exclusivo).
- **MEDICO**: solo accede a pacientes vinculados por al menos un turno propio (RN-010, ver
  arriba).
