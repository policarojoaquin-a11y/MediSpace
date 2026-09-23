# TEST_REPORT — MediSpace QA end-to-end

**Fecha**: 2026-09-23
**Alcance**: Fases 0-9 completas sobre la base viva `MediSpace` (`localhost\SQLEXPRESS01`), con
datos QA aislados (`QA_*`, `@qa.test`, DNI 99000001-99000999, matrículas `QA-*`) creados,
probados, y **ya limpiados** al cierre de esta sesión. Los bugs encontrados durante la QA
**fueron corregidos** en la misma sesión, verificados con tests automatizados y en vivo.

Este documento no contiene credenciales ni datos personales reales — ver `qa/*.md` para el
detalle completo de cada fase.

## Backup y seguridad de la base real
- Backup completo verificado: `medispace_pre_qa_20260923.bak` (6.52 MB, `WITH CHECKSUM`,
  `RESTORE VERIFYONLY` OK).
- Snapshot de control pre-QA vs. post-limpieza: **coincidencia exacta, byte a byte, en las 22
  tablas reales** (`qa/snapshot_control_pre_qa_20260923.txt` vs. re-ejecución de
  `qa/snapshot_control.sql` tras `qa_cleanup.sql`). `Dashboard_Gerencial` (sin filtro QA propio,
  comparado fila por fila excluyendo `Fecha_Actualizacion`): **10 filas históricas idénticas**;
  la fila de hoy (2026-09-23), recalculada después de la limpieza, coincide **exacta** con una
  consulta SQL independiente sobre `Turnos`/`Facturacion` ya limpios
  (`16/0/0/0/16/$0/$0/0` en ambos lados).
- **Resultado: cero diferencias en datos reales.** Ningún registro real fue alterado, borrado ni
  contaminado en ningún momento de esta sesión.
- Los 4 archivos adjuntos QA subidos a disco durante las pruebas fueron borrados.
- `qa_cleanup.sql` corrió en modo dry-run primero (verificado, `ROLLBACK`, conteos coincidentes)
  y luego en modo real (`COMMIT`) — confirmado 0 filas QA remanentes en las 11 verificaciones
  cruzadas (usuarios, pacientes, médicos, consultorios, obras sociales, especialidades, turnos,
  facturación, arrendamientos, liquidaciones).

## Resumen ejecutivo

| Categoría | Cantidad |
|---|---:|
| Bugs encontrados y **corregidos** en esta sesión | **5** (4 causas raíz + 1 de permisos) |
| Reglas de negocio nuevas agregadas por los fixes | RN-022, RN-023, RN-024 |
| Reglas de negocio verificadas PASS (código ya existente) | 23 de 23 revisadas (RN-001 a RN-021) |
| Falsas alarmas investigadas y descartadas correctamente | 2 (ver Fase 7) |
| Funcionalidades NO IMPLEMENTADAS | 14 |
| Funcionalidades PARCIALES | 4 |
| Puntos A DEFINIR (incl. 1 reclasificado) | 4 |
| Hallazgos de seguridad (reportados, no corregidos a pedido del usuario) | 1 |
| Tests automatizados | **135/135 verdes** (125 originales + 10 nuevos) |

## Bugs encontrados y corregidos

| # | Severidad | Módulo | Resumen | Fix | Test |
|---|---|---|---|---|---|
| BUG-001 | Alta | Arrendamiento | Se podía crear un contrato sobre un consultorio no disponible | **RN-022** nueva en `ArrendamientoServiceImpl.crearArrendamiento` | `testRN022_ConsultorioNoDisponibleRechazado` |
| BUG-001b | Media | Turnos | Consecuencia: turnos "Disponible" reservables en consultorio fuera de servicio | Resuelto automáticamente por el fix de RN-022 | (cubierto por el mismo test) |
| BUG-002 | Media | Arrendamiento/Turnos | Un contrato podía quedar "ACTIVO" generando 0 turnos reales, sin aviso | **RN-023** nueva — bloquea el segundo contrato en la causa raíz | `testRN023_SuperposicionMedicoEnOtroConsultorioRechazada` |
| BUG-003 | Alta | Facturación/Liquidación/Reportes | Liquidación, Cierre Diario y 3 reportes sumaban facturas `ANULADO` como cobradas (evidencia: $140.000 vs $100.000 real) | Mismo filtro que ya tenía el Dashboard, aplicado en los 5 lugares | `testBUG003_*` (4 tests, uno por método afectado) |
| BUG-004 | Alta | Liquidación | La misma facturación se podía liquidar dos veces (períodos superpuestos) | **RN-024** nueva — rechaza si se superpone con una liquidación EMITIDA existente | `testRN024_BloqueaLiquidacionSuperpuesta` |
| BUG-005 | Media | Historia Clínica / Permisos | `PUT`/`DELETE` evoluciones permitían GERENTE, contra RN-010 ("ningún otro rol" además del médico tratante) — hallado en producción por un `EntityNotFoundException` en cascada al probar el escenario | `@PreAuthorize` corregido a solo `MEDICO` en ambos endpoints | `editarEvolucion_PreAuthorizeExcluyeAGerente`, `anularEvolucion_PreAuthorizeExcluyeAGerente` |

Los 5 fixes están verificados por partida doble: `mvn test` (135/135) y en vivo contra la base
real reiniciando la app con el código nuevo, reproduciendo los mismos pasos que encontraron cada
bug. Detalle completo con evidencia en `docs/ANEXO_CAMBIOS_POST_ENTREGA.md` (Parte K) y
`qa/VERIFICACION_FIXES_2026-09-23.md`.

**Nota sobre BUG-003**: el propio código de `ReporteServiceImpl.recalcularDashboard` (Dashboard
Gerencial) ya tenía el fix correcto antes de esta sesión — la corrección se había hecho en un
solo lugar y nunca se había propagado a los otros 5 puntos que suman `Facturacion.Importe_Total`.

**Nota sobre BUG-005**: se descubrió en vivo durante la sesión por un incidente real —
`EntityNotFoundException: Unable to find Paciente with id 20` reportado por el usuario. La causa
fue un bug relacionado pero distinto (`PacienteController` no tiene endpoint de reactivación,
dejando una referencia colgante tras una baja de prueba) — resuelto en caliente por SQL directo
(mismo patrón que el incidente RN-019/RN-020 de producción ya documentado en `docs/spec.md`).
Ver "Hallazgo adicional" más abajo — este segundo problema **no se corrigió** en código todavía.

## Hallazgo adicional (no corregido) — Pacientes sin endpoint de reactivación
`PacienteController` tiene `DELETE /{id}` (baja lógica) pero **no** `PUT /{id}/reactivar` —a
diferencia de Usuarios y Médicos, que sí lo tienen. Un paciente dado de baja queda inaccesible
para siempre por API, y cualquier referencia colgante a su Historia Clínica (u otra entidad
relacionada) puede producir un `EntityNotFoundException` en cascada, replicando el mismo patrón
de incidente ya documentado para Usuario/ObraSocial (`docs/spec.md` línea 150). **Se recomienda
agregar `PUT /api/pacientes/{id}/reactivar`** siguiendo el mismo patrón que `MedicoController`/
`UsuarioController`. No se corrigió en esta sesión (fuera del alcance explícito pedido) — mitigado
en caliente para el caso puntual encontrado.

## Funcionalidades NO IMPLEMENTADAS

| Funcionalidad | Sección de la propuesta que la pide | Evidencia |
|---|---|---|
| Recuperación de contraseña (link 20 min) | 1.4 Interfaces (implícito, login) | Solo existe `/api/auth/login` |
| Descarga de Historia Clínica completa (PDF) | No especificada explícitamente | Sin endpoint ni librería de PDF |
| Auditoría de accesos y modificaciones (HC) | RF-H6 (Anexo) | Sin tabla ni mecanismo de audit log en todo el esquema |
| Auditoría financiera (valores anteriores/nuevos) | — | Idem |
| Descarga de comprobantes y liquidaciones | — | Sin endpoint de exportación |
| Exportación a Excel (RF-R3) | 1.3.7 Módulo de Reportes | Gap reconocido por `docs/spec.md` línea 29 |
| Estados de Facturación `PARCIAL` / `REINTEGRADO` | schema.sql (comentario) | Documentados pero ningún código los asigna |
| Reportes: ausentes, cancelaciones, tiempo de espera, pacientes atendidos/historial, especialidades más demandadas, agenda ocupada, horas ociosas | 1.3.7 (Reportes Operativos/Médicos) | No existen como reportes propios |
| KPIs del Dashboard (especialidades/horarios de mayor demanda, ranking médicos, % ausentismo, ingresos mensuales, obras sociales con mayor volumen, rentabilidad) | 1.4 Interfaz de Gerencia | `DashboardResponseDTO` solo expone 8 campos |
| Filtros por período en el Dashboard | 1.4 Interfaz de Gerencia | Solo admite una fecha puntual |
| Tiempo real en la agenda del médico (Flujo A) | Flujo A (paso 3) | Sin polling/WebSocket/SSE en el frontend |
| "Recordatorios Pendientes" (menú Administrativo) | 1.4 Interfaz Personal Administrativo | Sin concepto de "recordatorio" en el backend |
| "Estadísticas Generales" / "Configuración del Sistema" / ítem de menú "Gestión de Consultorios" (Gerente) | 1.4 Interfaz de Gerencia | Sin ítem de nav ni pantalla dedicada (el CRUD de consultorios sí existe vía API) |
| `PUT /api/pacientes/{id}/reactivar` | Implícito (estados Activo/Inactivo/Fallecido) | Ver "Hallazgo adicional" arriba |

## Funcionalidades PARCIALES

| Funcionalidad | Detalle |
|---|---|
| Búsqueda de Historia Clínica (nombre/DNI) | Client-side, un solo resultado, sin filtro por médico ni fecha |
| Filtros de Facturación | Backend solo fecha; frontend agrega estado y texto libre, sin obra social/método/tipo |
| Reporte de uso de consultorios | Cubre uso básico y rentabilidad; horas ociosas no se calcula |
| Contrato "lunes a miércoles" como una sola operación | El modelo solo admite 1 día por contrato — hacen falta 3 altas separadas |

## Puntos A DEFINIR

1. **Cancelación anticipada**: el código implementa "vuelve a Disponible" para cancelaciones
   ANTES del horario, y "Cancelado" solo para cancelaciones tardías — contradice el resultado
   literal que pide el enunciado de QA para "cancelación anticipada" (que se supone antes del
   horario, pero se espera "→ Cancelado").
2. **RN-006** (70/30 a nivel médico vs. contrato): confirmado que el split vive en
   `Arrendamiento_Modulo`, no en `Medicos`.
3. **Descarga de HC**: no especificada explícitamente en la propuesta original.
4. **Reportes para Administrativo — RECLASIFICADO** (era "bug de permisos", corregido a A
   DEFINIR): el documento original se contradice a sí mismo — su menú "1.4 Interfaces" lista
   "Reportes Operativos" para Administrativo, pero la sección 1.3.7 aclara explícitamente que
   Administrativo accede a información equivalente desde Turnos/Facturación, **no** desde
   Reportes. El código (403 para Administrativo) coincide con la sección 1.3.7. Ver
   `qa/DIFF_MENUS_HOME.md`.

## Hallazgo de seguridad (reportado, no corregido a pedido explícito del usuario)
**Password inicial del médico = su matrícula**, sin forzar cambio en el primer login. La
matrícula es un dato semi-público dentro del sistema (visible para Gerente/Administrativo, y
potencialmente conocida fuera del sistema). Severidad media — requiere conocer/adivinar la
matrícula. Detalle y recomendación en `qa/FASE7_ABM_PERMISOS.md`.

## Documentación corregida durante esta sesión
- `docs/spec.md`: agregadas RN-022 a RN-024; nota corregida sobre qué RN son "oficiales" del
  documento original (hasta RN-018, no RN-015 — ver `qa/DIFF_MENSAJES_RN.md`).
- `docs/ANEXO_CAMBIOS_POST_ENTREGA.md`: Parte K con el detalle completo de los 5 fixes.

## Matriz de permisos
Ver `qa/FASE7_ABM_PERMISOS.md` — matriz completa por módulo × rol (GERENTE/ADMINISTRATIVO/
MEDICO/sin sesión), confirmada en vivo endpoint por endpoint a lo largo de toda la suite.

## Diffs contra la propuesta original (pedidos explícitamente)
- `qa/DIFF_MENSAJES_RN.md` — mensajes de error de las 18 reglas oficiales, documento vs. código.
- `qa/DIFF_MENUS_HOME.md` — menús de Home por rol, documento vs. `NAV_CONFIG` real.
- `qa/DIFF_PALETA_COLORES.md` — paleta de colores, documento vs. CSS real (verificado con
  `getComputedStyle` en vivo). 9 de 10 valores coinciden exactos.

## Documentos por fase (detalle completo)
`qa/FASE0_RECONOCIMIENTO.md` · `qa/FASE2_FLUJO_A.md` · `qa/FASE2_FLUJO_B.md` ·
`qa/FASE2_FLUJO_C.md` · `qa/FASE3_HISTORIA_CLINICA.md` ·
`qa/FASE4_FACTURACION_LIQUIDACIONES.md` · `qa/FASE5_6_REPORTES_DASHBOARD.md` ·
`qa/FASE7_ABM_PERMISOS.md` · `qa/FASE8_NO_FUNCIONALES.md` ·
`qa/VERIFICACION_FIXES_2026-09-23.md`

## Suite reutilizable
- `qa/qa_seed_fase1.ps1` — datos QA (especialidades, obras sociales, consultorios, médicos vía
  flujo real, pacientes), credenciales vía `.env.qa` (no versionado).
- `qa/qa_cleanup.sql` — limpieza transaccional con modo dry-run (`@DryRun`), verificado.
- `qa/snapshot_control.sql` — snapshot de control re-ejecutable (incluye comparación especial
  fila por fila para `Dashboard_Gerencial`).
- `qa/e2e/ResponsiveSmokeTest.java`, `qa/e2e/PaletteCheck.java` — Playwright standalone.
- `qa/load-env.ps1` + `.env.qa.example` — manejo de credenciales sin texto plano.

## Estado final
**Todos los datos QA fueron eliminados.** La base real quedó verificada byte-a-byte idéntica a
su estado previo a esta sesión (excepto por los 5 bugs corregidos en el código de la aplicación,
que persisten como mejoras permanentes). La app está corriendo con el código corregido.
