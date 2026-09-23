# Anexo — Desvíos y decisiones respecto a la Entrega del Primer Cuatrimestre

**Documento base:** "Entrega primer cuatrimestre Policaro" (Estudio de Prefactibilidad + Informe de Relevamiento + Propuesta Comercial + Propuesta Técnica + Base de Datos), presentada 12/03/2026 – 26/04/2026.

Este anexo se agrega **sin modificar el documento original** — todo lo que sigue son decisiones de producto y correcciones tomadas durante el desarrollo, posteriores a la entrega. Se agrupa en trece partes: (A) lo ya confirmado en sesiones anteriores a esta, (B) lo de la sesión 2026-08-23 (Obras Sociales, RN-016 a RN-018, decisiones de permisos M1-M5), (C) lo de la sesión 2026-08-27 (incidente en producción y las dos reglas que lo previenen), (D) lo de la ronda de correcciones sobre el checklist de verificación en vivo del 27/08/2026, (E) lo de la sesión 2026-09-01 (cancelación de turnos y cancelación de un día completo de la agenda de un médico), (F) lo de la sesión 2026-09-02 (Dashboard Gerencial: se reprodujo y corrigió la causa raíz que quedó abierta en §D.2.4), (G) lo de la sesión 2026-09-02 (el médico gestiona su propia cartilla de prestaciones y obras sociales — re-alinea RF-M3/RF-M4 con el relevamiento original), (H) lo de la sesión 2026-09-02 (la factura automática toma el precio de la prestación elegida en vez del importe de consulta fijo del médico), (I) lo de la sesión 2026-09-02 (el identificador del paciente se rotula "DNI / Pasaporte" para contemplar extranjeros — re-alinea con la Entrega §2), (J) lo de la sesión 2026-09-09 (correcciones de la verificación en vivo completa: re-facturación de un cupo reasignado tras una cancelación, control de pertenencia del médico al leer Historias Clínicas y descargar adjuntos, y consistencia de fin-de-día en dos reportes), (K) lo de la sesión 2026-09-23 (suite de QA end-to-end: 4 bugs corregidos — RN-022 a RN-024 nuevas y el fix de facturas anuladas sumando de más en Liquidación/Cierre/Reportes), (L) lo de la sesión 2026-09-23 (el copago se autocompleta desde la cartilla de obras sociales del médico al reservar un turno, en vez de cargarse como texto libre), y (M) lo de la misma sesión 2026-09-23 (corrección de la fórmula de la Parte L tras validar el modelo de negocio real — el coseguro es lo que la obra social le paga al médico, no lo que paga el paciente — y propagación a Liquidación, Cierre de Caja y Reportes con la RN-025 nueva).

---

## Parte A — Desvíos confirmados en sesiones previas

| # | Ítem original | Decisión confirmada | Motivo |
|---|---|---|---|
| 1 | Rol Paciente con login de solo lectura | **Eliminado por completo.** Paciente es solo un registro de datos (alta/edición por Gerente/Administrativo), sin usuario ni contraseña ni pantallas propias. | Decisión de producto |
| 2 | Módulo de agenda independiente + botón manual "Generar Turnos" | **Eliminado.** Los turnos se generan automáticamente al crear un Contrato de Arrendamiento (médico + consultorio + días + horarios); esa es la única fuente de la agenda de un médico. | Unificar dos conceptos que se superponían |
| 3 | Acceso a Reportes no restringido explícitamente para Administrativo | **Restringido a solo GERENTE** en los 5 endpoints de `/api/reportes/*`. | Decisión de producto (ver también Parte B, ítem donde se reconfirma esta decisión) |
| 4 | Alta/baja de Contratos de Arrendamiento: Gerente y Administrativo | **Restringido a solo GERENTE.** | Decisión de producto (ver también Parte B) |
| 5 | RN-013 (superposición de consultorio) valida solo consultorio + horario | **Valida también por día de la semana** (columna `Dia_Semana` en `Arrendamiento_Modulo`, no contemplada en el modelo original). | El modelo original generaba falsos positivos de "ocupado" al no distinguir días |
| 6 | Relación médico–prestación como columna suelta en `Medicos` (1 prestación por médico) | **Corregido a relación real N:M** vía tabla `Medico_Prestacion`, con importe y duración propios por médico. | Error de modelado del documento original — la propuesta pedía múltiples prestaciones por médico pero el esquema solo permitía una |
| 7 | Tipografía: Inter | **Source Serif 4** (marca/títulos) + **IBM Plex Sans** (UI) + **IBM Plex Mono** (datos tabulares). Se mantiene la paleta de colores exacta del documento original. | Decisión de diseño |
| 8 | Alta de médicos: solo Gerente (matriz de permisos) | **Confirmado y reforzado** en el backend (`hasRole('GERENTE')` estricto). | Alineación con la propuesta |

---

## Parte B — Cambios y desvíos de esta sesión (2026-08-23)

### B.1 Módulo de Obras Sociales (funcionalidad nueva, no contemplada en la entrega original)

La entrega original solo registraba "obra social" como un dato de texto libre del paciente. Se agregó un **módulo completo** con:

- Catálogo propio de obras sociales (alta, baja lógica reactivable, modificación, búsqueda con filtros) — nombre único, código/sigla, plan, "requiere bono de consulta previo", observaciones.
- Selección desde catálogo (no texto libre) al asociar una obra social a un Médico o a un Paciente.
- Relación N:M médico–obra social con **coseguro propio de esa relación**, decidido por el propio médico (no un valor fijo de la obra social ni editable por Gerente/Administrativo) — replica el mismo patrón ya usado para médico–prestación.

Ver `docs/spec.md` §4.9 para el detalle funcional completo.

### B.2 Reglas de negocio nuevas: RN-016, RN-017, RN-018

La numeración original (RN-001 a RN-015) no cubre estas reglas, agregadas junto con Obras Sociales y el coseguro:

| ID | Módulo | Regla |
|---|---|---|
| RN-016 | Obras Sociales | No se da de baja una obra social con médicos activos asociados |
| RN-017 | Médicos | Un médico solo puede editar el coseguro de su propia relación con una obra social, no la de otro médico |
| RN-018 | Facturación | No se liquida si hay turnos "Atendido" sin ningún cobro asociado directamente (aunque no exista factura pendiente) |

**Corrección respecto a una nota previa:** en un registro anterior de cambios, la regla que hoy es **RN-018** había quedado anotada como "RN-016" (era la única regla nueva en ese momento). Al agregarse el módulo de Obras Sociales con su propia RN-016, ambas reglas quedaron etiquetadas igual en el código — colisión detectada y corregida hoy: RN-016 se mantiene para Obras Sociales, y la regla de facturación pasó a **RN-018**. RN-017 (ownership del coseguro) tampoco tenía número asignado en el código hasta hoy.

RN-005 y RN-018 son reglas distintas aunque de redacción parecida: RN-005 bloquea por facturas con estado `PENDIENTE`; RN-018 bloquea por turnos "Atendido" sin ningún `Cobro` asociado en absoluto (cubre el caso de que nunca se haya generado una Facturación, algo que RN-005 no detecta).

### B.3 Decisiones de permisos (M1–M5)

Auditoría de la matriz de permisos del documento original contra el backend actual; decisiones tomadas hoy con el cliente/usuario:

| Módulo | Lo que decía el documento | Decisión tomada |
|---|---|---|
| Pacientes | Acceso completo (alta/baja/edición) solo para Administrativo, sin alta para Gerente | **Gerente pasa a solo lectura** en Pacientes (antes tenía alta/baja/edición igual que Administrativo, por error de implementación) |
| Médicos / Agenda | Administrativo puede "Gestionar agenda" | Se **mantiene GERENTE estricto** para alta/baja de contratos de arrendamiento (de donde depende la agenda) — reconfirma la decisión ya registrada en Parte A, ítem 4. "Gestionar agenda" de Administrativo se limita a consulta |
| Historias Clínicas | Administrativo puede "Ver historias clínicas: Sí" (texto completo) | El código enmascaraba motivo/diagnóstico/tratamiento para Administrativo — **se corrigió**: ahora ve el contenido clínico completo, igual que Médico |
| Reportes | Administrativo tiene acceso puntual a "Turnos por fecha", "Pacientes ausentes" y "Cobros pendientes" | Se **reconfirma GERENTE estricto** en los 5 endpoints de `/api/reportes/*` (Parte A, ítem 3) — los 3 reportes puntuales del documento no son endpoints de ese módulo: ya son alcanzables por Administrativo hoy vía el módulo de Turnos (filtro por fecha/estado) y el módulo de Facturación (filtro por estado de pago) |
| Facturación (exportación) | Administrativo puede "Exportar reportes: Sí" | Sin cambios — la exportación a Excel (ver Limitaciones, abajo) no está implementada para ningún rol todavía |

---

## Parte C — Incidente en producción y reglas nuevas (sesión 2026-08-27)

### C.1 Qué pasó

`GET /api/pacientes` y `GET /api/medicos` empezaron a devolver `HTTP 500` sin ningún cambio de código de por medio — el sistema venía funcionando y un día dejó de andar. Diagnosticado levantando una segunda instancia local para capturar el stack trace real (el `GlobalExceptionHandler` lo loguea pero no lo expone en la respuesta): en ambos casos, un registro activo (un Paciente, un Médico) tenía una referencia a otro registro que había sido dado de baja (una Obra Social, un Usuario). El patrón de borrado lógico del proyecto (`@SQLRestriction("Visible = 1")`) hace que, ante una referencia así, Hibernate no pueda "encontrar" la fila dada de baja al resolverla — y como eso ocurre adentro del mapeo de **todo** el listado, una sola fila con una referencia colgante tira abajo el endpoint completo para cualquier usuario, no solo el registro afectado.

### C.2 Por qué pasó

Ninguna regla de negocio existente lo prevenía:
- RN-016 (baja de Obra Social) solo chequeaba médicos activos asociados, nunca pacientes.
- No existía ninguna regla que bloqueara dar de baja un Usuario si todavía pertenecía a un Médico activo.

### C.3 Reglas de negocio nuevas: RN-019, RN-020

| ID | Módulo | Regla |
|---|---|---|
| RN-019 | Obras Sociales | No se da de baja una obra social con pacientes activos asociados |
| RN-020 | Usuarios | No se da de baja un usuario que pertenece a un médico activo |

### C.4 Cómo se resolvió

- **Dato en producción:** reparado en caliente reactivando la Obra Social y el Usuario puntuales afectados (`PUT .../reactivar`, acción reversible — no se borró ni recreó nada).
- **Prevención:** se agregaron RN-019 y RN-020 al backend (`ObraSocialServiceImpl`, `UsuarioServiceImpl`), con test automatizado explícito para cada una (6 tests nuevos).

---

## Parte D — Correcciones sobre el checklist de verificación en vivo (2026-08-27)

Ronda de correcciones a partir de `CHECKLIST_VERIFICACION_COMPLETA_27_08.xlsx` (pruebas manuales en vivo del 27/08). Prioridad: Historias Clínicas, Reportes y Dashboard gerencial. Lo que sigue son los **cambios estructurales** (no contemplados en la Entrega) que salieron de esa ronda; los bugs corregidos que ya estaban dentro del contrato (p. ej. "no se puede cancelar un turno", "el dashboard no carga") no se listan acá porque no son desvíos.

### D.1 Historias Clínicas

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.1.1 | Interfaz de HC inconsistente entre roles; la vista de ADMINISTRATIVO no mostraba nombre/DNI del paciente | El frontend ahora usa `nombrePaciente`/`dniPaciente` que ya venían en el DTO (`HistoriaClinicaResponseDTO`) como fuente primaria, en vez de depender de un objeto paciente que a veces llegaba nulo | Bug de UI |
| D.1.2 | `agregarEvolucion` no validaba autoría (Hallazgo 4 del checklist previo) | El service ahora exige que el médico autenticado tenga al paciente **vinculado por un turno propio** (`PacienteRepository.existsVinculadoAMedico`) antes de permitir crear la evolución — mismo criterio RN-010 que ya aplicaban editar/anular/adjuntar. Además se sacó `GERENTE` del `@PreAuthorize` de ese endpoint (no es médico; el service ya fallaba al resolverlo). 2 tests nuevos. | RF-H2 ("solo la crea el médico responsable") / cierre de hueco de seguridad |
| D.1.3 | Adjuntos: ADMINISTRATIVO no tenía ninguna vía en la UI para adjuntar (solo podía el MEDICO, y a través del modal de edición de la evolución) | Se agregó un **modal de adjuntos dedicado** (`modal-adjuntos`) accesible desde cada evolución con un botón de clip, para ADMINISTRATIVO y MEDICO (RF-H3, "Adjuntar estudios: Sí" para Administrativo). Solo adjunta/descarga, no toca el texto clínico. | RF-H3 — la capacidad estaba en el backend pero no era alcanzable para Administrativo |
| D.1.4 | `historias.js` calculaba `canEdit` incluyendo GERENTE | Se alineó con el backend: `canEdit` solo para MEDICO. ADMINISTRATIVO ve el contenido completo (M3) pero no edita; GERENTE no llega (403). | Consistencia frontend/backend |

**Pendiente de decisión del cliente (ver `docs/PREGUNTAS_CHECKLIST_27_08.md`):** si ADMINISTRATIVO/GERENTE deben poder ver la HC completa (hoy: ADMIN sí, GERENTE 403), y si la spec exige un listado de todas las HC (hoy no hay).

### D.2 Reportes y Dashboard Gerencial

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.2.1 | "Descargar los reportes en formato .csv" (pedido nuevo) | Exportación a **CSV** de los 3 reportes, armada en el cliente a partir de los datos ya cargados (botón "Descargar CSV" en cada resultado). Separador `;`, BOM UTF-8 para que Excel abra las tildes bien. **No** hay endpoint de exportación en el backend — RF-R3 (exportación a Excel/impresión) sigue sin implementarse server-side. | Pedido de negocio del 27/08. La Entrega pedía Excel; se entrega CSV como primera iteración (abre en Excel igual). |
| D.2.2 | "Uso de Consultorios no funciona" (reporte siempre vacío) | El reporte se rearmó para calcularse desde las **Facturaciones** del período (cada una = 1 turno atendido, con su consultorio y los porcentajes ya resueltos del contrato vigente), en vez de leer la tabla `Uso_Consultorio` — que solo se llena vía un endpoint que no tiene ningún botón en la UI, por eso el reporte salía siempre vacío. | Bug: la fuente de datos elegida originalmente nunca se poblaba en el flujo real |
| D.2.3 | `reporteUsoConsultorios` usaba un split 70/30 **hardcodeado** | Al pasar a calcularse desde Facturaciones, ahora usa los **porcentajes reales** de cada factura (`porcentajeMedico`/`porcentajeConsultorio`, que ya se habían resuelto del contrato de arrendamiento vigente al facturar). Las columnas del reporte pasan de "Consultorio (30%)" / "Médico (70%)" a "Parte Consultorio" / "Parte Médico". | Consistencia con Liquidación/Facturación para el mismo período |
| D.2.4 | Dashboard gerencial "no carga nada" | Endurecimiento: `getDashboardHoy`/`recalcularDashboard` ahora toleran filas duplicadas para la misma fecha (`findFirstByFechaOrderByIdDashboardDesc` en vez de un `findByFecha` que reventaba con `NonUniqueResultException` y tiraba abajo todo el dashboard). `getDashboardHoy` pasó a `@Transactional`. **Nota:** no se pudo reproducir la causa raíz sin la base viva — ver `docs/PREGUNTAS_CHECKLIST_27_08.md`. | Robustez / diagnóstico |
| D.2.5 | El `GlobalExceptionHandler` logueaba los 500 pero no los exponía (ver §C.1) | Los 500 ahora incluyen un campo `detail` en la respuesta con el tipo de excepción y su causa raíz (nombre de clase + mensaje, sin datos sensibles), y el frontend lo muestra en el toast. | Poder diagnosticar fallas 500 sin levantar una segunda instancia a leer el stack trace |

### D.3 Usuarios

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.3.1 | ADMINISTRATIVO podía dar de alta usuarios con cualquier rol (incluido Gerente) | Regla nueva: **un ADMINISTRATIVO solo puede crear usuarios con rol Administrativo**. Solo un GERENTE crea otros GERENTE. Validado en `UsuarioController.crearUsuario` (backend) y ocultando la opción "Gerente" del alta cuando el actor es Administrativo (frontend). | Pedido de negocio del 27/08. La Entrega decía "alta de usuario por Administrativo/Gerente" sin restringir el rol asignable. |

### D.4 Médicos

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.4.1 | No se podía dar de baja a un médico aunque no tuviera ningún turno con paciente — RN-012 contaba también los turnos DISPONIBLE | Se aclara la interpretación de RN-012 ("turnos futuros sin reasignar"): **solo bloquean los turnos futuros con paciente real asignado** (`RESERVADO` / `EN_ESPERA`). Los turnos `DISPONIBLE` (cupo generado automáticamente por el contrato de arrendamiento, sin paciente) no bloquean — si bloquearan, ningún médico con contrato activo podría darse de baja nunca. | Bug de implementación de RN-012, no un cambio de la regla |
| D.4.2 | `GET /api/medicos/{id}`, `.../{id}/prestaciones`, `.../{id}/obras-sociales` — un MEDICO podía leer datos de OTRO médico, incluidos coseguros pactados (Hallazgo 1) | Se agregó el guard de pertenencia (`MedicoAccessGuard.verificarAccesoPropio`): un MEDICO solo consulta su propio id; GERENTE/ADMINISTRATIVO sin cambios. | Constitución §3 ("un médico solo accede a lo propio") |

### D.5 Turnos

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.5.1 | `GET /api/turnos/{id}` — un MEDICO podía leer un turno ajeno por id (Hallazgo 2) | Mismo guard de pertenencia que ya tenía `PUT /api/turnos/{id}/estado`. | Constitución §3 |
| D.5.2 | La UI de turnos no ofrecía "Cancelar" sobre un turno EN_ESPERA (solo sobre RESERVADO) | Se agregó la acción "Cancelar turno" también en EN_ESPERA (RF-T3 / RN-004: solo ATENDIDO es inmutable). Los botones llevan `aria-label`. | Bug de UI |

### D.6 Facturación

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.6.1 | Había métodos de pago habilitados que no correspondían (Tarjeta Débito/Crédito, "Obra Social" como método) | Los **únicos métodos de pago válidos** son ahora **EFECTIVO** y **TRANSFERENCIA**, en el registro de cobro y en la reserva. Validado en `FacturacionServiceImpl.registrarCobro` (backend) y en los dos `<select>` (frontend). La cobertura de obra social se sigue registrando aparte (campo obra social + copago), no como "método de pago". | Pedido de negocio del 27/08 |
| D.6.2 | Con Transferencia, el cobro "nunca se registraba" (en realidad se registraba, pero la factura quedaba PENDIENTE igual que si no hubiera cobro, sin ninguna señal visual) | El cobro por transferencia **sí se registra** (siempre lo hizo). Ahora el frontend distingue "PENDIENTE sin cobro" de "TRANSFERENCIA A VALIDAR" (badge naranja + columna "Método"), y hay un endpoint nuevo `PUT /api/facturacion/{id}/confirmar-transferencia` que valida el ingreso y pasa la factura a PAGADO. | Sección 4.6 ("las transferencias quedan pendientes hasta validación") — faltaba el paso de validación explícito |

### D.7 Arrendamiento

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.7.1 | Como MEDICO se veían los contratos de todos los médicos con sus porcentajes (Hallazgo 3) | `GET /api/arrendamientos/contratos`: para el rol MEDICO se siguen devolviendo todos los contratos (necesita ver la disponibilidad de los consultorios), pero con los **porcentajes y las observaciones enmascarados** en las filas que no son propias. | "Con ver la disponibilidad alcanza" (checklist 27/08) |
| D.7.2 | El botón "Dar de baja" de un contrato aparecía para el rol MEDICO (y siempre fallaba) | El botón solo se muestra para GERENTE (que es el único rol que puede darlo de baja; el backend ya lo exigía). | UX |

### D.8 Consultorios

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| D.8.1 | Cambiar el estado de un consultorio (Bloqueado/Mantenimiento/Fuera de servicio) no avisaba si había turnos pendientes en ese consultorio | El cambio de estado **se sigue permitiendo siempre** (no se bloquea), pero si hay turnos futuros con paciente (RESERVADO/EN_ESPERA) en ese consultorio, la respuesta trae un campo `advertencia` y el frontend lo muestra como aviso naranja. | Pedido de negocio del 27/08 ("mostrar un mensaje de aviso, pero permitir igual el cambio") |

### D.9 Resumen de permisos que cambian respecto a la matriz vigente

| Módulo | Antes (Parte B / matriz) | Ahora |
|---|---|---|
| Usuarios | Alta por Administrativo con cualquier rol asignable | Administrativo solo puede asignar rol Administrativo al dar de alta (D.3.1) |
| Historias Clínicas — crear evolución | `GERENTE` + `MEDICO` en el `@PreAuthorize` | Solo `MEDICO`, y solo sobre pacientes propios (D.1.2) |
| Médicos — `GET /{id}` y sub-recursos | Lectura para los 3 roles sin ownership | MEDICO restringido a su propio id (D.4.2) |
| Turnos — `GET /{id}` | Lectura para los 3 roles sin ownership | MEDICO restringido a turnos propios (D.5.1) |
| Arrendamiento — `GET /contratos` | MEDICO veía todo, con porcentajes | MEDICO ve todo pero sin porcentajes ajenos (D.7.1) |
| Facturación — métodos de pago | EFECTIVO, TARJETA_DEBITO, TARJETA_CREDITO, TRANSFERENCIA, OBRA_SOCIAL | Solo EFECTIVO y TRANSFERENCIA (D.6.1) |

### D.10 Cambios de API (nuevos endpoints / firmas)

- **Nuevo:** `PUT /api/facturacion/{id}/confirmar-transferencia` (GERENTE, ADMINISTRATIVO) — valida una transferencia pendiente y pasa la factura a PAGADO.
- `GET /api/medicos/{id}`, `GET /api/medicos/{id}/prestaciones`, `GET /api/medicos/{id}/obras-sociales`, `GET /api/turnos/{id}`, `GET /api/arrendamientos/contratos`: sin cambio de contrato REST, pero ahora aplican control de pertenencia para el rol MEDICO.
- Respuesta de `PUT /api/consultorios/{id}/estado`: puede incluir el campo `advertencia` (string, opcional).
- Respuesta de cualquier `HTTP 500`: incluye ahora un campo `detail` (string) con el tipo de excepción.

Ninguno de estos cambios toca el esquema de base de datos (`docs/schema.sql`) — todos usan columnas y tablas ya existentes.

---

## Parte E — Turnos (cancelación, días del médico) y Facturación (filtro + cobro anticipado) (sesión 2026-09-01)

Huecos detectados en Turnos y Facturación durante pruebas en vivo. Incluye un **cambio de flujo** en Facturación (§E.6): el cobro ahora se puede registrar con el paciente en sala de espera, antes de que el médico marque *Atendido*.

### E.1 Turnos — el botón "Cancelar turno" no se veía en el tablero

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| E.1.1 | En el tablero de turnos, la acción "Cancelar turno" (y en `EN_ESPERA` también "No Asistió") no aparecía / no se podía clickear, aunque el código del botón existía | La columna de acciones de la tabla estaba fijada en **30px** (`table-layout: fixed`) mientras cada botón-ícono mide 32px; con `overflow: hidden` en la columna, los íconos que no entraban se recortaban y el último (Cancelar) quedaba fuera de la vista. Se rediseñó la celda de acciones: contenedor flex (`.turno-acciones`), íconos compactos de 24px solo en esa columna y anchos de columna rebalanceados (acción 30→76px, hora 52→46px, teléfono 70→58px, dentro de los 300px fijos de la columna). | Bug de UI (CSS) |

### E.2 Turnos — "Cancelar día del médico" (funcionalidad nueva)

No existía forma de cancelar la agenda de un médico para un día completo (p. ej. vacaciones o licencia); había que cancelar turno por turno.

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| E.2.1 | Cancelación masiva por día / rango | Endpoint nuevo `POST /api/turnos/cancelar-dia` (body: `idMedico`, `fecha`, `fechaHasta` opcional para un rango de vacaciones, `motivo` opcional). Cancela en lote los turnos **futuros** de ese médico en el período: los `DISPONIBLE` y los `RESERVADO`/`EN_ESPERA` pasan a `CANCELADO`. Devuelve los contadores y la **lista de pacientes a contactar** (nombre + teléfono + fecha/hora) para los turnos que tenían paciente. | Pedido de negocio del 01/09 (vacaciones/licencia del profesional) |
| E.2.2 | Turnos que no se tocan | Los `ATENDIDO` y los ya cerrados (`CANCELADO`/`NO_ASISTIO`) quedan intactos (RN-004); los turnos con `Fecha_Hora` ya transcurrida tampoco se modifican. Regla nueva **RN-021**. | Consistencia con RN-004 |
| E.2.3 | Permisos | `GERENTE` y `ADMINISTRATIVO` pueden cancelar el día de cualquier médico. Un `MEDICO` puede cancelar **solo días propios**: su `idMedico` se resuelve desde el JWT y se ignora el que venga en el body (mismo criterio que `GET /api/turnos`). | Autogestión del profesional + Constitución §3 |
| E.2.4 | Frontend | Botón "Cancelar día del médico" en el encabezado de cada columna del tablero de turnos → modal con médico (fijo), fecha desde / hasta y motivo. Al confirmar, si había pacientes con turno se muestra la lista para contactarlos. | — |
| E.2.5 | Motivo | El `motivo` es informativo: viaja en la respuesta (toast / lista de contacto) pero **no se persiste** — la tabla `Turnos` no tiene columna para eso. Mejora futura opcional: columna `Motivo_Cancelacion`. | Alcance / sin cambio de esquema |

### E.3 Reglas de negocio nuevas: RN-021

| ID | Módulo | Regla |
|---|---|---|
| RN-021 | Turnos | Cancelar un día (o rango) de la agenda de un médico solo afecta turnos futuros del período: los `DISPONIBLE` y los `RESERVADO`/`EN_ESPERA` pasan a `CANCELADO`; los `ATENDIDO` y los ya cerrados no se modifican (RN-004). Los pacientes de turnos reservados se listan para contacto manual. |

### E.4 Facturación — filtro por fecha en el listado

El listado de Facturación traía **todas** las facturaciones (`findAll()`, una fila por atención facturada) con solo un buscador de texto por nombre. Crecía sin límite y era difícil de usar.

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| E.4.1 | Filtro por fecha (server-side) | `GET /api/facturacion` ahora acepta `?desde=YYYY-MM-DD` (un día) o `?desde=...&hasta=...` (rango inclusivo). Sin parámetros devuelve todas. Reusa `FacturacionRepository.findByFechaFacturacionBetween` (ya existía para Reportes). | Pedido de negocio del 01/09 ("hay mucha información junta") |
| E.4.2 | Frontend | Barra de fecha igual a la de Turnos (Hoy · ‹ › · calendario), botón "Rango…" para un período, y "Ver todas". **Arranca filtrando por el día de hoy.** | Consistencia con Turnos |
| E.4.3 | Filtro por Estado de Pago | Dropdown client-side combinable con la fecha: Pendiente (sin cobro) / Transferencia a validar / Pagado / Parcial / Anulado / Reintegrado. "Transferencia a validar" es el sub-estado `estadoPago=PENDIENTE + metodoPago=TRANSFERENCIA` que la UI ya distinguía. | Pedido de negocio del 01/09 |

### E.5 GlobalExceptionHandler — parámetro de query mal formado → 400

Un parámetro de fecha inválido en la URL (p. ej. `?desde=ayer`) caía en el handler genérico y devolvía **500**. Se agregó un handler para `MethodArgumentTypeMismatchException` que responde **400** con un mensaje claro. Aplica a todos los endpoints con parámetros tipados (Turnos, Reportes, Facturación).

### E.6 Facturación — cobro anticipado (paciente en sala de espera) — **cambio de flujo**

**Flujo original (Entrega):** RF-F1 — "Al pasar un turno a *Atendido* se genera automáticamente un registro de facturación pendiente". El cobro solo se podía registrar **después** de que el médico marcara *Atendido*.

**Flujo nuevo (pedido de negocio del 01/09):** el circuito real del consultorio es: llega el paciente → la administración lo pone *En Espera* y **le cobra ahí mismo** → recién después el médico lo marca *Atendido*. Para soportarlo:

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| E.6.1 | La factura pendiente se genera al pasar el turno a **`EN_ESPERA`** (además de `ATENDIDO`) | `TurnoServiceImpl.cambiarEstadoTurno` llama `crearFacturacionAutomatica` cuando el turno pasa a `EN_ESPERA` **o** a `ATENDIDO`. | Permitir cobrar mientras el paciente espera |
| E.6.2 | `crearFacturacionAutomatica` es **idempotente** | Si el turno ya tiene una facturación (`findByTurnoIdTurno`), la devuelve sin crear otra. Así marcar *Atendido* después de *En Espera* no duplica la factura. | Evitar duplicados |
| E.6.3 | Botón **"Registrar cobro"** ($) en el turno `EN_ESPERA` del tablero | `TurnoResponseDTO` ahora trae `idFacturacion` y `estadoPagoFacturacion` (solo se resuelven para turnos `EN_ESPERA`/`ATENDIDO`). El botón abre el mismo modal de cobro (`abrirCobroDesdeTurno`); si ya se cobró muestra `$✓`. El módulo Facturación sigue funcionando igual (ahora las facturas aparecen desde que el paciente está *En Espera*). | Cobrar sin cambiar de pantalla |
| E.6.4 | Anti doble-cobro | `registrarCobro` rechaza si la factura ya está `PAGADO` o `ANULADO`. | El botón queda más expuesto |
| E.6.5 | Cancelar / "No Asistió" un turno que ya tenía factura | `FacturacionService.anularFacturacionDeTurno`: la factura pasa a `ANULADO` (con observación). Si ya había un cobro, se marca que hay un **reintegro manual pendiente** (el registro de `Cobro` se conserva). Se dispara desde `cambiarEstadoTurno` y desde `cancelarDiaMedico`. | Una factura huérfana en `PENDIENTE` bloquearía la liquidación del médico (RN-005) |

**Sin cambio de esquema.** La numeración RF-F1 se mantiene; su alcance ahora incluye `EN_ESPERA` (ver `docs/spec.md`).

### E.7 Cambios de API

- **Nuevo:** `POST /api/turnos/cancelar-dia` (GERENTE, ADMINISTRATIVO, MEDICO — este último solo su propia agenda). Body `CancelarDiaMedicoDTO`; respuesta `CancelacionDiaResponseDTO` (contadores + `pacientesAContactar`).
- **Cambio:** `GET /api/facturacion` acepta ahora `?desde` y `?hasta` (opcionales, `LocalDate` ISO). Sin ellos, comportamiento anterior (todas).
- **Cambio:** `TurnoResponseDTO` incorpora `idFacturacion` y `estadoPagoFacturacion` (nullables).
- **Cambio de comportamiento:** `PUT /api/turnos/{id}/estado` con `nuevoEstado=EN_ESPERA` genera la facturación pendiente; con `CANCELADO`/`NO_ASISTIO` la anula si existía.
- Respuesta de un parámetro de query con formato inválido: `400` en vez de `500`.
- Sin cambios en el esquema de base de datos (`docs/schema.sql`).

### E.8 Tests

`mvn test` completo el 2026-09-01: **111/111 verdes** (95 previos + 16 de la sesión 2026-09-01):
- 4 de `cancelarDiaMedico` + 3 de cobro anticipado/anulación en `TurnoServiceTest`;
- 2 de resolución de `idMedico` por rol en `TurnoControllerTest`;
- 3 de filtro por fecha + 4 de factura idempotente / anti doble-cobro / anulación en `FacturacionServiceTest`.

---

## Parte F — Dashboard Gerencial: datos desactualizados (sesión 2026-09-02)

Cierra la causa raíz que quedó abierta en **§D.2.4** ("no se pudo reproducir sin la base viva"). Con la base y movimiento real del día se reprodujo el síntoma exacto reportado por el usuario: *"no tiene data correcta de cantidad de pacientes que se atendieron, facturación, etc."*.

### F.1 El dashboard del día quedaba congelado en la primera consulta de la jornada

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| F.1.1 | `getDashboardHoy()` devolvía la fila cacheada de `Dashboard_Gerencial` para hoy si existía, y **solo recalculaba cuando no había ninguna**. La fila se crea la primera vez que alguien abre el dashboard en el día (normalmente a la mañana, con casi todo en cero) y después ya nunca se refrescaba: ni un scheduler, ni el frontend llaman a `/dashboard/recalcular` (era un *dead binding*, ver `docs/CHECKLIST_VERIFICACION_COMPLETA.md` §6). El botón "Actualizar" de la UI hace `GET /dashboard` → devolvía siempre la misma foto vieja. | Para la fecha de **hoy**, `getDashboardHoy()` **siempre recalcula** (`recalcularDashboard(LocalDate.now())`). El día en curso es información viva (turnos que se atienden, facturación, altas de pacientes durante toda la jornada); precalcularlo no tiene sentido a la escala de un consultorio (3 queries acotadas a un día). `recalcularDashboard` sigue persistiendo/actualizando la fila, así la tabla `Dashboard_Gerencial` queda como registro histórico y el endpoint `/dashboard/recalcular?fecha=` sigue sirviendo para fechas pasadas. | Bug: RF-R4 ("indicadores precalculados para no recalcular en tiempo real") se implementó como caché que nunca se invalidaba para el día actual |
| F.1.2 | `facturacionTotalDia` sumaba **todas** las facturas del día sin mirar el estado, incluidas las `ANULADO` (turno que se canceló después de generar su factura pendiente — `anularFacturacionDeTurno` la deja en `ANULADO` pero conserva el `Importe_Total`) y `REINTEGRADO`. Inflaba la "Facturación del Día" con plata que no se facturó de verdad. | El total del día ahora excluye `ANULADO` y `REINTEGRADO`. `cobrosPendientes` ya filtraba solo `PENDIENTE`, no cambia. | Bug: dato financiero incorrecto |
| F.1.3 | La ventana de fin de día era `fecha.atTime(23,59,59)` — dejaba afuera lo facturado/agendado en el último segundo (`23:59:59.000001`…`.999999`). | Se usa `LocalTime.MAX` (mismo criterio que `FacturacionServiceImpl`). Aplicado en `recalcularDashboard` y en `reporteUsoConsultorios`. | Consistencia / borde |

**Reproducción en vivo (antes del fix), 2026-09-02:** `GET /api/reportes/dashboard` a las 14:50 devolvía `turnosAtendidos: 3`, `facturacionTotalDia: 63000`, `fechaActualizacion: 11:06:11`; `POST /api/reportes/dashboard/recalcular` en el mismo momento devolvía `turnosAtendidos: 6`, `facturacionTotalDia: 76500`. Entre las 11:06 y las 14:50 se habían atendido 3 pacientes más y facturado $13.500 más, pero la UI seguía mostrando los números de las 11:06.

**Verificación (después del fix):** cada `GET /api/reportes/dashboard` recalcula — el `fechaActualizacion` avanza en cada llamada y los números coinciden exactos con `POST /dashboard/recalcular`. Sin errores en el log.

### F.2 Sin cambios de esquema ni de contrato REST

- `GET /api/reportes/dashboard` y `POST /api/reportes/dashboard/recalcular`: misma firma. Cambia solo el comportamiento del GET para la fecha de hoy (antes podía devolver una foto vieja; ahora siempre fresca).
- No se toca `docs/schema.sql` ni ninguna entidad.
- El frontend no necesitó cambios: el botón "Actualizar" (`loadDashboard()` → `GET /dashboard`) ahora sí trae datos frescos.

### F.3 Tests

`mvn test` completo el 2026-09-02: **113/113 verdes** (111 previos + 2 nuevos en `ReporteServiceTest`):
- `testGetDashboardHoy_RecalculaAunqueYaExistaLaFotoDelDia` — regresión de F.1.1: aunque exista fila para hoy, se recalcula y se devuelven los números frescos.
- `testRecalcularDashboard_ExcluyeFacturasAnuladasDelTotal` — F.1.2: `ANULADO`/`REINTEGRADO` no cuentan en `facturacionTotalDia`; `cobrosPendientes` solo `PENDIENTE`.

---

## Parte G — Cartilla del médico: cada médico gestiona sus prestaciones y obras sociales (sesión 2026-09-02)

### G.1 El médico no podía sumar sus propias prestaciones ni obras sociales

**Situación previa.** Una decisión de 2026-08-23 (RF-M3) había dejado el alta/baja de la relación médico–obra social y de la relación médico–prestación como **admin-only** (Gerente/Administrativo). El propio médico solo podía *ver* su cartilla y *editar el coseguro* de una obra social ya asociada. En "Mis Prestaciones" el título decía literalmente "(solo lectura)".

**Por qué se revierte.** El documento de relevamiento original describe la hoja del médico —con sus prestaciones, importes y *"obras sociales con las que trabaja"*— como algo que *"es actualizable cada vez que el médico lo desee"* (Entrega, §1.9, y §1.10 "cada médico informa… sus obras sociales"). El médico es la fuente de esa información. Dejarlo admin-only fue un desvío; habilitar la autogestión **re-alinea con la Entrega**.

**Cambio (aditivo — Gerente/Administrativo conservan todo lo que ya podían).**

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| G.1.1 | El médico gestiona sus **prestaciones** | Endpoints nuevos `POST/PUT/DELETE /api/medicos/me/prestaciones[/{idMedicoPrestacion}]` (rol `MEDICO`). Resuelven el médico desde el email del JWT y **validan pertenencia** sobre la fila antes de editar/borrar (misma lógica y mensajes que `actualizarCoseguroPropio`). Reutilizan `agregarPrestacion`/`actualizarPrestacion`/`eliminarPrestacion` — misma validación de duplicados y misma baja lógica. | RF-M4 / relevamiento |
| G.1.2 | El médico gestiona sus **obras sociales** | Endpoints nuevos `POST /api/medicos/me/obras-sociales` y `DELETE /api/medicos/me/obras-sociales/{idMedicoObraSocial}` (rol `MEDICO`), con el mismo chequeo de pertenencia. El `PUT /me/obras-sociales/{id}` (coseguro) ya existía. El médico asocia obras sociales **del catálogo existente**; crear una obra social nueva en el catálogo sigue siendo admin-only (es un maestro compartido con "requiere bono", código, etc.). | RF-M3 / relevamiento |
| G.1.3 | RN-017 ampliada | De *"un médico solo puede editar el coseguro de su propia relación con una obra social"* a *"un médico solo gestiona su propia cartilla: sus prestaciones y las obras sociales con las que trabaja (incluido el coseguro), nunca las de otro médico"*. Mismo principio (Constitución §3), alcance más amplio. No es una regla nueva — no se agrega RN-022. | Coherencia |
| G.1.4 | Frontend "Mis Prestaciones" | Pasa de tabla de solo lectura a **tabla editable**: botón "+ Agregar prestación", edición en línea de duración/importe/tipo y "Quitar" por fila. Cada acción persiste al instante. | — |
| G.1.5 | Frontend "Mis Datos" → Obras Sociales | Se agrega un selector (catálogo, filtrado a las que el médico todavía no tiene) + coseguro + "Agregar", y un botón "Quitar" por fila. El bloque de coseguro que ya existía se mantiene. | — |
| G.1.6 | `importeParticular` de una prestación | Sigue siendo **informativo**: la facturación automática usa `Medico.importeConsulta` (RN-006), que **no** es autoeditable (queda admin-only vía `actualizarMedico`). Habilitar la cartilla no cambia nada del split ni de la facturación. | Sin impacto financiero |
| G.1.7 | Se saca el campo **"Tipo"** de una prestación de toda la UI | La columna/campo `Tipo` (texto libre "Presencial / Virtual…" que nadie completaba) se quitó de "Mis Prestaciones" y del modal de Médicos (Gerente/Administrativo). La columna `Medico_Prestacion.Tipo` **se mantiene en la base y en el DTO** (patrón del proyecto: no se toca el esquema); simplemente ya no se muestra ni se envía desde ningún formulario. En las prestaciones que ya tuvieran un valor, se conserva salvo que se editen desde el modal del médico (que ya no manda el campo). | Pedido del 02/09 — campo sin uso real que ensuciaba la pantalla |
| G.1.8 | "Mis Prestaciones" y "Mis Datos" rediseñadas | "Mis Prestaciones" pasa de una grilla de inputs sueltos a **tabla de lectura + modal de alta/edición** (mismo patrón que Médicos/Pacientes): nombre en negrita, duración/importe en mono, íconos de editar/quitar. "Mis Datos" se parte en dos cards — *Datos personales* (lista de definición para lo de solo lectura + nombre/apellido editables) y *Obras sociales con las que trabajás* (tabla con coseguro editable en línea + barra de "agregar"). CSS nuevo en `app.css` (sección "MI CARTILLA"). | Pedido del 02/09 — "se ve bastante feo" |

### G.2 Sin cambios de esquema

- Entidades `Medico_Prestacion` y `Medico_ObraSocial` sin tocar. Solo endpoints nuevos + una capa de resolución "por email del JWT + chequeo de pertenencia" en `MedicoServiceImpl`.
- Los endpoints admin (`/api/medicos/{id}/prestaciones`, `/api/medicos/{id}/obras-sociales`) quedan igual.

### G.3 Cambios de API (nuevos endpoints)

- **Nuevo:** `POST /api/medicos/me/prestaciones` (MEDICO) — agrega una prestación a la cartilla propia.
- **Nuevo:** `PUT /api/medicos/me/prestaciones/{idMedicoPrestacion}` (MEDICO) — edita una prestación propia (409-lógico si es de otro médico: `RN-017`).
- **Nuevo:** `DELETE /api/medicos/me/prestaciones/{idMedicoPrestacion}` (MEDICO) — baja lógica de una prestación propia.
- **Nuevo:** `POST /api/medicos/me/obras-sociales` (MEDICO) — asocia una obra social del catálogo a la cartilla propia, con coseguro.
- **Nuevo:** `DELETE /api/medicos/me/obras-sociales/{idMedicoObraSocial}` (MEDICO) — quita una obra social de la cartilla propia.

### G.4 Verificación en vivo (2026-09-02, médico `ana.gomez@medispace.com`)

- **API:** alta de prestación → edición de su importe → alta de obra social del catálogo → listados reflejan los cambios → baja de ambas → estado vuelve al baseline. Guard: `PUT /api/medicos/me/prestaciones/{id}` sobre una prestación de otro médico → **HTTP 400 `RN-017: No podés modificar las prestaciones de otro médico.`**. Duplicado: `POST` de una prestación ya asociada → `Esta prestación ya está asociada al médico.`.
- **UI (headless, logueado como MEDICO):** "Mis Prestaciones" y "Mis Datos" cargan sin errores de JS; el modal "Agregar prestación" da de alta una fila; en modo edición el nombre queda fijo (no editable) y el cambio de importe persiste; "Quitar" borra la fila. Screenshots revisados.
- Sin errores en el log del servidor.

### G.5 Tests

`mvn test` completo el 2026-09-02: **118/118 verdes** (113 previos + 5 nuevos en `MedicoServiceTest`):
- `testAgregarPrestacionPropia_ResuelveMedicoPorEmailYAgrega`
- `testActualizarPrestacionPropia_DeOtroMedicoLanzaBusinessRuleException`
- `testEliminarPrestacionPropia_DeLaCartillaPropia_BajaLogica`
- `testAgregarObraSocialPropia_ResuelveMedicoPorEmailYAgrega`
- `testEliminarObraSocialPropia_DeOtroMedicoLanzaBusinessRuleException`

---

## Parte H — La factura toma el precio de la prestación elegida (sesión 2026-09-02)

Continuación natural de la Parte G: si el médico ahora carga un importe por prestación (`Medico_Prestacion.importeParticular`), la factura automática debería usar ese precio, no un valor fijo por médico.

### H.1 `Facturacion.importeTotal` salía del importe de consulta fijo del médico, ignorando la prestación

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| H.1.1 | `crearFacturacionAutomatica` seteaba `importeTotal = Medico.importeConsulta` (un valor fijo por médico), aunque el turno se hubiera reservado para una prestación puntual con su propio precio. La prestación solo se usaba para el texto de `tipoConsulta`. | El importe base de la factura ahora es el `importeParticular` de la relación **médico–prestación** del turno (`MedicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion`). Si el turno no tiene prestación específica ("Consulta general") **o** el médico no le cargó precio, se cae a `Medico.importeConsulta` (comportamiento anterior). El circuito de cobro (`registrarCobro`) sigue pudiendo ajustar el importe a mano (p. ej. "consulta + electro" en una misma atención — relevamiento §1.10). | Relevamiento §1.10: *"el valor de cada prestación varía según el tipo de atención, ya que no es equivalente el costo de una consulta al de un estudio"* |
| H.1.2 | El split RN-006 (70/30 o el del contrato) **no cambia**: se sigue aplicando sobre `importeTotal`. Al ser ahora el precio real de la prestación, el split refleja mejor *"el 70% de lo recaudado"* (relevamiento §1.10). | — | Sin cambio de RN-006 |
| H.1.3 | Frontend — modal "Reservar Turno" | El `<select>` de Prestación muestra el precio en **cada** opción, incluida **"Consulta general — $X"** (el `importeConsulta` del médico), más un renglón "Se factura $X (precio de la prestación)" / "…(importe de consulta del médico)" según lo elegido. Es informativo: el backend calcula el importe, el front no lo manda. | Que la administración vea qué se va a facturar antes de reservar |
| H.1.4 | Frontend — "Tipo de Consulta" | Se sacó la opción **"Control"** del `<select>` (quedan "Consulta" y "Práctica Médica"). Es solo una etiqueta de la reserva (`Turno.Tipo_Consulta` → `Facturacion.Tipo_Consulta`), ninguna lógica de negocio se ramifica por ese valor; los turnos/facturas viejos con "CONTROL" lo conservan. | Pedido del 02/09 |

### H.2 Sin cambios de esquema ni de contrato REST

- `Medico_Prestacion.importeParticular` y `Turno.ID_Prestacion` ya existían. Solo se inyecta `MedicoPrestacionRepository` en `FacturacionServiceImpl` y se agrega el `<select>` enriquecido + el renglón de ayuda en `turnos.js` / `app.html`.
- `POST /api/turnos/{id}/reservar` y el flujo de estados no cambian de firma.

### H.3 Verificación en vivo (2026-09-02)

- Turno reservado para "Limpieza facial" (precio $40.000; el médico tiene `importeConsulta` $5.000) → al pasar a `EN_ESPERA`, `Facturacion.importeTotal = 40000.00`.
- Turno reservado **sin** prestación → `Facturacion.importeTotal = 5000.00` (fallback a `importeConsulta`).
- Modal de reserva: el `<select>` lista `Consulta general — $15.000,00` / `Ecografía — $6.000,00` / `Revision cervical — $7.800,00`; con "Consulta general" el renglón dice *"Se factura $15.000,00 (importe de consulta del médico)"* y con una prestación *"…(precio de la prestación)"*. "Tipo de Consulta" muestra solo "Consulta" y "Práctica Médica".
- Turnos de prueba cancelados después (facturas → `ANULADO`, que la Parte F ya excluye del total del dashboard).

### H.4 Tests

`mvn test` completo el 2026-09-02: **120/120 verdes** (118 previos + 2 nuevos en `FacturacionServiceTest`):
- `testCrearFacturacionAutomatica_UsaImporteParticularDeLaPrestacionElegida`
- `testCrearFacturacionAutomatica_CaeAImporteConsultaSiLaPrestacionNoTienePrecio`

---

## Parte I — Identificación del paciente: "DNI / Pasaporte" (sesión 2026-09-02)

### I.1 El alta de paciente pedía solo "DNI", sin contemplar extranjeros

La Entrega ya resuelve el caso — la ficha del paciente identifica por **"DNI / Pasaporte (requerido – único)"** (Entrega §2 y las FAQ del relevamiento: *"¿Qué pasa si un paciente extranjero no tiene DNI argentino? Usamos el número de pasaporte como identificador"*). La implementación había etiquetado el campo solo como "DNI", lo que hacía dudar si se podía cargar un extranjero.

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| I.1.1 | El campo identificador se rotula **"DNI / Pasaporte"** en todos los formularios de alta de paciente (módulo Pacientes y alta rápida dentro de "Reservar Turno"), con placeholder *"DNI, o N° de pasaporte si es extranjero"*. | Etiqueta + placeholder. **No cambia el modelo**: sigue siendo la columna `Pacientes.DNI VARCHAR(20)` — un pasaporte entra sin problema y no hay validación de formato (nunca la hubo). | Re-alinea con la Entrega §2 |
| I.1.2 | Mensajes de negocio | **RN-008**: "El DNI del paciente es inmutable" → "El **documento (DNI / Pasaporte)** del paciente es inmutable una vez creado". **RN-009**: idem para "ya se encuentra registrado". La regla y su número no cambian: aplica al identificador, sea DNI o pasaporte. | Que el mensaje no diga "DNI" cuando se cargó un pasaporte |
| I.1.3 | Textos de UI | Buscadores de paciente (Pacientes, Turnos, Historias Clínicas) y encabezados de tabla pasan de "DNI" a "DNI / Pasaporte" (labels/columnas) o "documento" / "Doc:" (placeholders y prefijos de valor). | Consistencia |
| I.1.4 | Bebés / menores sin DNI tramitado | Se resuelve por procedimiento, como en el relevamiento: se carga el documento de la madre/tutor en el mismo campo. No requiere cambio de sistema. | — |

### I.2 Sin cambios de esquema

- `Pacientes.DNI` sigue siendo `VARCHAR(20) NOT NULL UNIQUE`. El nombre de la columna en la base no cambia; solo la presentación.
- `PacienteCreateDTO` / `PacienteUpdateDTO` sin cambios (nunca tuvieron `@Pattern`/`@Size` sobre el DNI).

### I.3 Verificación en vivo (2026-09-02, como ADMINISTRATIVO)

- Alta de paciente extranjero con `dni = "P-USA-4477X"` → `201`, HC vacía creada.
- Alta con el mismo pasaporte → `RN-009: El documento (DNI / Pasaporte) ya se encuentra registrado (incluso si está inactivo).`
- `PUT` cambiando el documento → `RN-008: El documento (DNI / Pasaporte) del paciente es inmutable una vez creado.`
- Formularios: label "DNI / Pasaporte *" y placeholder "DNI, o N° de pasaporte si es extranjero" en el modal de Pacientes y en el alta rápida de "Reservar Turno".
- Paciente y usuario administrativo de prueba borrados después.

### I.4 Tests

`mvn test` completo el 2026-09-02: **121/121 verdes** (120 previos + 1 nuevo en `PacienteServiceTest`; 2 asserts de mensaje actualizados en los tests de RN-008/RN-009):
- `testRN008_PasaporteDeExtranjeroSeAceptaComoDocumento` — un `dni` con formato de pasaporte (`AB1234567`) se acepta y genera la HC.

---

## Parte J — Correcciones de la verificación en vivo completa (sesión 2026-09-09)

Verificación en vivo de todo el sistema con los 3 roles contra la base viva, foco en Historias Clínicas, evoluciones, adjuntos y reportes gerenciales. `mvn test` en verde (121/121) y ~65 pruebas de API. Los 4 módulos andaban en el happy path; se corrigieron 4 problemas.

### J.1 Una factura anulada bloqueaba re-facturar el turno

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| J.1.1 | `FacturacionServiceImpl.crearFacturacionAutomatica` (idempotencia): `findByTurnoIdTurno(...).isPresent()` devolvía también las facturas en estado `ANULADO`. Circuito real (§E.6.5): un turno pasa a *En Espera* (se factura) → el paciente se retira → se cancela el turno (factura → `ANULADO`, cupo → `DISPONIBLE`) → **el mismo cupo se re-reserva para otro paciente** → al pasar a *En Espera* se devolvía la factura ANULADA vieja → "Esta facturación está anulada" y la atención nueva **no se podía cobrar nunca**. | Si la factura previa del turno está `ANULADO`, se **reutiliza esa misma fila** con los datos nuevos (importe, paciente, split, obra social, tipo de consulta) y vuelve a `PENDIENTE`. No se crea una segunda factura — `findByTurnoIdTurno` devuelve una sola por turno y una segunda rompería esa unicidad. La fila queda con la observación `[Refacturada: …]`. | Bug — reproducido en vivo el 09/09 |

### J.2 IDOR en Historias Clínicas y adjuntos (rol MEDICO)

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| J.2.1 | `GET /api/historias-clinicas/paciente/{id}` no validaba pertenencia: un MEDICO leía la **historia clínica completa de cualquier paciente** por id. Ídem `GET /api/historias-clinicas/adjuntos/{id}/descargar` — cualquier médico bajaba cualquier adjunto por id. La UI lo tapaba (el buscador de pacientes ya filtra por médico), la API no. | Control de pertenencia para el rol MEDICO en `obtenerHistoriaClinicaPorPaciente` y `descargarAdjunto` (`PacienteRepository.existsVinculadoAMedico`, mismo criterio RN-010 que ya aplicaba `agregarEvolucion`). ADMINISTRATIVO ve todas; GERENTE no llega (403). Error: **400 `RN-010: No tenés acceso a la historia clínica de un paciente que no atendés.`** | Constitución §3 / spec §4.2 — cierra el mismo tipo de hueco que los Hallazgos 1‑2 (§D.4.2 / §D.5.1) |

### J.3 Cobro sin importe → 500

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| J.3.1 | `POST /api/facturacion/{id}/cobro` sin `importeTotal` → **HTTP 500** (`PropertyValueException`, `Cobros.Importe_Total` es NOT NULL). El frontend ya lo previene con un guard, pero un request directo o una regresión de UI daban un 500. | Chequeo explícito en `registrarCobro`: si `importeTotal` es null → **400 "El importe total del cobro es obligatorio."** | Robustez |

### J.4 Fin de día inconsistente en 2 reportes

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| J.4.1 | `ReporteServiceImpl.reporteFacturacionPorMedico` y `reporteFacturacionPorObraSocial` usaban `hasta.atTime(23,59,59)`, dejando fuera lo facturado en la última fracción del día. El dashboard, `reporteUsoConsultorios` y `FacturacionServiceImpl` ya se habían pasado a `LocalTime.MAX` (§F.1.3). | Los 2 reportes usan ahora `hasta.atTime(LocalTime.MAX)`. | Consistencia — completa el fix §F.1.3 |

### J.5 Sin cambios de esquema ni de contrato REST

- Ningún cambio toca `docs/schema.sql`. `GET /api/historias-clinicas/paciente/{id}` y `.../adjuntos/{id}/descargar` mantienen su firma REST; solo aplican control de pertenencia para MEDICO (igual que ya hacían `/medicos/{id}` y `/turnos/{id}` desde §D.4.2 / §D.5.1).
- `RegistrarCobroDTO` sin cambios de campos. La firma interna `HistoriaClinicaService.obtenerHistoriaClinicaPorPaciente` / `descargarAdjunto` ahora recibe el `Usuario` autenticado (el controller ya lo tenía en el `SecurityContext`).

### J.6 Tests

`mvn test` completo el 2026-09-09: **125/125 verdes** (121 previos + 4 nuevos):
- `FacturacionServiceTest.crearFacturacionAutomatica_RefacturaReutilizandoLaFilaSiLaAnteriorEstabaAnulada` (J.1)
- `FacturacionServiceTest.registrarCobro_RechazaSiFaltaElImporteTotal` (J.3)
- `HistoriaClinicaServiceTest.testMedicoNoAccedeHistoriaDePacienteAjeno` (J.2)
- `HistoriaClinicaServiceTest.testMedicoNoDescargaAdjuntoDePacienteAjeno` (J.2)

---

## Parte K — Suite de QA end-to-end sobre la base viva (sesión 2026-09-23)

Suite de QA completa (Fases 0-8: reconocimiento, datos QA aislados `QA_*`/`@qa.test`, Flujos A/B/C, Historia Clínica, Facturación/Liquidaciones, Reportes/Dashboard, ABM/permisos, no funcionales) corrida por API directa contra la base viva, con backup previo (`medispace_pre_qa_20260923.bak`) y datos QA identificables. Se encontraron y corrigieron 4 bugs (2 de severidad alta financiera). Detalle completo, evidencia y pasos de reproducción en `qa/TEST_REPORT.md` y los documentos por fase en `qa/`.

### K.1 Se podía contratar un consultorio no disponible

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| K.1.1 | `ArrendamientoServiceImpl.crearArrendamiento` validaba RN-013 (superposición) y RN-014 (mínimo 4hs) pero nunca leía `Consultorio.Estado` — se podía asignar un contrato a un consultorio `EN_MANTENIMIENTO`/`BLOQUEADO`/`FUERA_DE_SERVICIO`, que además generaba turnos "Disponible" reservables por un paciente en un consultorio fuera de servicio. | Nueva regla **RN-022**: se rechaza si `Consultorio.Estado != DISPONIBLE`. Mensaje: `RN-022: El consultorio no está Disponible (estado actual: …). No se puede asignar un contrato de arrendamiento.` | Bug — reproducido en vivo el 23/09 (QA-3 en mantenimiento) |

### K.2 Un contrato podía quedar "activo" generando 0 turnos, sin aviso

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| K.2.1 | `TurnoServiceImpl.generarTurnosParaContrato` deduplica turnos por `(médico, fecha_hora)` solamente, sin el consultorio. Como no existía ningún chequeo de superposición a nivel médico (RN-013 solo mira el consultorio), un médico podía tener 2 contratos activos superpuestos en día/horario en consultorios distintos — el segundo contrato se creaba igual (`201`, `Estado: ACTIVO`) pero la deduplicación descartaba en silencio todos sus turnos nuevos (`turnosGenerados: 0`), sin ningún error ni advertencia visible. | Nueva regla **RN-023** en `crearArrendamiento`: se rechaza si el médico ya tiene otro contrato activo que se superponga en día/horario/período, sin importar el consultorio. Ataca la causa raíz (ya no se puede crear el segundo contrato conflictivo) en vez de parchear la deduplicación de turnos. Mensaje: `RN-023: El médico ya tiene otro contrato activo que se superpone con el día/horario/período solicitado (en otro consultorio).` | Bug — reproducido en vivo el 23/09 |

### K.3 Liquidación, Cierre Diario y 3 reportes sumaban facturas ANULADAS como si fueran cobradas

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| K.3.1 | `LiquidacionServiceImpl.generarLiquidacion`, `CierreDiarioServiceImpl.generarCierreDiario`, `ReporteServiceImpl.reporteFacturacionPorMedico`, `.reporteFacturacionPorObraSocial` y `.reporteUsoConsultorios` sumaban `Facturacion.Importe_Total` de **todas** las facturas del período, sin filtrar por estado. Una factura `ANULADO` (turno cancelado tras estar "En Espera") conserva su `Importe_Total` pero no es plata realmente facturada — inflaba liquidaciones, cierres y reportes. Evidencia: liquidación de `QA_Dr_Cardio` dio $140.000 en vez de $100.000 (2 facturas anuladas de $20.000 incluidas), validado contra una consulta SQL independiente. | Se aplicó a los 5 lugares el mismo filtro que **ya existía, correcto, en `ReporteServiceImpl.recalcularDashboard`** (el Dashboard Gerencial): excluir `ESTADO_PAGO IN ('ANULADO', 'REINTEGRADO')` antes de sumar. El fix se había hecho una sola vez (en el Dashboard) y nunca se había propagado a los otros 5 puntos que agregan montos de Facturación. | Bug — el propio código del Dashboard ya documentaba (en un comentario) el motivo exacto de este bug en los otros 5 lugares |

### K.4 La misma facturación se podía liquidar dos veces

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| K.4.1 | `LiquidacionServiceImpl.generarLiquidacion` no verificaba si el período solicitado se superponía con una liquidación `EMITIDA` ya existente del mismo médico. Se pudo generar una liquidación diaria y luego una semanal que incluía ese mismo día — ambas `EMITIDA`, ambas reclamando el mismo importe sobre las mismas facturas. Un médico liquidado por las dos cobraría el doble por el mismo trabajo. | Nueva regla **RN-024**: se rechaza si existe una liquidación `EMITIDA` del médico cuyo `[Fecha_Desde, Fecha_Hasta]` se superpone con el período solicitado. Mensaje: `RN-024: Ya existe una liquidación EMITIDA para este médico que se superpone con el período solicitado (id …, … a …).` | Bug — reproducido en vivo el 23/09 (liquidación diaria + semanal simultáneas) |

### K.5 Sin cambios de esquema

Los 4 fixes son lógica de servicio + 2 métodos nuevos de repositorio (`ArrendamientoModuloRepository.countSuperposicionesMedico`, `LiquidacionMedicaRepository.findSuperpuestas`) — ninguno toca `docs/schema.sql`. Sin cambios de contrato REST (mismos endpoints, mismos DTOs; solo nuevos casos de rechazo con su mensaje).

### K.6 Tests

`mvn test` completo el 2026-09-23: **133/133 verdes** (125 previos + 8 nuevos):
- `ArrendamientoServiceTest.testRN022_ConsultorioNoDisponibleRechazado`
- `ArrendamientoServiceTest.testRN023_SuperposicionMedicoEnOtroConsultorioRechazada`
- `LiquidacionServiceTest.testRN024_BloqueaLiquidacionSuperpuesta`
- `LiquidacionServiceTest.testBUG003_FacturasAnuladasNoSumanAlTotal`
- `CierreDiarioServiceTest.testBUG003_FacturaAnuladaNoSumaAlCierre`
- `ReporteServiceTest.testBUG003_ReporteFacturacionPorMedico_ExcluyeAnuladasYReintegradas`
- `ReporteServiceTest.testBUG003_ReporteFacturacionPorObraSocial_ExcluyeAnuladas`
- `ReporteServiceTest.testBUG003_ReporteUsoConsultorios_ExcluyeAnuladas`

Además se corrigieron 2 fixtures de `ArrendamientoServiceTest` (`testRN014_Exactamente4HorasPermitido`, `testRN013_SuperposicionHorariaRechazada`) que construían su `Consultorio` mock sin `Estado` — con el nuevo chequeo RN-022, `Estado = null` se interpreta como "no disponible" y rechazaba antes de llegar a la lógica que esos tests querían ejercitar. Se les agregó `.estado("DISPONIBLE")` (todo `Consultorio` real tiene ese valor por default en el schema).

Verificado además en vivo contra la base real (datos QA) tras reiniciar la app con el código nuevo: los 4 fixes se confirmaron uno por uno con los mismos pasos que los habían encontrado (ver `qa/TEST_REPORT.md`).

---

## Parte L — El copago se autocompleta desde la cartilla del médico al reservar un turno (sesión 2026-09-23)

Pedido de negocio: al reservar un turno, "Obra Social" era un campo de **texto libre** (`<input type="text">`, cualquier valor tipeado) y "Copago" un número que la administración tenía que averiguar y cargar a mano, sin relación con el coseguro que el propio médico ya había pactado para esa obra social (RF-M3 / Anexo §B.1, §G.1.2). Quedaba anotado como limitación pendiente ("Copago no se prellena automáticamente…", ver versión anterior de este documento).

### L.1 Qué cambió

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| L.1.1 | Campo "Obra Social" en el modal "Reservar Turno" | Pasa de texto libre a un **desplegable acotado a la cartilla de ese médico** (reusa `GET /api/medicos/{id}/obras-sociales`, el mismo endpoint que ya alimenta "Mis Datos" desde la Parte G — no fue necesario ningún endpoint nuevo). Cada opción muestra cuánto cubre esa obra social, p. ej. "OSDE — Cubre $20,00"; si el médico no le definió coseguro a esa obra social, se lista igual como "… — cobertura sin definir". | Que solo se puedan elegir obras sociales que el médico realmente acepta, no cualquier texto |
| L.1.2 | Campo "Monto a cobrar al paciente" (antes rotulado "Copago") | Se **autocompleta como el importe de la consulta/prestación elegida MENOS lo que cubre la obra social** (`importeEstimado - Medico_ObraSocial.Importe_Coseguro`), no con el coseguro directamente — ver corrección en §M.1 (esta tabla reflejaba una fórmula invertida en la versión original de esta Parte, corregida el mismo 23/09 tras validar el modelo de negocio real con el usuario). Si es "Particular", se autocompleta con el importe completo. Sigue siendo un `<input type="number">` **editable**. | RF-T2 — automatizar el caso general sin bloquear la excepción |
| L.1.3 | Auto-selección por paciente | Si el paciente elegido ya tiene una obra social cargada en su ficha (`Paciente.idObraSocial`) y esa obra social está entre las que el médico acepta, el desplegable la preselecciona solo al elegir al paciente (antes de que la administración toque el campo) y dispara el mismo cálculo del copago. Si no coincide (el médico no la acepta, o el paciente no tiene obra social), queda en "Particular (sin obra social)" para elegir a mano. | Reduce el caso más común (paciente con OS habitual) a cero clicks extra |
| L.1.4 | "Particular (sin obra social)" | Se mantiene como primera opción (valor vacío, mismo comportamiento que el texto libre vacío de antes) — el copago queda en blanco para cargar a mano si corresponde. | Compatibilidad con el flujo sin cobertura |

### L.2 Sin cambios de esquema ni de contrato REST

Cambio **100% frontend** (`app.html`, `js/modules/turnos.js`). El backend ya tenía todo lo necesario desde las Partes B y G:
- `GET /api/medicos/{id}/obras-sociales` (existente) sigue devolviendo `idObraSocial`, `nombreObraSocial`, `importeCoseguro` — el dropdown se arma con esa respuesta tal cual.
- `ReservarTurnoDTO.obraSocial` sigue siendo un `String` (el nombre) y `.copago` un `BigDecimal` — el payload que manda el frontend no cambia de forma, solo el valor que el usuario ya no tiene que adivinar. Sin migraciones, sin cambios en `Turno`, `Medico_ObraSocial` ni `docs/schema.sql`.

### L.3 Verificación en vivo (2026-09-23, como ADMINISTRATIVO)

Probado con headless Chromium contra la app corriendo (`mvn spring-boot:run`) y la base real:
- Médico `ana.gomez@medispace.com` (id 1) tiene cartilla con **OSDE** (coseguro $20,00) y **Swiss Medical Verificacion** (sin coseguro cargado). Al abrir "Reservar Turno" sobre un turno disponible suyo, el desplegable "Obra Social" mostró exactamente esas dos opciones + "Particular" — **no** apareció "Coperativa obrera" (obra social de la cartilla de otro médico), confirmando el filtrado por médico.
- Paciente "Maria Lopez" (tiene OSDE cargada en su ficha): al seleccionarla, el desplegable se autoseleccionó en "OSDE — Cubre $20,00".

**Nota (corrección posterior, ver §M):** en esta primera verificación el campo se completó con `20` (el coseguro directo). Tras validar el modelo de negocio real con el usuario, se corrigió: debía completarse con `importe de la consulta − coseguro` (p. ej. $4.800 − $20 = $4.780, ver §M.3), no con el coseguro solo. El resto de lo verificado acá (filtrado por médico, auto-selección por paciente, recálculo al cambiar de obra social a mano, edición manual posterior) sigue siendo válido — cambió únicamente la fórmula del valor calculado.

### L.4 Tests

Cambio sin lógica de backend en su versión original — no se agregaron tests de `mvn test` en esta Parte (el comportamiento se verificó en vivo, §L.3). La corrección de la fórmula sí tocó backend además de frontend — ver tests en §M.7.

---

## Parte M — Coseguro OS vs. copago en mano: fórmula correcta y RN-025 (sesión 2026-09-23)

Continuación directa de la Parte L. Al usar el flujo nuevo (capturas de pantalla del usuario), aparecieron tres problemas: (1) "no tiene mucho sentido" — el monto calculado en la reserva no reflejaba ningún descuento real; (2) el modal "Registrar Cobro" volvía a pedir "Cubierto OS" en blanco, sin relación con lo cargado al reservar; (3) la tabla de Facturación no mostraba nada distinto. Investigación del modelo de negocio con el usuario (ejemplo real: consulta $4.800, obra social OSDE, coseguro pactado $20) reveló que la Parte L había implementado la fórmula **invertida**.

### M.1 El modelo de negocio correcto (confirmado explícitamente por el usuario)

- **Costo Total** de la consulta: $4.800 (precio de lista, no cambia según la cobertura).
- **Coseguro** (`Medico_ObraSocial.Importe_Coseguro`, $20 en el ejemplo): es lo que **la obra social le paga al médico directamente**, fuera de la caja del consultorio — no lo que paga el paciente. La Parte L lo había tratado como si fuera el monto a cobrarle al paciente.
- **Lo que el paciente paga en mano** (efectivo/transferencia, a través del sistema) = Costo Total − Coseguro = $4.800 − $20 = **$4.780**. Si es "Particular" (sin obra social), paga el total completo.
- El split 70/30 médico/consultorio (RN-006), el Cierre de Caja diario y los Reportes (por médico, por obra social, Dashboard) se calculan **solo sobre lo que entra a la caja del consultorio** ($4.780 en el ejemplo) — los $20 del coseguro son 100% del médico y nunca pasan por el split.

### M.2 Hallazgo que acotó el alcance del fix

`Turno.Importe_Copago_Planificado` y `Facturacion.Importe_Copago` son el mismo valor de punta a punta (`TurnoServiceImpl.reservarTurno` lo copia 1:1 a `Turno`, `FacturacionServiceImpl.crearFacturacionAutomatica` lo copia 1:1 a `Facturacion`). Corrigiendo la fórmula en el frontend de reserva, ese campo pasa a representar exactamente "lo cobrado en mano" sin tocar la generación automática de la factura — el resto del fix es propagar ese concepto a Cobro, Liquidación, Cierre de Caja y Reportes.

### M.3 Qué cambió

| # | Ítem | Decisión / cambio | Motivo |
|---|---|---|---|
| M.3.1 | Fórmula del monto a cobrar al paciente (reserva de turno) | `js/modules/turnos.js`: `actualizarCopagoDesdeObraSocial()` reescrita — `montoACobrar = Math.max(0, importeEstimado - coseguro)` en vez de `= coseguro`. Si es "Particular", `montoACobrar = importeEstimado` completo (antes quedaba en blanco). Se coordina con la prestación elegida (`obtenerImporteEstimadoReserva()`, reutilizada por ambos cálculos) para que cambiar la prestación después de elegir la obra social recalcule también. | RF-T2, corrección de la Parte L |
| M.3.2 | `FacturacionServiceImpl.crearFacturacionAutomatica` — fallback cuando no hay copago planificado | Turnos legacy o reservados sin pasar por el modal (`importeCopagoPlanificado == null`) caían a `ZERO`; con Liquidación ahora usando este campo como base, hubieran liquidado $0 al médico. Fallback corregido a `importeConsulta` (mismo criterio que "Particular": sin dato, se asume que se cobró todo en mano). | Evitar una regresión silenciosa introducida por M.3.4 |
| M.3.3 | Modal "Registrar Cobro" — "Cubierto OS" pasa a ser derivado | `app.html`/`js/modules/facturacion.js`: el input deja de ser libre (`readonly`), se recalcula en vivo (`recalcularCobroOs()`) como `Total − Copago` cada vez que se edita alguno de los dos. `Total` y `Copago` siguen editables (caso "consulta + electro", Anexo §H). Backend (`FacturacionServiceImpl.registrarCobro`): deja de leer `dto.getImporteCubiertoOs()` (campo eliminado de `RegistrarCobroDTO`) y lo calcula server-side. | Elimina la doble carga manual que motivó el pedido ("vuelve a preguntar cobertura OS") |
| M.3.4 | **RN-025** — base del split 70/30 y de las sumas financieras | `SplitFinancieroCalculator` gana dos métodos: `montoCobradoEnMano(total, copago)` (= copago, o total si copago es null) y `montoCubiertoPorObraSocial(total, copago)` (complemento, nunca negativo) — única fuente de verdad, igual que ya lo es para el split 70/30, para que estos cinco lugares no puedan volver a divergir entre sí. Aplicado en: `LiquidacionServiceImpl.generarLiquidacion`, `CierreDiarioServiceImpl.generarCierreDiario`, `ReporteServiceImpl.recalcularDashboard`, `.reporteFacturacionPorMedico`, `.reporteFacturacionPorObraSocial`, `.reporteUsoConsultorios`. | El médico y el consultorio se reparten lo que efectivamente entra a la caja, no el precio de lista |
| M.3.5 | `FacturacionResponseDTO.importeCubiertoOs` (nuevo, computado) | Expone `importeTotal - importeCopago` sin persistirlo (sin cambio de esquema). Se muestra como columna nueva "Cubierto OS" en la tabla de Facturación, entre "Total" y "Copago". | Visibilidad — antes "en Facturación no se modificaba nada" |
| M.3.6 | `ReporteFacturacionDTO.totalCopago` → `totalCubiertoOs` | Con `importeCopago` representando ahora "lo cobrado en mano", sumarlo por médico/obra social daba el mismo valor que `totalFacturado` — columna redundante. Se repuso por la suma de `montoCubiertoPorObraSocial(...)`: cuánto corresponde reclamarle a cada obra social en el período. Actualizado en `reportes.js` (columnas "Copago" → "Cubierto OS" en ambos reportes). | Consistencia — evita la misma ambigüedad que motivó todo este cambio |

### M.4 Sin cambios de esquema

Los 3 importes (`Importe_Total`, `Importe_Copago`, `Importe_Cubierto_OS`) ya existían en `docs/schema.sql` (`Facturaciones` y `Cobros`) desde antes de esta sesión — el fix es enteramente de lógica de servicio + 2 métodos nuevos en `SplitFinancieroCalculator` + un campo computado en dos DTOs. `RegistrarCobroDTO` pierde el campo `importeCubiertoOs` (ya no se confía en un valor libre del formulario) — sin impacto porque no tenía otro caller.

### M.5 Regla de negocio nueva: RN-025

| ID | Módulo | Regla |
|---|---|---|
| RN-025 | Facturación | El split 70/30 médico/consultorio (RN-006) y las sumas de Liquidación, Cierre de Caja y Reportes se calculan sobre lo efectivamente cobrado en mano en el consultorio (`Importe_Total` menos lo cubierto por la obra social), no sobre el precio de lista total. El coseguro que la obra social le paga al médico directamente nunca entra a la caja del consultorio y es 100% del médico. |

### M.6 Verificación

- Datos reales consultados en la base viva antes de implementar: médico `ana.gomez` (id 1) tiene OSDE con coseguro $20,00. Ninguna `Facturacion`/`Turno` real fue creada bajo la fórmula vieja de la Parte L (no se confirmó ninguna reserva real durante esa verificación — ver §L.3), así que no hay datos a corregir retroactivamente.
- Con los números del ejemplo ($4.800 / $20 / $4.780): `Facturacion.importeCopago = 4780`, `importeCubiertoOs = 20`; Liquidación con split 70/30 → médico $3.346, consultorio $1.434 (antes hubiera dado $3.360/$1.440 sobre $4.800).

### M.7 Tests

`mvn test` completo el 2026-09-23 después de este cambio: **143/143 verdes** (135 previos + 8 nuevos):
- `FacturacionServiceTest.testRegistrarCobro_CalculaImporteCubiertoOsComoTotalMenosCopago` (renombrado desde `...PersisteImporteCubiertoOsCorrectamente`, ahora verifica el cálculo server-side)
- `FacturacionServiceTest.testRegistrarCobro_ImporteCopagoNuloAsumeQueSeCobroTodoEnMano`
- `FacturacionServiceTest.testCrearFacturacionAutomatica_ConCoseguroExponeImporteCubiertoOsCorrecto`
- `FacturacionServiceTest.testCrearFacturacionAutomatica_SinCopagoPlanificadoAsumeQueSeCobraElTotal` (cubre el fix del fallback, M.3.2)
- `LiquidacionServiceTest.testRN025_SplitSeCalculaSobreLoCobradoEnManoNoSobreElTotal`
- `CierreDiarioServiceTest.testRN025_CierreUsaLoCobradoEnManoNoElTotal`
- `ReporteServiceTest.testReporteFacturacionPorMedico_UsaLoCobradoEnManoYCalculaTotalCubiertoOs`
- `ReporteServiceTest.testReporteFacturacionPorObraSocial_CalculaTotalCubiertoOs`
- `ReporteServiceTest.testRecalcularDashboard_UsaLoCobradoEnManoCuandoHayObraSocial`

Además se corrigieron dos fixtures existentes (`ReporteServiceTest.testReporteFacturacionPorMedico_AgrupaPorMedicoDistinto` y `.testReporteFacturacionPorObraSocial_AgrupaPorObraSocial`) que seteaban `importeCopago` explícito sin relación con `importeTotal` — antes ese campo era decorativo (no se usaba en el cálculo), ahora participa, así que se ajustaron para no testear sin querer la fórmula de RN-025 en un test pensado solo para el agrupamiento.

## Limitaciones conocidas / pendientes (actualizado 2026-09-23)

- **Exportación de reportes a Excel / impresión** — el backend sigue sin endpoint de exportación. Desde el 27/08 hay exportación a **CSV** armada en el cliente (ver §D.2.1); Excel nativo e impresión siguen pendientes.
- **Recuperación de contraseña vía email** con enlace temporal — no implementada.
- **Registro de "quién accedió" a una historia clínica** (RF-H6: auditoría de lectura con usuario/fecha/hora) — no implementado. Desde el 09/09 el acceso sí está *restringido* por rol y pertenencia (un MEDICO solo abre la HC/adjuntos de pacientes propios — §J.2), pero no se registra cada lectura.
- ~~Copago no se prellena automáticamente desde el coseguro configurado por el médico al reservar un turno~~ — **resuelto el 23/09** (§L), con la fórmula corregida el mismo día tras validar el modelo de negocio real (§M).
- **Motivo de cancelación de un día del médico** no se persiste (ver §E.2.5) — viaja solo en la respuesta. Mejora futura: columna `Motivo_Cancelacion` en `Turnos`.
- **Cancelar un día fuera del horizonte de turnos generado** (contratos sin fecha fin generan 6 meses de turnos): si las vacaciones caen más allá de ese horizonte todavía no hay turnos que cancelar. Al generarse esos turnos después no se respeta el bloqueo. Caso de borde; mejora futura sería una tabla de bloqueos que `generarTurnosParaContrato` consulte.
- **Evoluciones anuladas**: al anular una evolución desaparece de la consulta (soft-delete genérico) en vez de quedar visible con badge "Anulada". El frontend tiene el código del badge pero `EvolucionResponseDTO` no expone el estado y las filas anuladas se filtran por `@SQLRestriction`. Sigue pendiente.
- **Dashboard gerencial**: causa raíz reproducida y corregida el 2026-09-02 (Parte F) — el dashboard del día quedaba congelado en la primera consulta de la jornada porque `getDashboardHoy` servía la fila cacheada sin recalcular. Ahora "hoy" siempre recalcula. Pendiente (mejora, no bug): selector de fecha en la UI para ver dashboards históricos (el backend ya lo soporta vía `/dashboard/recalcular?fecha=`), y los indicadores de gráficos / comparativos / mensuales que pide la Entrega (hoy solo los 6 del día).
- Módulo de gestión de Usuarios dedicado: **implementado** (ya no está pendiente — pantalla completa de alta/baja/modificación).
- **Reintegro de un cobro** cuando se cancela un turno ya pagado (paciente en sala de espera que se retira): la factura se anula automáticamente pero el reintegro del dinero se hace **manual** — no hay flujo de reintegro en el sistema (el estado `REINTEGRADO` existe en el modelo pero sin operación que lo use). Mejora futura.
- **Cupo pagado, cancelado y re-reservado** (borde de §J.1): si un turno se cobró, se canceló (factura → `ANULADO`, con nota de reintegro manual) y el **mismo cupo** se re-reserva para otra atención, la factura anulada se reutiliza y vuelve a `PENDIENTE` (la nota de reintegro se conserva en las observaciones). El registro de `Cobro` anterior queda asociado al turno; si esa segunda atención también se cobra, el turno pasa a tener 2 filas en `Cobros` y `CobroRepository.findByTurnoIdTurno` (que devuelve una sola) podría fallar. Caso raro (cobro → cancelación → reasignación del mismo slot); mejora futura sería una factura nueva por atención en vez de reutilizar la fila.
- **Alta de prestación por el médico** crea una entrada nueva en el catálogo `Prestacion_Medica` si el nombre no existe (mismo comportamiento que el alta admin). No hay endpoint para depurar el catálogo de prestaciones sin uso — mejora futura menor.
- Cobertura de tests: `mvn test` corrido completo el 2026-09-23 sobre el estado actual del proyecto — **143/143 tests verdes** (133 de la Parte K + 8 de la Parte M, ver §M.7; entre medio el working tree ya traía 2 tests adicionales sin commitear no relacionados a estas partes).
- **Reintegro de un cobro** (estado `REINTEGRADO`): sigue sin ninguna operación que lo asigne — ver nota preexistente arriba. Los fixes de la Parte K (§K.3) sí lo excluyen defensivamente de las sumas de Liquidación/Cierre/Reportes, igual que ya hacía el Dashboard, por si en el futuro se implementa esa operación.

---

*Última actualización: 2026-09-23. Fuente técnica detallada y viva de todas las decisiones: `docs/spec.md`.*
