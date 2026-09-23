# Fase 4 — Facturación, liquidaciones y cierre diario

Generado: 2026-09-23. Pruebas por API directa sobre `QA_Dr_Cardio` (id 9) / `QA-1` (id 8).
**Nota metodológica importante**: `Facturacion.Fecha_Facturacion` se graba como
`LocalDateTime.now()` en el momento del cobro/"En Espera", **no** la `Fecha_Hora` del turno (que
en esta suite es futura, 2026-09-28 en adelante). Como todas las pruebas se ejecutaron hoy
(2026-09-23), los filtros de fecha de liquidación/cierre usados en las pruebas son sobre HOY, no
sobre la fecha del turno — esto es correcto y coincide con el uso real (en producción, "En
Espera" ocurre el mismo día del turno).

## BUG-003 (Alta severidad) — Liquidación y Cierre Diario suman facturas ANULADAS como si fueran válidas
- **Archivos/líneas**:
  - `LiquidacionServiceImpl.generarLiquidacion`, línea 58-59:
    `facturas = facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(...)` — sin
    filtro de `estadoPago`.
  - `CierreDiarioServiceImpl.generarCierreDiario`, línea 50-51: exactamente la misma consulta,
    mismo problema.
- **Pasos para reproducir**: generar una factura y anularla (p. ej. turno que pasa a "No
  Asistió" después de estar "En Espera", ver `qa/FASE2_FLUJO_B.md` caso 3), dejar otras facturas
  `PAGADO` en el mismo período, y generar una liquidación o un cierre diario para ese médico en
  ese período.
- **Esperado**: el total liquidado/cerrado solo debería sumar facturas con cobro real (`PAGADO`,
  y quizás `PARCIAL`/`REINTEGRADO` si existieran) — **no** facturas `ANULADO`.
- **Obtenido**: se suman TODAS las facturas del período sin filtrar por estado.
- **Evidencia concreta**: `QA_Dr_Cardio`, 2026-09-23 — 5 facturas `PAGADO` × $20.000 = $100.000
  (correcto) + 2 facturas `ANULADO` × $20.000 = $40.000 (turnos 1844 y 1855, ambas anuladas por
  "No Asistió"/cancelación masiva) = **$140.000 obtenido** en vez de $100.000. Split
  médico/consultorio arrastra el mismo error: $98.000/$42.000 en vez de $70.000/$30.000.
  Liquidaciones id 7/8/9 y Cierre id 1, todas con `Total_Facturado=140000`.
- **La corrección ya existe en el propio código, pero no se propagó**: `ReporteServiceImpl.
  recalcularDashboard` (línea 61-71) filtra explícitamente `!ANULADO && !REINTEGRADO` antes de
  sumar `totalFacturado`, con un comentario que describe este bug casi palabra por palabra:
  *"cuando un turno EN_ESPERA... se cancela, anularFacturacionDeTurno() la deja en ANULADO pero
  conserva su Importe_Total — sumarla infla la 'Facturación del Día' con plata que nunca se
  facturó de verdad."* Ese mismo filtro **falta** en `LiquidacionServiceImpl`,
  `CierreDiarioServiceImpl`, y además en `ReporteServiceImpl.reporteFacturacionPorMedico`
  (línea 111, `total`), `reporteFacturacionPorObraSocial` (línea 141) y
  `reporteUsoConsultorios` (línea 173) — los 3 reportes de Fase 5 que se apoyan en
  `Facturacion.Importe_Total` sin filtrar por estado. Es decir: **5 de 6 lugares que agregan
  montos de Facturación tienen el bug; solo el Dashboard fue corregido.**

## BUG-004 (Alta severidad) — Ninguna liquidación es exclusiva: la misma facturación se puede liquidar dos veces
- **Archivo/línea**: mismo método (`LiquidacionServiceImpl.generarLiquidacion`) — no hay ningún
  campo ni consulta que excluya facturas ya incluidas en una liquidación `EMITIDA` previa, ni una
  restricción de unicidad (médico, período) en `Liquidacion_Medica`.
- **Pasos para reproducir**: generar una liquidación diaria para un médico, y luego generar una
  liquidación semanal/mensual que incluya ese mismo día.
- **Esperado**: si una liquidación diaria ya "pagó" al médico por el día X, una liquidación
  semanal que incluya el día X no debería volver a contar esas mismas facturas (o el sistema
  debería impedir/advertir la superposición).
- **Obtenido**: liquidación id 8 (diaria, 2026-09-23, `EMITIDA`, `Total_Facturado=140000`) y
  liquidación id 9 (semanal, 2026-09-17 a 2026-09-23, `EMITIDA`, `Total_Facturado=140000`) **ambas
  activas simultáneamente**, ambas reclamando exactamente el mismo importe sobre las mismas
  facturas. Un médico liquidado por ambas cobraría el doble por el mismo trabajo.
- Relacionado con BUG-003 pero es un defecto distinto (ausencia de exclusividad, no de filtrado
  por estado) — lo separo porque la corrección de uno no arregla el otro.

## RN-005 / RN-018 — PASS
`POST /liquidaciones/generar` con una factura `PENDIENTE` en el período → `400 RN-005: ... (1
factura(s) pendiente(s))` — el mensaje incluye la cantidad N, como pide el enunciado.
(RN-018 comparte el mismo código de guardia y solo se distingue de RN-005 en un caso de borde que
no es alcanzable por el flujo normal de la API — ver nota en el código, ambas reglas están
implementadas correctamente contra su escenario típico.)

## RN-007 — PASS
- Anular sin motivo → `400`. Anular con motivo → `200`, estado `ANULADA`. Anular una liquidación
  ya anulada → `400 RN-007: La liquidación ya se encuentra anulada.`
- No existe ningún endpoint `PUT`/`PATCH` para editar una liquidación `EMITIDA` directamente —
  "modificar una emitida → error" se cumple trivialmente (no hay ruta que lo permita).
- "Anular → regenerar → recálculo": confirmado — al generar de nuevo tras anular, se crea una
  liquidación **nueva** (id distinto), calculada de cero sobre los datos actuales (no es una copia
  de la anulada). El total coincidió porque los datos subyacentes no habían cambiado entre ambas
  llamadas.

## RN-006 — confirma el punto "A DEFINIR" de la propia consigna
El split 70/30 sale del contrato de arrendamiento vigente (`Arrendamiento_Modulo.Porcentaje_*`),
no de un campo en `Medicos` (que no existe). No hay ningún endpoint que permita modificarlo
directamente sobre una factura ya generada (ni `FacturacionController` ni `LiquidacionController`
exponen un campo de porcentaje editable) — "tampoco modificable por API" se cumple.

## Estados de Facturación — PARCIAL respecto de lo documentado
`docs/schema.sql` comenta 5 estados posibles (`Pendiente/Pagado/Parcial/Anulado/Reintegrado`),
pero el código (`FacturacionServiceImpl`) solo produce 3: `PENDIENTE`, `PAGADO`, `ANULADO`.
**`PARCIAL` y `REINTEGRADO` no están implementados** — no hay ningún camino de código que los
asigne (grep sobre todo `src/main/java` solo los encuentra en comentarios).

## Auditoría financiera — NO IMPLEMENTADA
Mismo hallazgo que en Fase 3: no existe ninguna tabla ni mecanismo de auditoría en todo el
esquema. No hay forma de ver "valores anteriores y nuevos" de un cambio en Facturación/
Liquidación/Cierre — ninguna de esas tablas tiene versión histórica ni existe un log de cambios.

## Descarga de comprobantes y liquidaciones — NO IMPLEMENTADA
Ni `FacturacionController` ni `LiquidacionController` exponen ningún endpoint de exportación/
descarga (PDF, comprobante, etc.) — mismo patrón que la descarga de HC completa (Fase 3). No hay
librería de generación de documentos en `pom.xml` más allá de Apache POI que agregué yo mismo
para leer `.xlsx` en Fase 5 (no la usa el proyecto).

## Consultas y filtros de Facturación — PARCIAL
Backend (`GET /api/facturacion`): solo `desde`/`hasta` (fecha). Frontend (`facturacion.js`):
agrega filtro client-side por `estado` (incluyendo el sub-estado sintético "transferencia a
validar") y un buscador de texto libre que busca sobre `nombrePaciente + nombreMedico`
concatenados (no son filtros independientes por médico y por paciente). **No hay filtro por obra
social, método de pago ni tipo de consulta** en ningún lado — ni backend ni frontend.

## Cierre diario — totales y split
Automáticamente correctos en su fórmula (usa el mismo `SplitFinancieroCalculator` que
Facturación y Liquidación, así que si se corrige BUG-003 el split queda alineado en los tres
lugares a la vez) — el único defecto es el mismo BUG-003 (incluye facturas `ANULADO`).
`cantidadTurnos` también queda inflado por el mismo motivo (7 en vez de 5, cuenta las 2 facturas
anuladas como si fueran turnos facturados del día).
