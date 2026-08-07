# Auditoría MediSpace

> Auditoría de solo lectura sobre el estado real del código respecto a `docs/MediSpace_Propuesta_Tecnica_y_BD (1).md` (propuesta técnica + Anexo de desvíos confirmados). No se modificó ningún archivo de código, no se corrieron migraciones ni se tocaron tests existentes.
>
> **Metodología.** El servidor MCP `graphify-ts` está declarado en `.mcp.json` pero no estuvo disponible en esta sesión (confirmado con múltiples búsquedas de herramientas). Siguiendo el propio fallback documentado en `CLAUDE.md`, se leyó primero `graphify-out/GRAPH_REPORT.md` y luego se auditó cada uno de los 8 módulos con un agente de exploración independiente por módulo, usando lectura directa de código (Glob/Grep/Read) en paralelo. Cada agente citó archivo y línea para cada hallazgo; donde no pudo verificar con certeza, se marca explícitamente "requiere verificación manual". El rol **PACIENTE no tiene login** (decisión de producto confirmada, Anexo #1) — no se reporta en ningún punto como gap.
>
> **Resultado de `mvn clean compile`:** ✅ **BUILD SUCCESS** (exit code 0). El proyecto compila sin errores al momento de esta auditoría (2026-08-06). No se corrió `mvn test`.

---

## 1. Resumen ejecutivo

| Módulo | % funcionalidades completas (estimado) | Bugs abiertos | Modularidad | Prioridad de atención |
|---|---|---|---|---|
| Usuarios | ~15% | 2 | Modular (por ausencia de lógica, no por diseño probado) | **Media** |
| Pacientes | ~65% | 2 | Modular | **Media** |
| Médicos | ~60% | 4 (incl. 1 IDOR) | Modular con riesgos | **Alta** |
| Turnos | ~50% | 5 (incl. condición de carrera) | Modular con riesgos | **Alta** |
| Historias Clínicas | ~45% | 5 (incl. 1 de confidencialidad) | Modular con riesgos | **Alta** |
| Facturación / Liquidaciones | ~40% | 6 (incl. 2 de pérdida de datos monetarios) | Modular con riesgos | **Alta** |
| Reportes | ~25% | 5 | Modular con riesgos | **Media** |
| Arrendamiento | ~50% | 6 (incl. 2 IDOR) | Modular con riesgos | **Alta** |

Los porcentajes son una estimación cualitativa del auditor a partir de la clasificación funcionalidad-por-funcionalidad de la sección 3; no surgen de una métrica automatizada. "Bugs abiertos" cuenta únicamente los ítems que cada agente etiquetó explícitamente como BUG con evidencia de archivo:línea (no cuenta gaps de funcionalidad "no implementada").

**Hallazgo estructural positivo:** ningún módulo fue clasificado como **NO MODULAR**. No se encontró en ningún servicio backend una rama de código del tipo `if (rol == MEDICO) {...} else if (rol == ADMINISTRATIVO) {...lógica casi idéntica...}`, ni archivos de frontend duplicados por rol (`turnos-medico.js` vs `turnos-admin.js`, etc.). El principio de la sección 3 de la propuesta se sostiene a nivel estructural en los 8 módulos. Los riesgos detectados son de otro tipo: god nodes de baja cohesión, código muerto, inconsistencias entre capas de autorización, y — el hallazgo más grave — **varios bugs de control de acceso a nivel de objeto (IDOR)** donde un usuario autenticado con rol MEDICO puede consultar datos de otro médico simplemente cambiando un ID en la URL. Ver sección 2.

---

## 2. Auditoría de Modularidad

### 2.1 Panorama general

El código respeta la estructura pedida por la sección 3 de la propuesta en los 8 módulos: una entidad JPA por tabla, un repositorio por entidad, una interfaz de servicio + una única implementación, un controlador con `@PreAuthorize` por endpoint, y un único archivo JS por módulo (`turnos.js`, `medicos.js`, `pacientes.js`, `historias.js`, `facturacion.js`, `reportes.js`, `arrendamientos.js`). No se encontró la duplicación de lógica de negocio por rol que la sección 3 identifica como antipatrón a evitar.

Dicho esto, "sin duplicación por rol" no es sinónimo de "sin riesgo". Se identificaron cuatro categorías de riesgo real:

1. **God nodes de baja cohesión** (confirmado también por `graphify-out/GRAPH_REPORT.md`, que señala `ArrendamientoServiceImpl`, `TurnoServiceImpl`, `ReporteServiceImpl` y `MedicoServiceImpl` como los nodos de mayor centralidad del sistema).
2. **Autorización repartida de forma inconsistente entre capas** (controlador `@PreAuthorize`, lógica de enmascarado dentro del servicio, y gating de UI en el frontend) que ya derivó en bugs reales de exposición de datos.
3. **Código muerto / features a medio conectar** que indican refactors incompletos, no violaciones de modularidad en sí, pero sí deuda técnica que puede inducir a un desarrollador futuro a "arreglar" el síntoma agregando una rama por rol en el lugar equivocado.
4. **Módulos que son modulares porque casi no tienen lógica** (Usuarios), lo cual no es una violación pero tampoco es evidencia de que el patrón "resista" — no hay nada que duplicar todavía.

Ningún módulo requiere una unificación de servicios/controladores duplicados por rol (no existen duplicados). Las propuestas de esta sección son, en cambio, de **cohesión, consistencia de autorización y eliminación de código muerto**.

### 2.2 Detalle por módulo — "Con riesgos"

#### Médicos — MODULAR CON RIESGOS
- **Riesgo:** `app.js` contiene lógica de negocio del módulo Médicos. `loadMisDatos()` (`app.js:198-228`) y `loadMisPrestaciones()` (`app.js:170-195`) hacen fetch y renderizan HTML de datos/prestaciones del médico autenticado — esto debería vivir en `medicos.js`, no en `app.js`, que la sección 3.3 reserva exclusivamente para configuración de sidebar por rol.
- **Propuesta:** mover `loadMisDatos`/`loadMisPrestaciones` (y su render) a `medicos.js` como funciones del módulo, invocadas desde el router de `app.js` igual que el resto de las vistas.

#### Turnos — MODULAR CON RIESGOS
- **Riesgo:** `turnos.js` tiene dos funciones de construcción de acciones por turno — `buildTurnoAcciones()` (`turnos.js:221-235`) y `buildTurnoAccionesCompacto()` (`turnos.js:202-219`). No son variantes legítimas coexistentes: `buildTurnoAcciones` no tiene ningún call site (código muerto) y `buildTurnoAccionesCompacto` es la única usada, pero le faltan las acciones "Cancelar" y "No Asistió" que sí tiene la función muerta. No es duplicación por rol, es un refactor incompleto que dejó funcionalidad huérfana.
- **Propuesta:** eliminar `buildTurnoAcciones()` y portar las acciones "Cancelar"/"No Asistió" que le faltan a `buildTurnoAccionesCompacto()`, que debe quedar como única función de render de acciones.
- **Riesgo adicional:** `TurnoServiceImpl` no aplica ningún filtro de pertenencia para el rol MEDICO (ver bug de seguridad en 2.3) — la "limpieza" modular del servicio (sin ramas por rol) esconde que el control "solo propios" simplemente no está implementado en ningún lado, ni en el controller ni en el service.

#### Historias Clínicas — MODULAR CON RIESGOS
- **Riesgo:** la autorización real queda repartida en tres lugares que no coinciden entre sí: `@PreAuthorize` del controlador (`HistoriaClinicaController.java:22,35,47,59`), el enmascarado por rol dentro de `mapEvolucionToDTO` (`HistoriaClinicaServiceImpl.java:133-134`, que sólo distingue `"PACIENTE"` de "el resto"), y el gating de UI del botón "Historia Clínica" en `pacientes.js:55` (sin ningún control de rol). Consecuencia real: GERENTE y ADMINISTRATIVO —que según la tabla de permisos de la sección 4.5 tienen "No" y "Parcial" respectivamente— hoy ven el texto clínico completo (diagnóstico, tratamiento, indicaciones) porque el enmascarado sólo excluye a `"PACIENTE"`, rol que no existe con login.
- **Riesgo adicional:** `anularEvolucion` (`HistoriaClinicaServiceImpl.java:110-131`) no reutiliza la validación de autoría de médico que sí tiene `editarEvolucion` (líneas 93-97) — misma clase, mismo archivo, lógica de permisos duplicada de forma incompleta (presente en un método, ausente en el otro).
- **Propuesta:** centralizar el enmascarado/autorización de lectura en un único punto del servicio que reciba el rol Y lo module contra la tabla de permisos real de la sección 4.5 (No/Parcial/Sí), no solo contra "es paciente o no". Extraer la validación de autoría de `editarEvolucion` a un método privado reutilizado también por `anularEvolucion`.

#### Facturación / Liquidaciones — MODULAR CON RIESGOS
- **Resuelto en Sprint 2:** `PagoFacturacion` (entidad + repositorio) existía en el modelo de datos sin service, sin controller, y sin ningún uso en código de producción (0 referencias fuera de su propio archivo). Era el remanente de un rediseño a medio camino: el equipo migró a `Cobros` (más simple, 1:1 con turno) y dejó `Pago_Facturacion` (más rico, admite N pagos/comprobante/estado propio) huérfano — lo cual explica directamente por qué el estado `PARCIAL` y el flujo de "transferencia pendiente de validación" nunca se implementaron. **Decisión tomada:** eliminar `PagoFacturacion.java`/`PagoFacturacionRepository.java` formalmente en vez de retomarlos — `Cobros` cubre el caso real usado en producción y no había ningún caller que migrar.
- **Riesgo adicional:** el split 70/30 vive hardcodeado en `FacturacionServiceImpl.java:38-40` en vez de leerse de `ArrendamientoModulo.porcentajeMedico/porcentajeConsultorio` (que sí existe para eso), y `Facturacion.idArrendamiento` nunca se setea (queda siempre NULL) — dos fuentes de verdad para el mismo dato, una de ellas sin conectar.
- **Riesgo adicional:** las reglas RN-005 y RN-006 están con la numeración **invertida** en comentarios y nombres de test entre `FacturacionServiceImpl`/`FacturacionServiceTest` y `LiquidacionServiceImpl`/`LiquidacionServiceTest` respecto a la numeración del documento fuente (detalle en sección 4). No es un bug de comportamiento, pero es un riesgo de mantenibilidad real: un desarrollador que busque "RN-006" en el código va a encontrar la regla equivocada.
- **Propuesta:** (a) decidir explícitamente si `Pago_Facturacion` se retoma (para soportar pagos parciales/múltiples/comprobante) o se elimina del modelo — hoy es deuda muerta sin dueño; (b) hacer que `FacturacionServiceImpl` lea el split desde `ArrendamientoModulo` del contrato vigente y complete `idArrendamiento`; (c) corregir la numeración RN-005/RN-006 en comentarios y nombres de test para que coincidan con el documento fuente.

#### Reportes — MODULAR CON RIESGOS
- **Riesgo:** `ReporteServiceImpl` inyecta 5 repositorios y en **todos** sus métodos hace `findAll()` seguido de filtrado/agrupado en memoria con streams Java (líneas 47, 54, 65, 91, 125, 148) en vez de queries SQL filtradas por fecha. Es el god node de mayor acoplamiento del sistema según `GRAPH_REPORT.md`, y con volumen real de datos esto degrada severamente o hace inviable el módulo.
- **Riesgo adicional:** el indicador "Liquidaciones médicas" de la sección 4.7 vive fuera del módulo Reportes (en `LiquidacionController`/`LiquidacionService`, con control de acceso más amplio que "solo Gerente"), y no está expuesto ni reutilizado por `ReporteService` — inconsistencia de ubicación funcional respecto a la especificación.
- **Propuesta:** reescribir los métodos de `ReporteServiceImpl` como queries agregadas a nivel de repositorio (`@Query` con `GROUP BY`/`SUM`/filtro por rango de fecha), en vez de `findAll()` + stream. Decidir si "Liquidaciones médicas" se expone también desde `/api/reportes` o si se documenta explícitamente que vive en el módulo Facturación.

#### Arrendamiento — MODULAR CON RIESGOS
- **Riesgo:** `ArrendamientoServiceImpl` agrupa 4 responsabilidades poco relacionadas entre sí — contratos (`crearArrendamiento`, `darDeBajaArrendamiento`), uso operativo (`registrarUso`), cierre económico diario (`generarCierreDiario`) y disparo de generación de turnos (delegando a `TurnoService`) — en una sola clase con 5 repositorios inyectados. `generarCierreDiario` además reimplementa un cálculo de liquidación que ya existe (y diverge) en `LiquidacionServiceImpl`, sin que ambos flujos se alimenten entre sí.
- **Propuesta:** separar `UsoConsultorioService` y `CierreDiarioService` de `ArrendamientoService` (que quedaría acotado a gestión de contratos), y conectar `CierreDiario` con `LiquidacionMedica` en vez de mantener dos cálculos económicos independientes que pueden divergir.

### 2.3 Hallazgo transversal: bugs de control de acceso a nivel de objeto (IDOR)

No es un problema de modularidad en sí, pero surge repetidamente en los módulos "con riesgos" y merece tratamiento unificado porque **es el hallazgo de seguridad más importante de esta auditoría**: varios endpoints validan el **rol** (`@PreAuthorize("hasAnyRole(...,'MEDICO')")`) pero no validan que el **recurso solicitado pertenezca al usuario autenticado**:

- `GET /api/arrendamientos/medico/{idMedico}` (`ArrendamientoController.java:33-37`) — un MEDICO puede pasar cualquier `idMedico` y ver la agenda/contratos de otro médico.
- `GET /api/liquidaciones/medico/{idMedico}` (`LiquidacionController.java:35-39`) — mismo patrón, expone liquidaciones de otro médico.
- `TurnoController.java:30-37,45-54` — un MEDICO puede cambiar estado o listar turnos de otro médico pasando un `idMedico`/`idsMedico` arbitrario.

El patrón correcto **ya existe en el codebase** (`MedicoController.java:40-44`, endpoint `/me` que resuelve el médico desde el `Authentication` del JWT) pero no se aplicó de forma consistente en Arrendamiento, Liquidación ni Turnos. Ver plan de acción, sección 6, "Bloqueantes".

---

## 3. Por interfaz de usuario

### 3.1 GERENTE

**Usuarios** — Debería: alta/baja/modificación/consulta completos, único rol que modifica roles. Realmente puede: nada directamente. No existe pantalla de gestión de usuarios (`UsuarioController.java` no tiene ningún endpoint). Solo puede generar un usuario de forma indirecta al dar de alta un Médico. **Gap:** todo el módulo dedicado está ausente — alta directa de usuarios GERENTE/ADMINISTRATIVO, baja, reactivación, modificación de rol y consulta con filtros no existen en ningún punto del sistema.

**Pacientes** — Debería (según matriz 3.4): solo Consulta. Realmente puede: CRUD completo idéntico a Administrativo (`PacienteController.java` incluye `GERENTE` en `@PreAuthorize` de POST/PUT/DELETE; el sidebar le muestra "Nuevo Paciente"). **Gap:** discrepancia real entre la matriz 3.4 y el código — el código no distingue Gerente de Administrativo en este módulo en absoluto.

**Médicos** — Debería: alta/baja/modificación/consulta completos. Realmente puede: exactamente eso, y el bug histórico de alta permitida a Administrativo está confirmado como corregido (`MedicoController.java:24-25`, solo `hasRole('GERENTE')`). **Gap:** no puede reactivar médicos dados de baja (no existe la vía), no puede editar una configuración de agenda existente (solo crear un contrato nuevo o desactivarlo).

**Turnos** — Debería: reservar/cancelar/consulta global. Realmente puede: reservar y cambiar estado (`TurnoController.java:21-22` `hasAnyRole('GERENTE','ADMINISTRATIVO')`). **Gap:** el botón Cancelar es inalcanzable en la UI actual (código muerto, ver 2.2); reserva pierde silenciosamente tipo de consulta/método de pago/OS/copago cargados en el formulario.

**Historias Clínicas** — Debería: sin acceso. Realmente puede: acceso de lectura completo al texto clínico (diagnóstico, tratamiento, indicaciones) vía el botón "Historia Clínica" en Pacientes, sin gating de rol, y el backend lo permite explícitamente (`HistoriaClinicaController.java:22`, `hasAnyRole('GERENTE','ADMINISTRATIVO','MEDICO')`). **Gap de seguridad:** esto contradice directamente "Ver historias clínicas: No" de la tabla de permisos de la sección 4.5.

**Facturación** — Debería: consulta global/liquidaciones/exportar. Realmente puede: consulta global (`FacturacionController.java` incluye GERENTE), generar/anular liquidaciones (`LiquidacionController.java`). **Gap:** no hay ninguna pantalla de Liquidaciones en el frontend (`app.html` no tiene markup de liquidaciones) pese a que es el único rol con permiso de anularlas — funcionalidad de backend sin UI.

**Reportes** — Debería: dashboard gerencial completo, exclusivo. Realmente puede: exactamente eso — los 4 endpoints de `ReporteController.java` exigen `hasRole('GERENTE')` sin excepciones, confirmado. **Gap:** solo 3 de ~16 reportes de la especificación tienen UI (Facturación por Médico, por OS, Uso de Consultorios); exportación a Excel/impresión no existe; dashboard solo muestra el día de hoy (no hay selector de fecha pese a que el backend soporta recalcular históricos).

**Arrendamiento** — Debería: crear consultorio/asignar módulo/modificar disponibilidad. Realmente puede: crear consultorio y crear/dar de baja contratos. **Gap:** no existe ningún endpoint para modificar el estado de un consultorio (Bloqueado/En mantenimiento) — queda fijo en "Disponible" desde el alta para siempre; no puede ver historial de uso ni cierres diarios generados (sin pantalla ni endpoint GET).

### 3.2 ADMINISTRATIVO

**Usuarios** — Debería: alta (no médicos)/consulta. Realmente puede: nada — sin botón "Gestionar Usuarios" en el sidebar, sin endpoints. **Gap:** total, igual que para Gerente.

**Pacientes** — Debería: acceso completo (alta/baja/modificación/consulta + botón "Dar Turno"). Realmente puede: CRUD completo confirmado. **Gap:** el botón "Dar Turno" no existe como acción directa desde la fila de un paciente (la reserva de turno con paciente existe, pero desde el módulo Turnos); consulta con filtros solo cubre nombre+DNI, faltan Obra Social y Teléfono.

**Médicos** — Debería: modificación parcial/consulta, gestión de agenda. Realmente puede: modificación de datos personales **idéntica** a Gerente (no hay ninguna restricción de campos que justifique "parcial"); consulta con filtros incompleta (falta Obra Social); **no puede gestionar agenda en absoluto** — crear/dar de baja contratos de arrendamiento está restringido a `hasRole('GERENTE')` estricto (desvío confirmado por Anexo #4), contradiciendo la fila "Gestionar agenda: Sí" de la tabla de permisos de la sección 4.3.

**Turnos** — Debería: reservar/cancelar/sala de espera/consulta global. Realmente puede: reservar, gestionar sala de espera. **Gap:** mismos bugs que para Gerente (cancelar inalcanzable, datos de reserva perdidos); filtros de consulta incompletos (sin DNI/matrícula reales, solo texto libre).

**Historias Clínicas** — Debería: adjuntar estudios/consulta parcial. Realmente puede: consulta con el mismo acceso completo al texto clínico que Gerente (mismo bug de enmascarado, sección 2.2). **Gap:** "Adjuntar estudios" no está implementado en absoluto — no existe ningún endpoint de subida de archivos pese a que el modelo (`AdjuntoHistoriaClinica`) y el DTO existen.

**Facturación** — Debería: registrar cobros/liquidaciones/exportar. Realmente puede: registrar cobros y generar liquidaciones (backend). **Gap:** exportar reportes de facturación le está vedado — los 3 endpoints de `/api/reportes/facturacion/*` exigen `hasRole('GERENTE')` estricto, contradiciendo "Exportar reportes: Sí" de la tabla de permisos de la sección 4.6; sin UI de Liquidaciones (mismo gap que Gerente).

**Reportes** — Debería (según Anexo #3, desvío confirmado): sin acceso. Realmente puede: sin acceso, confirmado en los 4 endpoints. Correcto, no es un gap.

**Arrendamiento** — Debería: asignar módulo/ver disponibilidad/modificar disponibilidad. Realmente puede: ver consultorios y contratos. **Gap:** "asignar módulo" (crear contrato) está restringido a solo GERENTE por el desvío confirmado del Anexo #4 (contradice la tabla original de la sección 4.8, pero el Anexo tiene precedencia — no se reporta como gap sino como aclaración); "modificar disponibilidad" no existe como funcionalidad para nadie, tampoco para Administrativo.

### 3.3 MEDICO

**Usuarios** — Debería: sin acceso. Realmente: sin acceso, confirmado (`RolEnum` no otorga a MEDICO ningún endpoint de usuarios). Correcto.

**Pacientes** — Debería: consulta de pacientes vinculados (a sus turnos/HC). Realmente puede: consulta de **todos** los pacientes del sistema, sin ningún filtro por médico (`PacienteServiceImpl.listarPacientes()` hace `findAll()` sin acotar). El label del sidebar dice engañosamente "Mis Pacientes". **Gap de seguridad/privacidad:** un médico ve pacientes que nunca atendió.

**Médicos** — Debería: modificación de datos y agenda propios. Realmente puede: **nada** — "Mis Datos" es 100% de solo lectura (sin botón editar, sin backend `PUT /medicos/me`), y no puede gestionar su propia agenda (crear/editar contrato restringido a GERENTE). **Gap total** respecto a la especificación. Además, `GET /arrendamientos/medico/{idMedico}` permite ver la agenda de **otro** médico (IDOR, sección 2.3).

**Turnos** — Debería: consulta y gestión de agenda propia. Realmente puede: ver y operar turnos de **cualquier** médico, no solo los propios — ni el controller ni el service filtran por médico autenticado. **Gap de seguridad:** además de ver, puede técnicamente cambiar estados de turnos ajenos llamando la API directamente. Sin filtro de estado (Pendientes/En Espera/Atendidos/No Asistió) en "Mi Agenda"; sin indicadores de cantidad total/pendientes del día.

**Historias Clínicas** — Debería: registrar y modificar evoluciones propias. Realmente puede: registrar evoluciones (sin validar que el turno/paciente le corresponda), modificar solo las propias (RN-010 correctamente aplicada en `editarEvolucion`) — **pero puede anular evoluciones de cualquier médico**, porque `anularEvolucion` no reutiliza esa misma validación. No puede adjuntar estudios (no implementado para nadie).

**Facturación** — Debería: consulta propia. Realmente puede: **nada** — los 3 endpoints de `FacturacionController.java` excluyen explícitamente a MEDICO, pero el ítem de menú "Mis Liquidaciones" apunta a esa misma vista, produciendo un **403 Forbidden** al hacer clic. El backend sí expone `GET /api/liquidaciones/medico/{idMedico}` (resumen de liquidación, no detalle de facturación), pero la UI no lo usa.

**Reportes** — Debería: sin acceso (desvío confirmado). Realmente: sin acceso. Correcto.

**Arrendamiento** — Debería: ver disponibilidad, ver liquidaciones propias. Realmente puede: el backend permite ver disponibilidad de consultorios (`hasAnyRole` incluye MEDICO), pero **el ítem no aparece en su sidebar** (`app.js` `NAV_CONFIG.MEDICO` no incluye `arrendamientos`) — inalcanzable desde la UI pese a estar autorizado. "Ver liquidaciones propias" está roto por el IDOR de la sección 2.3: puede ver liquidaciones de otro médico cambiando el ID en la URL.

---

## 4. Reglas de negocio (RN-001 a RN-016)

| ID | Módulo | Implementada | ¿Una sola vez o duplicada por rol? | Test unitario | Observaciones |
|---|---|---|---|---|---|
| RN-001 | Turnos | **Sí** | Una sola vez (`TurnoServiceImpl.java:86-88`) | Sí, correcto (`TurnoServiceTest.java:70-86`) | — |
| RN-002 | Turnos | **Sí** | Una sola vez (`TurnoServiceImpl.java:94-97`) | Sí (`TurnoServiceTest.java:88-102`) | Test usa matcher `any()` para fecha/hora, cobertura algo laxa |
| RN-003 | Turnos | **Sí** | Una sola vez (`TurnoServiceImpl.java:100-103`) | Sí (`TurnoServiceTest.java:104-120`) | Mismo comentario que RN-002 |
| RN-004 | Turnos | **Sí** | Una sola vez (`TurnoServiceImpl.java:130-132`) | Parcial (`TurnoServiceTest.java:122-133`) | Test solo cubre `ATENDIDO→CANCELADO`, no `ATENDIDO→DISPONIBLE` explícitamente (misma condición de código, riesgo bajo) |
| RN-005 | Facturación | **Sí, pero con la etiqueta invertida en el código** | Una sola vez (`LiquidacionServiceImpl.java:43-49`, comentado como "RN-006") | Sí (`LiquidacionServiceTest.testRN006_NoLiquidaSiHayFacturasPendientes`) | El código y el test usan el nombre "RN-006" para lo que el documento define como RN-005 — corregir nomenclatura |
| RN-006 | Facturación | **Sí, con diferencia grave, y etiqueta invertida** | Una sola vez (`FacturacionServiceImpl.java:38-40`, comentado como "RN-005") | Sí, nombre también invertido (`testRN005_SplitFinanciero7030Configurado`) | Split 70/30 hardcodeado, no lee `ArrendamientoModulo` como "configurado a nivel médico/contrato" prometía la especificación |
| RN-007 | Facturación | **Sí** | Una sola vez (`LiquidacionServiceImpl.java:97-109`) | Sí, correcto (`LiquidacionServiceTest.java:74-107`) | "Regenerar" tras anular no es un flujo guiado, hay que volver a llamar `generarLiquidacion` manualmente |
| RN-008 | Pacientes | **Sí** | Una sola vez (`PacienteServiceImpl.java:80-83`) | Sí, correcto (`PacienteServiceTest.java:69-99`) | — |
| RN-009 | Pacientes | **Sí** | Una sola vez (`PacienteServiceImpl.java:33-37`, query nativa `countByDniIncludingInactive`) | Parcial | El test mockea el resultado del repositorio; no hay test de integración que ejercite realmente el `@SQLRestriction` con un paciente inactivo real |
| RN-010 | Historias Clínicas | **Sí en `editarEvolucion`, NO aplicada en `anularEvolucion`** | Una sola vez para editar (`HistoriaClinicaServiceImpl.java:93-97`); ausente en anular | Sí para editar (`HistoriaClinicaServiceTest.java:64-96`); sin test para el gap de anular | **Bug de seguridad**: cualquier médico puede anular evoluciones de otro médico |
| RN-011 | Historias Clínicas | **Sí (motivo obligatorio y orden de persistencia correctos)**, con efecto colateral grave | Una sola vez (`HistoriaClinicaServiceImpl.java:110-131`) | Sí, correcto (`HistoriaClinicaServiceTest.java`, incluye verificación de orden con `InOrder`) | La anulación reutiliza el soft-delete genérico (`Visible=0`) — la evolución "anulada" desaparece por completo de la vista de la HC, no queda visible con badge; el motivo queda enterrado en `Observaciones` de un registro invisible |
| RN-012 | Médicos | **Sí, con bug de comparación de strings** | Una sola vez (`MedicoServiceImpl.java:127-139`, query en `MedicoRepository.java:19`) | Sí, pero no cubre el bug (mockea el resultado de la query) | El literal SQL usa `'NO ASISTIO'` (con espacio); el valor real del sistema es `NO_ASISTIO` — turnos "No Asistió" futuros bloquean indebidamente la baja del médico |
| RN-013 | Arrendamiento | **Sí, incluyendo el desvío de `Dia_Semana`** | Una sola vez (`ArrendamientoServiceImpl.java:57-64`, `ArrendamientoModuloRepository.java:19-33`) | Sí (`ArrendamientoServiceTest.java:107-127`) | Test no cubre directamente la cláusula `Dia_Semana` de la query nativa (sería necesario un `@DataJpaTest`) |
| RN-014 | Arrendamiento | **Sí, límite inclusive correcto** | Una sola vez (`ArrendamientoServiceImpl.java:46-51`) | Sí, cubre ambos bordes (`ArrendamientoServiceTest.java:64-105`) | 4.0hs exactas son válidas, confirmado por test y código |
| RN-015 | Usuarios | **Sí, aunque redundante frente a la garantía estructural del modelo** | Una sola vez (`UsuarioServiceImpl.validarUnicoRol`, líneas 44-59) | Sí, 3 tests (`UsuarioServiceTest.java:38-89`) | `Usuarios.Rol` es una única columna, por lo que "más de un rol" ya es estructuralmente imposible; la validación protege más bien contra rol vacío/inválido |
| RN-016 | Facturación | **Sí** | Una sola vez (`TurnoRepository.countAtendidosSinCobro`, `LiquidacionServiceImpl.java:51-55`) | Sí, correcto (`LiquidacionServiceTest.java:110-160`) | En el flujo actual es funcionalmente redundante con RN-005 (ambas detectan el mismo hecho de negocio desde tablas distintas) — documentar la redundancia, no eliminarla sin análisis |

---

## 5. Modelo de datos

Comparación entre `docs/schema.sql` (fuente de verdad real del proyecto, según Anexo), la sección 10 de la propuesta, y las entidades JPA verificadas por los agentes.

| Tabla | ¿Existe en schema.sql? | ¿Coincide la estructura con la propuesta / el código? | Observaciones |
|---|---|---|---|
| Usuarios | Sí | Sí, exacta | Sin diferencias entre schema.sql, propuesta y entidad JPA |
| Pacientes | Sí | Sí, con el desvío confirmado (sin FK a Usuarios) | Desvío ya documentado (Anexo #1), correctamente reflejado en las 3 fuentes |
| ObraSocial | Sí | Sí | — |
| Medicos | Sí | Sí | `ID_Prestacion` suelto del documento original ya no existe en schema.sql (corregido, Anexo #6) |
| Especialidades | Sí | Sí | — |
| Agenda_Medico | Sí (deprecada) | Existe en el schema con comentario explícito "no la usa más", sin entidad JPA activa | Consistente con el desvío confirmado (Anexo #2) — tabla legada, no en uso |
| Medico_ObraSocial | Sí | Sí | Tabla N:N, sin cambios |
| Prestaciones_Medicas | Sí | Sí | Catálogo; el desvío está en Medico_Prestacion, no acá |
| Medico_Prestacion | Sí | Sí, implementa correctamente el desvío del Anexo #6 | Reemplaza la FK suelta original, con `Importe_Particular`/`Duracion_Estimada_Min`/`Tipo` por médico |
| Turnos | Sí | Sí, exacta | Sin diferencias |
| Cobros | Sí | Sí, en uso real (a diferencia de Pago_Facturacion) | Ver hallazgo abajo |
| Historia_Clinica | Sí | Sí, exacta | — |
| Evolucion_Clinica | Sí | Sí, con comentario propio del schema advirtiendo la ambigüedad | El propio `schema.sql:214` anota que usa `Visible` "como no anulada; anulación real vía Estado si se agrega" — esa columna `Estado` **nunca se agregó**; RN-011 usa `Visible`, con el efecto colateral descrito en la sección 4 |
| Adjuntos_HistoriaClinica | Sí | Estructura sí, pero **sin ningún endpoint que la use** | Modelo/DTO/repo existen; no hay controlador de subida de archivos — tabla sin vía de entrada |
| Facturacion | Sí | Sí, con `ID_Arrendamiento` nunca poblado | Columna existe y está en el modelo, pero el service nunca la setea (sección 2.2) |
| Liquidacion_Medica | Sí | Sí, exacta | — |
| Pago_Facturacion | Sí (columna del schema legado) | **Eliminada del modelo JPA en Sprint 2** — sin entidad ni repositorio | Confirmado sin ningún caller fuera de sus propios archivos antes de eliminarla. El flujo de pagos parciales/comprobante no se implementa; `Cobros` cubre el caso real 1:1 usado en producción. La tabla queda tal cual en `docs/schema.sql` (fuente de verdad del schema real), solo se retiró el mapeo JPA que no se usaba |
| Reporte_Generado | Sí | Estructura sí, **sin ninguna entidad JPA, repositorio ni uso** | **No implementado** — ya estaba listado como pendiente de auditar en el Anexo; se confirma como no implementado |
| Dashboard_Gerencial | Sí, pero **con estructura distinta a la propuesta** | **No coincide entre las 3 fuentes** | **Desvío nuevo, sin documentar**: `docs/schema.sql` define un modelo genérico de indicador (`ID_Indicador, Nombre_Indicador, Categoria, Valor, Fecha_Calculo, Periodo`), mientras que la sección 10 de la propuesta y la entidad JPA real (`model/DashboardGerencial.java`) coinciden entre sí en un modelo "precalculado por día" (`ID_Dashboard, Fecha, Total_Turnos_Dia, Turnos_Atendidos, ...`). El código implementa el modelo de la propuesta, **no** el de `docs/schema.sql` — si `docs/schema.sql` se usara para levantar una base nueva, la app fallaría al arrancar (`ddl-auto: validate`) |
| Consultorio | Sí | Sí, exacta | En la práctica `Estado` queda fijo en `DISPONIBLE` desde el alta (sin endpoint de modificación), ver sección 3 |
| Arrendamiento_Modulo | Sí | Sí, incluyendo el desvío confirmado de `Dia_Semana` (Anexo #5) | — |
| Uso_Consultorio | Sí | Estructura sí, **sin caller desde el frontend** | El endpoint `POST /arrendamientos/uso` existe pero nada lo invoca — código alcanzable solo por API directa |
| Cierre_Diario | Sí | Sí, con `ID_Arrendamiento` nunca poblado | Mismo patrón que Facturacion.ID_Arrendamiento — la FK existe pero el builder no la completa (sección 2.2) |

**Nota adicional (ya documentada en el Anexo, no es un hallazgo nuevo):** `src/main/resources/db/schema.sql` está desactualizado respecto a `docs/schema.sql` y no se ejecuta en runtime (`spring.sql.init.mode: never`, `ddl-auto: validate`) — es efectivamente un artefacto muerto, confirmado por el agente de Reportes al revisar `application.yml`.

---

## 6. Plan de acción priorizado

### Bloqueantes (rompen un flujo core o una regla de negocio de seguridad)

1. **Corregir IDOR en Arrendamiento/Liquidaciones/Turnos** — un usuario con rol MEDICO puede leer (y en Turnos, escribir) datos de otro médico cambiando un ID en la URL. Módulos: Arrendamiento, Facturación, Turnos. Archivos: `ArrendamientoController.java:33-37`, `LiquidacionController.java:35-39`, `TurnoController.java:30-37,45-54`. Aplicar el patrón ya existente en `MedicoController.java:40-44` (resolver el médico desde el `Authentication`, ignorar/validar el `idMedico` del path contra el propio). Esfuerzo: **M**.
2. **Aplicar RN-010 también en `anularEvolucion`** — cualquier médico puede hoy anular evoluciones clínicas ajenas. Módulo: Historias Clínicas. Archivo: `HistoriaClinicaServiceImpl.java:110-131`. Esfuerzo: **S**.
3. **Corregir el enmascarado de Historias Clínicas para GERENTE/ADMINISTRATIVO** — hoy ven el texto clínico completo pese a que la tabla de permisos dice "No"/"Parcial". Archivo: `HistoriaClinicaServiceImpl.java:133-134,156-159`. Esfuerzo: **M**.
4. **Corregir condición de carrera en `reservarTurno`** — dos reservas concurrentes sobre el mismo turno pueden violar RN-001/002/003 bajo carga real. Módulo: Turnos. Archivo: `TurnoServiceImpl.java:81-118`. Agregar `@Version` (optimistic locking) a `Turno` o lock pesimista en la query de lectura. Esfuerzo: **M**.
5. **Corregir pérdida de datos monetarios en Facturación** — `importeOs` (frontend) vs `importeCubiertoOs` (DTO) nunca matchean, el copago cubierto por obra social se pierde silenciosamente; `registrarCobro` fuerza siempre `PAGADO` sin importar el método de pago. Archivos: `facturacion.js:70`, `RegistrarCobroDTO.java:17`, `FacturacionServiceImpl.java:88`. Esfuerzo: **S**.
6. **Corregir RN-012 (string `'NO ASISTIO'` vs `NO_ASISTIO`)** — turnos no asistidos bloquean indebidamente la baja de un médico. Archivo: `MedicoRepository.java:19`. Esfuerzo: **S**.
7. **Reservar turnos: recuperar tipo de consulta/método de pago/OS/copago del formulario** — hoy se cargan en la UI y se descartan silenciosamente por Jackson (campos ausentes en `ReservarTurnoDTO`). Archivos: `turnos.js:318-338`, `ReservarTurnoDTO.java`. Esfuerzo: **M**.

### Refactors de modularidad (priorizando "con riesgos" detectados en la sección 2)

**Sprint 2 — completado (2026-08-07).** Los ítems 8-14 están resueltos. `mvn clean test` corrido al final: 76/76 tests OK.

8. ✅ **Historias Clínicas** — resuelto como efecto colateral del Sprint 1 (no requirió trabajo adicional en Sprint 2): `HistoriaClinicaServiceImpl.validarAutoriaMedico()` ya es un método privado único usado por `editarEvolucion` y `anularEvolucion`, y `mapEvolucionToDTO()` ya centraliza el enmascarado por rol.
9. ✅ **Facturación** — `Pago_Facturacion` **eliminado formalmente** (entidad + repositorio; sin caller previo fuera de sus propios archivos, confirmado antes de borrar). `FacturacionServiceImpl` ahora resuelve el contrato de arrendamiento vigente del turno (médico + consultorio + día/horario, mismo criterio que la generación automática de turnos, en sentido inverso — ver `ArrendamientoModuloRepository.findContratoVigente`) y usa `arrendamiento.porcentajeMedico/porcentajeConsultorio` en vez de constantes hardcodeadas, con fallback a 70/30 si no hay contrato vigente. `Facturacion.idArrendamiento` queda poblado. Se extrajo `SplitFinancieroCalculator` (`util/SplitFinancieroCalculator.java`) como única fuente del cálculo de split, migrando también `LiquidacionServiceImpl` a usarlo.
10. ✅ **Reportes** — `ReporteServiceImpl` ya no usa `findAll()` en ningún método: se agregaron `FacturacionRepository.findByFechaFacturacionBetween`, `PacienteRepository.countByFechaCreacionBetween` y `UsoConsultorioRepository.findByFechaBetween` para acotar por fecha a nivel de query; el agrupamiento por médico/obra social/consultorio se mantiene en memoria pero sobre listas ya acotadas al rango de fechas pedido, no sobre la tabla completa. **Desvío documentado:** el split 30/70 hardcodeado en `reporteUsoConsultorios()` queda sin conectar a `ArrendamientoModulo` — un registro de uso agrupa múltiples contratos posibles en el tiempo, a diferencia de una Facturación (1 turno = 1 contrato vigente puntual), y resolverlo correctamente excedía el alcance de este ítem. Nuevo `ReporteServiceTest.java` con 3 tests.
11. ✅ **Arrendamiento** — separados `UsoConsultorioService`/`UsoConsultorioServiceImpl` (registrarUso) y `CierreDiarioService`/`CierreDiarioServiceImpl` (generarCierreDiario) de `ArrendamientoServiceImpl`, que queda acotado a gestión de contratos (5 métodos, 4 dependencias en vez de 7). `ArrendamientoController` inyecta los 3 services; `/api/arrendamientos/medico/{idMedico}` (guard de IDOR de Sprint 1, `medicoAccessGuard.verificarAccesoPropio`) no se tocó — tests de regresión (`ArrendamientoControllerTest`) corridos en verde después del refactor. `CierreDiario.idArrendamiento` ahora se puebla (mismo criterio de matching que Facturación, a nivel de día completo vía `findContratoVigentePorFecha`) y `CierreDiarioServiceImpl` usa `SplitFinancieroCalculator` — **desvío respecto a lo propuesto:** en vez de que `CierreDiario` genere o se vincule a una `LiquidacionMedica` (cardinalidad distinta: diario vs. por período), ambos comparten únicamente el cálculo de split, que ya no puede divergir entre sí. Nuevos `UsoConsultorioServiceTest.java` y `CierreDiarioServiceTest.java`.
12. ✅ **Turnos** — `buildTurnoAcciones()` eliminado; `buildTurnoAccionesCompacto()` ahora incluye Cancelar (sobre `RESERVADO`) y No Asistió (sobre `EN_ESPERA`) con el mismo estilo ícono-compacto que ya usaba.
13. ✅ **Médicos** — `loadMisDatos()`/`loadMisPrestaciones()` movidas de `app.js` a `medicos.js`; el router de `app.js` solo invoca las funciones (`medicos.js` ya cargaba antes que `app.js` en `app.html`, sin cambios de orden necesarios).
14. ✅ **Facturación/Liquidación** — numeración RN-005/RN-006 corregida en comentarios, mensajes de excepción y nombres de test (`FacturacionServiceTest`, `LiquidacionServiceTest`), verificado contra `docs/MediSpace_Propuesta_Tecnica_y_BD (1).md` (RN-005 = no liquidar con turnos sin cobro; RN-006 = split 70/30 a nivel médico) antes de aplicar el swap.

### Importantes (funcionalidad de la propuesta ausente o incompleta)

15. **Implementar el módulo de gestión de Usuarios** — alta directa (Gerente/Administrativo), baja lógica con reactivación, modificación con restricción de autocambio de rol, consulta con filtros, pantalla dedicada. Hoy `UsuarioController` está vacío. Archivos: `UsuarioController.java`, `UsuarioService(Impl).java`, nuevo `usuarios.js`. Esfuerzo: **L**.
16. **Implementar subida de adjuntos en Historias Clínicas** — el modelo existe, falta el controlador/endpoint de `MultipartFile` y la UI de carga. Archivos: nuevo endpoint en `HistoriaClinicaController.java`, `historias.js`. Esfuerzo: **M**.
17. **Reactivación de bajas lógicas** (Pacientes, Médicos) — hoy `@SQLRestriction("Visible = 1")` bloquea el acceso incluso para reactivar; agregar query nativa de bypass + endpoint de reactivación + checkbox "Incluir inactivos" en ambos módulos. Archivos: `PacienteServiceImpl.java`, `MedicoServiceImpl.java`, `pacientes.js`, `medicos.js`, `app.html`. Esfuerzo: **M**.
18. **Restringir "Pacientes" para MEDICO a solo pacientes vinculados** (turnos/HC propios), en vez de `findAll()`. Archivo: `PacienteServiceImpl.listarPacientes()`. Esfuerzo: **M**.
19. **Habilitar acceso real a Facturación propia para MEDICO** — hoy el ítem de menú "Mis Liquidaciones" produce 403. Agregar endpoint de facturación filtrado por médico autenticado y conectarlo a la UI, o redirigir el ítem del menú a Liquidaciones. Archivos: `FacturacionController.java`, `app.js`. Esfuerzo: **M**.
20. **Habilitar exportación de reportes para ADMINISTRATIVO** en Facturación (contradice la tabla de permisos de la sección 4.6 tal como está hoy). Archivo: `ReporteController.java:40,49,58`. Esfuerzo: **S**.
21. **Agregar endpoint de modificación de estado de Consultorio** (Bloqueado/En mantenimiento/Fuera de servicio) — hoy imposible de alcanzar, lo que a su vez impide que `crearArrendamiento` pueda validar "consultorio bloqueado". Archivos: `ConsultorioController.java`, `ConsultorioService(Impl).java`. Esfuerzo: **M**.
22. **Prevenir doble asignación del mismo médico a dos consultorios simultáneos** en Arrendamiento — hoy `countSuperposicionesConsultorio` solo filtra por consultorio, no por médico. Archivo: `ArrendamientoModuloRepository.java:19-33`. Esfuerzo: **S**.
23. **Corregir campo `Estado` de Pacientes** que se envía desde el frontend pero nunca se persiste (`PacienteUpdateDTO` no lo tiene). Archivos: `PacienteUpdateDTO.java`, `PacienteServiceImpl.java:91-98`. Esfuerzo: **S**.
24. **Conectar `Uso_Consultorio`/`Cierre_Diario` a una UI real** (hoy alcanzables solo por API directa). Archivo: `arrendamientos.js`. Esfuerzo: **M**. *(La FK `ID_Arrendamiento` en `CierreDiario` ya se completa — resuelto en Sprint 2, ítem 11, ahora en `CierreDiarioServiceImpl.java`. Queda pendiente solo la UI.)*
25. **Alinear `docs/schema.sql` con la entidad JPA real de `Dashboard_Gerencial`** (hoy divergen — riesgo de romper el arranque con `ddl-auto: validate` si se usa `schema.sql` para levantar una base nueva). Esfuerzo: **S**.

### Menores (pulido, casos borde, deuda técnica)

26. Agregar filtro por Obra Social y Teléfono en la consulta de Pacientes y Médicos (backend + frontend). Esfuerzo: **S**.
27. Agregar indicadores "cantidad total del día"/"cantidad pendientes" en la Agenda Médica de Turnos, y filtro por estado (Todos/Pendientes/En Espera/Atendidos/No Asistió). Esfuerzo: **M**.
28. Quitar/corregir el campo "Teléfono" fantasma en el formulario de Médicos (se captura en `medicos.js` pero no existe en el modelo). Esfuerzo: **S**.
29. Corregir el bug de nombre de campo `ev.fechaEvolucion` (debería ser `ev.fechaHora`) en `historias.js:68`, que hace que la fecha de toda evolución se muestre como "—". Esfuerzo: **S**.
30. Agregar el ítem `arrendamientos` al `NAV_CONFIG.MEDICO` en `app.js` (el backend ya lo permite, falta la entrada de sidebar). Esfuerzo: **S**.
31. Agregar auditoría de lectura de Historias Clínicas (usuario que accedió, fecha/hora) — hoy solo hay rastro parcial de autoría de escritura, ningún registro de lectura, según lo previsto en el Anexo como pendiente. Esfuerzo: **L**.
32. Implementar exportación de reportes a Excel/impresión (sección 4.7) — confirmado como no implementado en absoluto (sin dependencias de Apache POI/iText en `pom.xml`). Esfuerzo: **L**.

---

### Ítems que requieren verificación manual (no confirmables solo con lectura de código)

- Ejecución real de `mvn test` sobre la suite completa (esta auditoría solo corrió `mvn clean compile`, que pasó).
- Comportamiento bajo concurrencia real de la condición de carrera de `reservarTurno` (el hallazgo es por inspección de código, no por test de carga).
- Si `Uso_Consultorio`/`Cierre_Diario`/`POST /arrendamientos/uso` son invocados hoy por algún cliente externo a la UI web (Postman, integración futura) — el hallazgo de "código muerto" es respecto al frontend actual, no descarta otros consumidores no auditados.
