# Fase 5 — Reportes y exportaciones / Fase 6 — Dashboard gerencial

Generado: 2026-09-23. Pruebas por API directa. Dado que la mayoría de los turnos/facturas QA ya
existían de fases anteriores, la metodología "antes/después de crear los datos QA" del enunciado
se aplicó en su forma más estricta para el **Dashboard** (Fase 6, ver más abajo, con turnos QA
recién liberados de `QA_Dra_Pedia` corridos a la fecha de hoy) y de forma parcial para los
reportes de Fase 5 (se validó el total actual contra una consulta SQL independiente, que es el
otro método que pide el propio enunciado — punto 4 de Fase 5).

## BUG-003 se confirma también en los 3 reportes de Fase 5
Ya identificado en Fase 4 por lectura de código; confirmado ahora en vivo:

`GET /api/reportes/facturacion/medico?desde=2026-09-23&hasta=2026-09-23` para `QA_Dr_Cardio`:
`totalFacturado=140000.00` (API) — validado contra SQL independiente:
`SUM(Importe_Total) WHERE Estado_Pago <> 'ANULADO' = 100000.00` → **la API infla el total en
$40.000** (las 2 facturas `ANULADO` de $20.000 c/u). `totalCobrado` (que sí filtra por `PAGADO`)
da correctamente $100.000. Mismo defecto en `reporteFacturacionPorObraSocial` y
`reporteUsoConsultorios` (confirmado por lectura de código en Fase 4, ambos comparten la misma
consulta sin filtro de estado).

## Permisos de `/api/reportes/*` — RECLASIFICADO a A DEFINIR (corrección 2026-09-23)
Los 4 endpoints (`/dashboard`, `/dashboard/recalcular`, `/facturacion/medico`,
`/facturacion/obra-social`, `/consultorios`) son **exclusivos de GERENTE** — probado con
`ADMINISTRATIVO` y `MEDICO`, ambos `403` en los 5. Originalmente marqué esto como un gap de
permisos citando `docs/spec.md` línea 227 (que lista "Reportes Operativos" en el menú de
Administrativo). **Corrección**: el documento original
(`docs/Entrega primer cuatrimestre Policaro (1).md`, sección 1.3.7) aclara explícitamente que
*"el acceso a información equivalente para Administrativo se realiza desde los módulos de
Turnos (filtro por fecha/estado) y Facturación (filtro por estado de pago), no desde el módulo
de Reportes."* — el documento original **se contradice a sí mismo** (su propia lista de menú
"1.4 Interfaces" sí incluye "Reportes Operativos" para Administrativo). El código (403) coincide
con la sección 1.3.7, no con la lista de menú. **Reclasificado de "bug de permisos" a A DEFINIR**
— no hay nada que corregir del lado del código; la ambigüedad está en la especificación. Ver
`qa/DIFF_MENUS_HOME.md` para el detalle completo de ambos textos.

## Exportación a Excel (RF-R3) — NO IMPLEMENTADA (gap ya documentado por el propio proyecto)
`docs/spec.md` línea 29 lo dice explícitamente: *"Gap pre-existente, no resuelto acá: RF-R3 no
está implementado para ningún rol (no hay endpoint de exportación en todo el backend)."* No hay
ninguna ruta `/export` ni generación de `.xlsx` en ningún controller. Confirmado además por
ausencia total de librerías de Excel en `pom.xml` (agregué Apache POI yo mismo para esta suite,
el proyecto no la usaba). Por lo tanto los puntos 5 de Fase 5 (exportar, abrir con POI, verificar
`Reporte_Generado`) no son aplicables — no hay nada que exportar.

## Reportes contemplados en el enunciado vs. lo implementado
| Reporte pedido (Fase 5) | Estado |
|---|---|
| Turnos por fecha | PARCIAL — vía `GET /api/turnos?fecha=` (filtro de listado, no un "reporte" per se) |
| Ausentes / cancelaciones | **NO IMPLEMENTADA** como reporte propio — solo inferible contando `estado=NO_ASISTIO`/`CANCELADO` en el listado crudo de turnos |
| Tiempo de espera | **NO IMPLEMENTADA** — no hay ningún campo que registre cuándo empezó/terminó la espera (`Fecha_Reserva` existe, pero no hay timestamp de "pasó a Atendido" separado de `Fecha_Facturacion`) |
| Pacientes atendidos / historial / especialidades más demandadas / agenda ocupada (médicos) | **NO IMPLEMENTADA** como reportes dedicados |
| Facturación total / por médico / pendientes / por obra social / copagos | **PASS** (con el bug de facturas anuladas, BUG-003) — cubierto por `reporteFacturacionPorMedico`/`PorObraSocial` |
| Uso de consultorios / horas ociosas / rentabilidad por módulo / liquidaciones (arrendamiento) | **PARCIAL** — `reporteUsoConsultorios` cubre uso+rentabilidad básica (con BUG-003); horas ociosas **no** se calcula en ningún lado; liquidaciones ya tienen su propio listado (`/api/liquidaciones`) |

## Fase 6 — Dashboard Gerencial: validación por diferencia (delta)

**Permisos**: mismo resultado que arriba — solo GERENTE accede (`403` para Admin y Médico,
confirmado sobre `/reportes/dashboard`).

**Prueba de delta**: usé turnos QA de `QA_Dra_Pedia` (los de `QA_Dr_Cardio` ya estaban todos
usados por fases anteriores) corridos a la fecha de hoy vía SQL (QA-only, filtrado por ID +
matrícula). El enunciado pide "1 cobro de $20.000" — usé $15.000 (el importe real configurado de
`QA_Dra_Pedia`) porque no quedaban turnos limpios de `QA_Dr_Cardio`; la metodología de validar el
delta exacto es la misma.

| KPI | Antes | Después | Delta | Esperado | Resultado |
|---|---:|---:|---:|---:|---|
| turnosAtendidos | 0 | 1 | +1 | +1 | PASS |
| turnosNoAsistio | 0 | 1 | +1 | +1 | PASS |
| turnosCancelados | 0 | 1 | +1 | +1 | PASS |
| turnosDisponibles | 20 | 16 | -4 | -4 (los 4 turnos QA dejaron de estar libres) | PASS |
| facturacionTotalDia | 100000 | 130000 | +30000 | +30000 (ver nota) | PASS |
| cobrosPendientes | 0 | 15000 | +15000 | +15000 (turno "en espera" sin cobrar) | PASS |

**Nota sobre `facturacionTotalDia`**: mi expectativa inicial era +15.000 (solo el cobro), pero el
resultado correcto es +30.000 porque este KPI suma **toda** la facturación generada (`PAGADO` +
`PENDIENTE`, excluyendo `ANULADO`/`REINTEGRADO`) — no solo lo efectivamente cobrado. El turno
"en espera" (1888) generó una factura `PENDIENTE` de $15.000 que también cuenta acá, y por
separado en `cobrosPendientes`. Confirmé que esto es el diseño correcto (no un bug) al leer
`ReporteServiceImpl.recalcularDashboard`: `facturacionTotalDia` y `cobrosPendientes` son dos
KPIs con significados distintos y complementarios.

**Validación independiente por SQL** (regla del enunciado): los 8 valores absolutos del Dashboard
tras el evento coinciden **exactamente** con una consulta SQL independiente
(`20/1/1/1/16/130000/15000/6`) — el Dashboard es la única de las 6 superficies financieras
revisadas en esta suite (Facturación, Liquidación, Cierre Diario, 3 reportes de Fase 5, Dashboard)
que **sí** excluye correctamente las facturas `ANULADO`/`REINTEGRADO` de sus totales — ver la nota
en `qa/FASE4_FACTURACION_LIQUIDACIONES.md` sobre el fix aplicado acá pero no propagado al resto.

## KPIs del enunciado ausentes en `DashboardResponseDTO`
El DTO actual solo expone 8 campos: `totalTurnosDia`, `turnosAtendidos`, `turnosCancelados`,
`turnosNoAsistio`, `turnosDisponibles`, `facturacionTotalDia`, `cobrosPendientes`,
`nuevosPacientes`. El enunciado pide además, **todos NO IMPLEMENTADOS**: especialidades y
horarios de mayor demanda, pacientes por médico, % de ausentismo, ranking de facturación por
médico, ingresos mensuales (solo hay "del día"), copagos totales, obras sociales con mayor
volumen, rentabilidad, uso de consultorios (esto último sí existe pero como reporte aparte, no
como KPI del dashboard). Filtros por período: **no implementados** en el dashboard — solo
`/dashboard/recalcular?fecha=` acepta una fecha puntual, no un rango.

## Recalculo de indicadores tras la limpieza QA (Fase 9)
Pendiente — se hará al final, junto con la limpieza, recalculando el Dashboard del/de los día(s)
usados por esta suite para que no queden contaminados con datos QA (regla explícita del
enunciado, Fase 6 punto final).

## Gráficos / alertas operativas / responsive del Dashboard
Pendiente de la pasada con Playwright (Fase 8) — no evaluado en esta sección backend-only.
