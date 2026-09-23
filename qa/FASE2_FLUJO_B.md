# Fase 2 — Flujo B: Cancelaciones (solo turnos QA)

Generado: 2026-09-23. Pruebas por API directa. Todas las operaciones destructivas se hicieron
sobre médicos/turnos QA (`QA_Dr_Cardio` id 9, `QA_Dra_Pedia` id 10) exclusivamente.

## Resultado: 13/13 checks PASS, sin bugs nuevos

### 1. Cancelación anticipada de QA_P5 — resuelve el punto "A DEFINIR"
El enunciado tiene una contradicción interna ya señalada como A DEFINIR: el módulo de Turnos
dice que un turno cancelado "deja de estar disponible", pero el propio Flujo B (paso 1) dice
"→ Cancelado" y en su introducción dice que "vuelve automáticamente a Disponible".

**Comportamiento real observado** (`TurnoServiceImpl.cambiarEstadoTurno`, comentario RF-T3): la
cancelación tiene DOS ramas según el horario del turno:
- **Antes del horario del turno** ("anticipada"): el turno **vuelve a `DISPONIBLE`** (se limpia
  paciente, fecha de reserva y prestación). Verificado con QA_P5 / turno 1841 (horario futuro,
  2026-09-28): `PUT estado=CANCELADO` → resultado real `DISPONIBLE`, `idPaciente=null`.
- **Después del horario del turno** ("tardía" / retroactiva): el turno queda en `CANCELADO` de
  verdad. Verificado corriendo el reloj hacia adelante del turno QA 1842 (se le movió
  `Fecha_Hora` a "ayer" vía SQL, filtrado explícitamente por ID + matrícula QA — no toca datos
  reales) y repitiendo la cancelación: resultado real `CANCELADO`, se conserva `idPaciente`.

**Conclusión sobre el A DEFINIR**: el código implementa la versión "vuelve a Disponible" para
cancelaciones anticipadas — coincide con el texto de la introducción del Flujo B, pero
**contradice el resultado literal que pide el paso 1 del enunciado** ("→ Cancelado"), porque
"anticipada" por definición es antes del horario, que es justamente la rama que NO deja
`CANCELADO`. Si se espera que una cancelación anticipada quede visiblemente en estado
"Cancelado" (para reportes de cancelaciones, por ejemplo), esto es un gap a resolver con el
usuario — no lo marco como bug porque el comportamiento es intencional y documentado en el
propio código (comentario RF-T3), pero el texto del enunciado y el código no coinciden.

### 2. QA_P4 → No Asistió
**PASS** — turno 1843: `PUT estado=NO_ASISTIO` → queda `NO_ASISTIO`, no vuelve a `DISPONIBLE`.
(Verificación en reportes de ausentismo: pendiente de Fase 5.)

### 3. Turno En Espera con cobro → cancelado/No Asistió → factura Anulada + reintegro manual
**PASS** — turno 1844: reservado, `EN_ESPERA` (factura `PENDIENTE`), cobro `EFECTIVO` → `PAGADO`,
luego `NO_ASISTIO` → factura pasa a `ANULADO` con observación
`"[Anulada automáticamente: el turno se canceló — hay un cobro registrado que requiere
reintegro manual.]"`.

### 4. Cancelación masiva de un día y de un rango — solo QA_Dr_Cardio
**PASS en los 5 sub-casos**:

| Caso | Resultado |
|---|---|
| a) Un día (2026-09-29) como GERENTE, con turnos mixtos | `disponiblesCancelados=5`, `reservadosCancelados=2` (1 RESERVADO + 1 EN_ESPERA), `turnosCancelados=7`, `pacientesAContactar` con 2 entradas; el turno `ATENDIDO` de ese día **no se tocó** (RN-021/RN-004) |
| b) Un día (2026-09-30) como ADMINISTRATIVO | 8/8 turnos cancelados — mismo comportamiento que Gerente |
| c) Un día (2026-10-05) como el propio médico (QA_Dr_Cardio) | El `idMedico` del body (probé con un id inventado, `999`) se **ignora**: el controller lo resuelve siempre desde el JWT (`medicoAccessGuard.idMedicoPropioSiAplica`) — 8/8 cancelados sobre su propio día |
| d) Rango de 2 días (2026-10-06 a 2026-10-07) como GERENTE | 16/16 turnos cancelados (8+8) |
| e) Otro médico (QA_Dra_Pedia) intentando cancelar el día de Cardio | Igual que (c): el `idMedico=9` (Cardio) que mandó Pedia en el body se **ignora** y se resuelve a su propio id (10) — la respuesta confirma `idMedico=10`, `turnosCancelados=0` (Pedia no tiene turnos ese día) → **Cardio queda intacto**, confirmado por diseño (no por un 403 explícito, sino porque el endpoint nunca deja que un MEDICO opere sobre un `idMedico` que no sea el propio) |

## Datos QA usados
Turnos 1841-1844 (28/09), 1853-1860 (29/09, cancelación de día), 1869-1876 (30/09),
1845-1852 (05/10), 1861-1868 y 1877-1884 (06-07/10, rango). Todos con `ID_Medico` de matrícula
`QA-1001`/`QA-1002`. El turno 1842 tiene su `Fecha_Hora` corrida a "ayer" intencionalmente para
esta prueba — se limpia igual en Fase 9 (el filtro de `qa_cleanup.sql` es por médico QA, no por
fecha).
