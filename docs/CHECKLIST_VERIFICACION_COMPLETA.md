# Checklist Integral de Verificación — MediSpace

**Fecha:** 2026-08-23. **Método:** no ejecutado en vivo — construido cruzando lectura exhaustiva del código actual (13 controllers, 14 services, 9 módulos frontend) contra `docs/spec.md`, la cobertura real de los 87 tests automatizados, y las verificaciones en vivo previas (`docs/VERIFICACION_MODULAR_POST_SPRINT2.md`, 2026-08-10), marcando explícitamente qué de esas verificaciones sigue vigente y qué quedó superado por cambios posteriores.

**Cómo leer las columnas de estado:**
- ✅ **Test automatizado** — hay un `@Test` que lo cubre, corre en cada `mvn test`.
- 🟢 **Confirmado en vivo (vigente)** — se probó contra un servidor real y el código no cambió desde entonces.
- 🟡 **Confirmado en vivo (desactualizado)** — se probó en algún momento, pero el código cambió después; hay que reprobar.
- ⚪ **Nunca probado en vivo** — solo confirmado por lectura de código o por test automatizado; falta la prueba manual/end-to-end.
- 🔴 **Gap conocido** — no implementado o implementado con un problema confirmado.

---

## 1. Hallazgos de esta auditoría — a decidir/arreglar

No son parte del pedido original (Pacientes/Médicos/HC/Reportes ya cubiertos en `docs/ANEXO_CAMBIOS_POST_ENTREGA.md`). Son nuevos, encontrados al armar este checklist. Se documentan acá para no perderlos — **no se arreglaron en esta sesión**.

| # | Hallazgo | Dónde | Severidad |
|---|---|---|---|
| 1 | Un MEDICO autenticado puede leer datos de **otro** médico por id — sin chequeo de ownership (`MedicoAccessGuard` no se invoca en estos 3 endpoints, a diferencia de sus equivalentes `/me/*`) | `GET /api/medicos/{id}`, `GET /api/medicos/{id}/prestaciones`, `GET /api/medicos/{id}/obras-sociales` | **Alta** — exposición de datos de otro profesional (incluye coseguros pactados) |
| 2 | Un MEDICO puede leer el turno de **otro** médico por id (`GET /api/turnos/{id}` no valida ownership, a diferencia de `PUT /api/turnos/{id}/estado` en el mismo controller que sí lo hace) | `TurnoController.java` | **Media** — expone datos de turno/paciente de otro médico |
| 3 | `GET /api/arrendamientos/contratos` no valida ownership — un MEDICO sin pasar `medicoId` recibe **todos** los contratos del sistema (a diferencia de `GET /arrendamientos/medico/{idMedico}`, al lado, que sí lo valida) | `ArrendamientoController.java` | **Media** |
| 4 | `agregarEvolucion` (crear una evolución clínica nueva) no valida autoría — solo `editarEvolucion`/`anularEvolucion`/`agregarAdjunto` aplican RN-010. Un GERENTE o MEDICO podría, en teoría, crear una evolución "como" otro médico | `HistoriaClinicaServiceImpl.java` | **Media** |
| 5 | RN-016 (baja de ObraSocial bloqueada por médicos activos) es la **única** de las 18 reglas sin ningún test automatizado | `ObraSocialServiceTest` (inexistente para baja) | Media (deuda de tests) |
| 6 | `ConsultorioService`/`ConsultorioController` no tienen **ningún** test — ni de servicio ni de controller. Es el único servicio de los 14 sin `*ServiceTest` | — | Media (deuda de tests) |
| 7 | 9 de 13 controllers no tienen ningún `*ControllerTest` — los permisos por rol (`@PreAuthorize`) de esos 9 no están verificados por CI, solo por lectura | Auth, Facturacion, Reporte, Especialidad, Usuario, ObraSocial, Medico, Consultorio, Paciente | Media (deuda de tests) |
| 8 | El mensaje de excepción de RN-016 no incluye su propio tag ("No se puede dar de baja: hay médicos activos asociados...", sin "RN-016:") — inconsistente con el resto de las reglas | `ObraSocialServiceImpl.java:85-87` | Baja (cosmético) |

---

## 2. Resumen ejecutivo por módulo

| Módulo | Reglas de negocio | Con test automatizado | `*ControllerTest` | Última verificación en vivo | ¿Sigue vigente? |
|---|---|---|---|---|---|
| Usuarios | RN-015 | 1/1 | No | 2026-08-10 — decía "no existe ningún endpoint" | 🟡 **No** — hoy hay CRUD+reactivar completo, nunca reprobado en vivo |
| Pacientes | RN-008, RN-009 | 2/2 | No | 2026-08-10 — CRUD (ADMIN) y RN-008/009 PASAN | 🟡 Parcial — el CRUD probablemente sigue vigente, pero el permiso de GERENTE cambió hoy (M1, ver §3.2) y nunca se probó en vivo |
| Médicos | RN-012, RN-017 | 2/2 (RN-017 parcial, ver §1) | No | 2026-08-10 — alta/baja/RN-012 PASAN | 🟡 Parcial — alta/baja probablemente vigente; autoedición (`PUT /me`) y coseguro (RN-017) son funcionalidad agregada después, **nunca probadas en vivo** |
| Turnos | RN-001 a RN-004 | 4/4 | Sí (parcial — solo guard de acceso) | 2026-08-10 — reserva/espera/cancelación/no-asistió PASAN; **marcar Atendido FALLABA (500)** | 🟡 Crítico — la causa raíz (bug de tipos TIME/DATETIME) se documentó como corregida después, pero **nunca se reprobó en vivo el flujo completo**. Máxima prioridad de reverificación |
| Historias Clínicas | RN-010, RN-011 | 2/2 | Parcial (1 test, solo inspecciona la anotación `@PreAuthorize`) | 2026-08-10 — RN-010/011 PASAN; enmascarado de ADMINISTRATIVO confirmado intacto | 🟡 Parcial — RN-010/011 probablemente vigentes; el enmascarado se **revirtió hoy** (M3) — comportamiento opuesto al último confirmado en vivo, nunca reprobado |
| Facturación | RN-006 (cálculo, no excepción) | Sí (indirecto) | No | 2026-08-10 — **circuito automático completo FALLABA** (0 facturaciones generadas nunca, mismo bug que Turnos/Atendido); piezas aisladas (cobro, permiso MEDICO 403) sí confirmadas | 🟡 Crítico — depende del mismo fix de Turnos, nunca reprobado end-to-end en vivo |
| Liquidaciones | RN-005, RN-006, RN-007, RN-018 | 4/4 | Sí (parcial — solo guard) | 2026-08-10 — generar/anular (RN-007) PASAN; RN-005 y RN-018(ex-RN-016) "no confirmables en esa sesión" | 🟡 Parcial — generar/anular probablemente vigente; RN-005/RN-018 nunca confirmadas en vivo (sí por unit test) |
| Arrendamiento (contratos) | RN-013, RN-014 | 2/2 | Sí (parcial — solo guard) | 2026-08-10 — RN-013/014 PASAN, uso/cierre diario confirmados | 🟢 Probablemente vigente — código no tocado desde entonces |
| Consultorios | — | 0/0 (sin test, único servicio sin `*ServiceTest`) | No | 2026-08-10 — decía "no existe cambio de estado" | 🔴 **Nunca probado en vivo** — el endpoint de cambio de estado (H3) se agregó después del último audit, cero cobertura de ningún tipo. Máxima prioridad |
| Reportes | — | — | No | 2026-08-10 — todo PASA (dashboard, facturación por médico/OS, uso consultorios), GERENTE-only confirmado | 🟢 Probablemente vigente — código no tocado, y hoy se confirmó deliberadamente no tocarlo (M4/M5) |
| Obras Sociales | RN-016 | 0/1 (única regla sin test, ver §1) | No | **Nunca** — el módulo es posterior al último audit en vivo (agregado 2026-08-19) | 🔴 **Nunca probado en vivo**, ni una vez. Máxima prioridad |
| Especialidades | — | 2/2 (creación) | No | 2026-08-10 — 100% (alta+listado) | 🟢 Probablemente vigente |

---

## 3. Detalle por módulo

### 3.1 Usuarios

**Qué hace (RF-U1 a RF-U5):** alta manual (email/password/rol), baja lógica reactivable, modificación (rol solo editable por GERENTE), búsqueda con filtros, passwords hasheadas.

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/usuarios` | GERENTE, ADMINISTRATIVO | ⚪ Sin reverificar en vivo desde el fix |
| `PUT /api/usuarios/{id}` | GERENTE, ADMINISTRATIVO (cambio de rol restringido a GERENTE dentro del service) | ⚪ |
| `GET /api/usuarios/{id}` | GERENTE, ADMINISTRATIVO | ⚪ |
| `GET /api/usuarios` (filtros email/rol/incluirInactivos) | GERENTE, ADMINISTRATIVO | ⚪ |
| `DELETE /api/usuarios/{id}` | GERENTE, ADMINISTRATIVO | ⚪ |
| `PUT /api/usuarios/{id}/reactivar` | GERENTE, ADMINISTRATIVO | ⚪ |

**Regla de negocio:** RN-015 (único rol activo) ✅ testeada (`UsuarioServiceTest`, 3 casos: rol vacío, rol inválido, múltiple). Nota: el único caller real de la validación hoy es `MedicoServiceImpl.crearMedico` (rol hardcodeado MEDICO) — las ramas de rechazo son alcanzables solo indirectamente.

**Frontend (`usuarios.js`):** Nuevo/Guardar/Editar/Baja/Reactivar, búsqueda con debounce. Rol GERENTE/ADMINISTRATIVO solo en el dropdown de alta (MEDICO se crea desde el módulo Médicos). Select de rol deshabilitado en edición salvo `getRol()==='GERENTE'`, y siempre deshabilitado si el usuario editado es MEDICO.

**Qué falta probar:**
- [ ] Alta/edición/baja/reactivación completa como GERENTE y como ADMINISTRATIVO.
- [ ] Confirmar que ADMINISTRATIVO **no puede** cambiar el rol de otro usuario (debe fallar server-side aunque el POST/PUT en sí den 200).
- [ ] Búsqueda con cada filtro (email, rol, incluir inactivos).
- [ ] Confirmar que un MEDICO no puede acceder a ningún endpoint de este módulo (403).

---

### 3.2 Pacientes

**Qué hace (RF-P1 a RF-P3):** alta con creación automática de HC vacía, DNI inmutable (RN-008) y único incluso inactivo (RN-009).

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/pacientes` | **Solo ADMINISTRATIVO** (M1, hoy — antes incluía GERENTE) | ⚪ Nunca probado en vivo con el permiso nuevo |
| `PUT /api/pacientes/{id}` | Solo ADMINISTRATIVO (M1) | ⚪ |
| `DELETE /api/pacientes/{id}` | Solo ADMINISTRATIVO (M1) | ⚪ |
| `GET /api/pacientes/{id}` | GERENTE, ADMINISTRATIVO, MEDICO (MEDICO filtrado a pacientes vinculados) | 🟢 CRUD base confirmado 2026-08-10, probablemente vigente |
| `GET /api/pacientes` | ídem | 🟢 (filtro por médico, ver H1 más abajo) |

**Reglas de negocio:** RN-008 (DNI inmutable) ✅ testeada + 🟢 confirmada en vivo 2026-08-10. RN-009 (DNI único incluso inactivo) ✅ testeada + 🟢 confirmada en vivo 2026-08-10.

**H1 (resuelto, confirmar en vivo):** `PacienteServiceImpl.listarPacientes` ahora filtra por médico vía `findVinculadosAMedico` cuando el caller es MEDICO (antes hacía `findAll()` sin acotar). Nunca reprobado en vivo desde el fix.

**Frontend (`pacientes.js`):** Nuevo/Guardar/Editar/Historia Clínica/Baja. GERENTE: botones Nuevo/Editar/Baja ocultos (`puedeEditar`), ícono de Historia Clínica oculto (`puedeVerHC`) — defensa en profundidad agregada hoy junto con M1.

**Qué falta probar:**
- [ ] **GERENTE intentando crear/editar/dar de baja un paciente → debe dar 403** (cambio de hoy, M1).
- [ ] GERENTE no ve los botones de alta/edición/baja en la UI (solo lectura).
- [ ] MEDICO ve únicamente pacientes vinculados a sus turnos/HC, no el listado completo (H1).
- [ ] RN-008/RN-009 con datos frescos (aunque ya confirmadas antes, el código no cambió — chequeo rápido).

---

### 3.3 Médicos

**Qué hace (RF-M1 a RF-M5):** alta con usuario auto-creado (password=matrícula), agenda vía Contrato de Arrendamiento (no autónoma), relación N:M con obras sociales (coseguro decidido por el propio médico), relación con prestaciones, baja bloqueada por turnos futuros (RN-012).

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/medicos` | GERENTE | 🟢 Confirmado en vivo 2026-08-10 |
| `PUT /api/medicos/{id}` | GERENTE, ADMINISTRATIVO (sin distinción de campos entre ambos) | 🟢 Confirmado 2026-08-10 |
| `GET /api/medicos/me` / `PUT /api/medicos/me` | MEDICO | ⚪ `PUT` no existía en el audit anterior ("no existe autoedición") — es funcionalidad agregada después, nunca probada en vivo |
| `GET /api/medicos/me/prestaciones` | MEDICO | ⚪ |
| `GET /api/medicos/{id}` | GERENTE, ADMINISTRATIVO, MEDICO — **⚠️ sin ownership check, ver hallazgo §1.1** | ⚪ |
| `GET /api/medicos` (incluirInactivos) | GERENTE, ADMINISTRATIVO | 🟢 |
| `DELETE /api/medicos/{id}` | GERENTE | 🟢 RN-012 confirmada con datos reales (208 turnos futuros) |
| `PUT /api/medicos/{id}/reactivar` | GERENTE | ⚪ H2, agregado después del último audit en vivo |
| `GET/POST/PUT/DELETE .../prestaciones` | GERENTE (alta/mod/baja), lectura para los 3 roles — **⚠️ lectura sin ownership, ver §1.1** | ⚪ |
| `GET/POST/PUT/DELETE .../obras-sociales` | GERENTE+ADMINISTRATIVO (alta/mod/baja), lectura para los 3 — **⚠️ lectura sin ownership, ver §1.1** | ⚪ Funcionalidad nueva (Obras Sociales), nunca probada en vivo |
| `GET /api/medicos/me/obras-sociales` / `PUT .../me/obras-sociales/{id}` | MEDICO (RN-017: solo su propia relación) | ⚪ Nunca probada en vivo |

**Reglas de negocio:** RN-012 (baja bloqueada por turnos futuros) ✅ testeada + 🟢 confirmada en vivo con datos reales. RN-017 (coseguro solo propio) ✅ testeada (parcial, ver §1) + ⚪ nunca probada en vivo.

**Frontend (`medicos.js`):** CRUD completo + filas dinámicas de prestaciones/obras sociales + alta rápida de especialidad/obra social inline. Sección "Mis Datos" (autoedición, solo nombre/apellido editable — especialidad e importe consulta son admin-only) y "Mis Prestaciones" (solo lectura) para MEDICO.

**Qué falta probar:**
- [ ] **Un MEDICO pidiendo `GET /medicos/{id}` de OTRO médico → hoy devuelve 200 con datos ajenos (hallazgo §1.1). Confirmar el alcance real antes de decidir si se arregla.**
- [ ] Autoedición de "Mis Datos" (nombre/apellido) como MEDICO, confirmar que especialidad/importe NO se pueden tocar desde ahí.
- [ ] Alta/edición/baja de relación médico-obra social como GERENTE/ADMINISTRATIVO.
- [ ] Edición de coseguro propio como MEDICO, y confirmar 400 al intentar editar el de otro médico.
- [ ] H2: reactivar médico dado de baja, confirmar que vuelve a poder loguearse.

---

### 3.4 Turnos

**Qué hace (RF-T1 a RF-T7):** generación automática al crear contrato de arrendamiento, reserva, ciclo de estados completo, cancelación con reglas de horario.

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/turnos/{id}/reservar` | GERENTE, ADMINISTRATIVO | 🟢 Confirmado 2026-08-10, incluye `idPrestacion` (H5 ya funcionaba en backend, frontend se completó después) |
| `PUT /api/turnos/{id}/estado` | GERENTE, ADMINISTRATIVO, MEDICO (con ownership) | 🟡 **En espera/Cancelado/No Asistió confirmados 🟢; transición a ATENDIDO FALLABA (500) el 2026-08-10 — causa raíz corregida después, nunca reprobada en vivo. MÁXIMA PRIORIDAD.** |
| `GET /api/turnos/{id}` | GERENTE, ADMINISTRATIVO, MEDICO — **⚠️ sin ownership check, ver §1.2** | ⚪ |
| `GET /api/turnos` (filtros) | ídem, con ownership para MEDICO en el listado | 🟢 |
| `DELETE /api/turnos/{id}` | GERENTE, ADMINISTRATIVO | ⚪ (dead binding en frontend, ver §6 — nunca se dispara desde la UI) |

**Reglas de negocio:** RN-001 a RN-004, las 4 ✅ testeadas. RN-001/002/003 🟢 confirmadas en vivo 2026-08-10. **RN-004 no era verificable en vivo el 2026-08-10 porque ningún turno llegaba a ATENDIDO** (el estado que protege era inalcanzable) — sigue pendiente de una confirmación en vivo real ahora que (presuntamente) el bug de Atendido está resuelto.

**Frontend (`turnos.js`):** navegación por día, filtro de médicos (checkbox popover), reserva con alta rápida de paciente inline, cambios de estado con confirmación para Cancelar/No Asistió. Sin gating de rol en el módulo (depende 100% del sidebar — GERENTE no tiene ítem de navegación a Turnos).

**Qué falta probar (prioridad máxima del checklist completo):**
- [ ] **Flujo A completo: reservar → En Espera → marcar Atendido → confirmar que YA NO da 500 y que se genera la Facturación automáticamente.** Este es el hallazgo más grave del audit anterior; nunca se confirmó que el fix funcione end-to-end.
- [ ] RN-004 con un turno real en ATENDIDO (intentar revertir o cancelar retroactivamente, debe fallar).
- [ ] Cancelación antes/después del horario (Flujo B) — probablemente vigente pero sin reconfirmar recientemente.
- [ ] Guardia IDOR: MEDICO no puede cambiar estado de turno ajeno (cubierto por test, confirmar en vivo).
- [ ] **Un MEDICO pidiendo `GET /turnos/{id}` de un turno ajeno → hoy devuelve datos (hallazgo §1.2).**

---

### 3.5 Historias Clínicas

**Qué hace (RF-H1 a RF-H6):** HC única autogenerada, evolución por atención (solo el médico responsable la edita), adjuntos, anulación con motivo (no se borra), auditoría.

| Endpoint | Rol | Estado |
|---|---|---|
| `GET /api/historias-clinicas/paciente/{id}` | ADMINISTRATIVO, MEDICO (**GERENTE explícitamente excluido**) | 🟢 Confirmado 2026-08-10 (GERENTE→403); **contenido para ADMINISTRATIVO cambió hoy: antes enmascarado, ahora completo (M3) — nunca reprobado en vivo** |
| `POST .../evoluciones` | GERENTE, MEDICO — sin chequeo de autoría al crear (hallazgo §1.4) | 🟢 Confirmado 2026-08-10 (como MEDICO) |
| `PUT .../evoluciones/{id}` | GERENTE, MEDICO (RN-010) | 🟢 Confirmado 2026-08-10 |
| `DELETE .../evoluciones/{id}/anular` | GERENTE, MEDICO (RN-010 + RN-011) | 🟢 Confirmado 2026-08-10 |
| `POST .../evoluciones/{id}/adjuntos` | ADMINISTRATIVO, MEDICO | ⚪ |
| `GET .../adjuntos/{id}/descargar` | ADMINISTRATIVO, MEDICO | ⚪ |

**Reglas de negocio:** RN-010 (autoría) ✅ testeada + 🟢 confirmada en vivo (editar Y anular). RN-011 (motivo obligatorio) ✅ testeada + 🟢 confirmada en vivo.

**Gap ya documentado, no tocado:** una evolución anulada desaparece por completo de la consulta (soft-delete genérico) en vez de quedar visible con badge "Anulada" — confirmado en vivo 2026-08-10, no se tocó desde entonces, presumiblemente sigue igual.

**Inconsistencia frontend/backend a testear:** `historias.js` calcula `canEdit` incluyendo GERENTE (mostraría botones de edición), pero GERENTE no tiene ítem de sidebar hacia Historias Clínicas y el backend igual lo excluye del GET — código de UI aparentemente inalcanzable, pero vale confirmar que no hay una vía de acceso indirecta.

**Qué falta probar:**
- [ ] **ADMINISTRATIVO viendo una HC → confirmar que ahora ve el texto clínico completo (motivo/diagnóstico/tratamiento/etc.), no `null` (M3, hoy).**
- [ ] GERENTE sigue sin poder ver ninguna HC (403).
- [ ] Adjuntos: subir (PDF/JPG/PNG), rechazar otra extensión, descargar.
- [ ] Confirmar si `agregarEvolucion` sin chequeo de autoría es explotable en la práctica (hallazgo §1.4).

---

### 3.6 Facturación

**Qué hace (RF-F1, RF-F2, RF-F6, RF-F7):** facturación automática al marcar turno Atendido, registro de cobro, estados (Pendiente/Pagado/Parcial/Anulado/Reintegrado).

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/facturacion/{id}/cobro` | GERENTE, ADMINISTRATIVO | 🟢 Confirmado 2026-08-10 (con datos insertados manualmente, ver nota) |
| `GET /api/facturacion/{id}` | GERENTE, ADMINISTRATIVO | ⚪ |
| `GET /api/facturacion` | GERENTE, ADMINISTRATIVO (**MEDICO sin acceso, confirmado por diseño**) | 🟢 Confirmado 2026-08-10 (MEDICO→403) |

**Regla de negocio:** RN-006 (split 70/30 centralizado en `SplitFinancieroCalculator`) ✅ testeada indirectamente (`FacturacionServiceTest`, `SplitFinancieroCalculatorTest`, `CierreDiarioServiceTest`).

**🔴 El hallazgo más grave del audit 2026-08-10 fue acá:** cero facturaciones se generaban nunca en toda la base, porque el disparador automático (turno→Atendido) fallaba el 100% de las veces por un bug de tipos TIME/DATETIME en `findContratoVigente`. El resto del módulo (cobro, permisos) se probó insertando una factura de prueba manualmente por SQL y **sí funcionaba aislado**. El plan de sesiones posteriores documenta que ese bug se corrigió (query nativa con `CAST`), pero **nunca se reprobó el circuito automático completo end-to-end en vivo desde entonces.**

**Qué falta probar:**
- [ ] **Turno → Atendido → confirmar que la Facturación se genera SOLA (sin insertar nada por SQL), con el split correcto según el contrato vigente del médico.**
- [ ] Registrar cobro con cada método de pago — confirmar que Transferencia queda PENDIENTE y el resto PAGADO inmediatamente.
- [ ] MEDICO sin acceso a ningún endpoint de este módulo (403).

---

### 3.7 Liquidaciones

**Qué hace (RF-F3 a RF-F5):** liquidación por período, split 70/30, inmutabilidad tras emisión (RN-007).

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/liquidaciones/generar` | GERENTE, ADMINISTRATIVO | 🟢 Confirmado 2026-08-10 |
| `PUT /api/liquidaciones/{id}/anular` | **Solo GERENTE** (ADMINISTRATIVO puede generar pero no anular — asimetría intencional) | 🟢 Confirmado 2026-08-10 (RN-007) |
| `GET /api/liquidaciones/medico/{id}` | GERENTE, ADMINISTRATIVO, MEDICO (ownership) | ⚪ |
| `GET /api/liquidaciones/me` | MEDICO | ⚪ |
| `GET /api/liquidaciones` (filtros) | GERENTE, ADMINISTRATIVO | ⚪ |
| `GET /api/liquidaciones/{id}` | GERENTE, ADMINISTRATIVO, MEDICO (ownership) | ⚪ |

**Reglas de negocio:** RN-005 (no liquida con facturas pendientes) ✅ testeada, ⚪ "no confirmable" en el audit anterior por falta de datos de prueba adecuados — sigue pendiente en vivo. RN-006 (split) ✅ testeada. RN-007 (emitida inmutable) ✅ testeada + 🟢 confirmada en vivo. **RN-018** (turnos atendidos sin cobro directo, ex-"RN-016" renombrada hoy) ✅ testeada, ⚪ "no se pudo ejecutar" en el audit anterior (requería un turno ATENDIDO, inalcanzable en ese momento) — sigue pendiente en vivo.

**Frontend (`liquidaciones.js`):** vista admin (GERENTE+ADMINISTRATIVO) vs. vista MEDICO ("Mis Liquidaciones"), filtros por médico/fecha. **Anular solo visible si `getRol()==='GERENTE'`** — ADMINISTRATIVO ve la lista pero no el botón, distinto del criterio binario admin/médico usado en el resto del módulo.

**Qué falta probar:**
- [ ] Generar liquidación con datos reales de un período con facturas cobradas.
- [ ] RN-005 con una factura deliberadamente dejada PENDIENTE en el período.
- [ ] RN-018 con un turno ATENDIDO real sin ningún cobro asociado.
- [ ] ADMINISTRATIVO generando una liquidación pero **sin poder anularla** (confirmar 403, y que el botón no aparece en la UI).
- [ ] MEDICO viendo únicamente sus propias liquidaciones vía `/me`.

---

### 3.8 Arrendamiento (contratos)

**Qué hace (RF-A1 a RF-A5):** ABM de consultorios, contrato médico-consultorio con generación automática de turnos, validación de superposición (RN-013), mínimo 4hs (RN-014), registro de uso y cierre diario.

| Endpoint | Rol | Estado |
|---|---|---|
| `POST /api/arrendamientos` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |
| `GET /api/arrendamientos/{id}` | GERENTE, ADMINISTRATIVO | ⚪ |
| `GET /api/arrendamientos/medico/{id}` | GERENTE, ADMINISTRATIVO, MEDICO (ownership, IDOR guard reconfirmado en 2026-08-10) | 🟢 |
| `GET /api/arrendamientos/contratos` | GERENTE, ADMINISTRATIVO, MEDICO — **⚠️ sin ownership check, ver §1.3** | ⚪ |
| `PUT /api/arrendamientos/{id}/baja` | Solo GERENTE | ⚪ |
| `POST /api/arrendamientos/uso` | GERENTE, ADMINISTRATIVO | 🟢 Confirmado 2026-08-10 (dead binding en frontend, ver §6) |
| `POST /api/arrendamientos/cierre-diario` | GERENTE, ADMINISTRATIVO | 🟢 Confirmado 2026-08-10, split correcto (dead binding en frontend, ver §6) |

**Reglas de negocio:** RN-013 (superposición) ✅ testeada + 🟢 confirmada en vivo. RN-014 (mínimo 4hs) ✅ testeada + 🟢 confirmada en vivo.

**Frontend (`arrendamientos.js`):** vista Consultorios (grid con cambio de estado) + vista Contratos (tabla con filtros médico/estado). "Nuevo Contrato" oculto salvo GERENTE. "Dar de baja" **sin gating de rol en el JS** — visible para cualquiera que llegue a la vista con una fila ACTIVA, aunque MEDICO también tiene acceso al ítem de sidebar "Contratos".

**Qué falta probar:**
- [ ] Flujo C completo: alta médico → crear contrato → confirmar generación automática de turnos.
- [ ] RN-013/RN-014 con datos frescos (probablemente vigentes, confirmación rápida).
- [ ] **Un MEDICO en la vista Contratos: confirmar si el botón "Dar de baja" realmente funciona o el backend lo rechaza — no hay gating explícito en el JS.**
- [ ] `GET /arrendamientos/contratos` como MEDICO sin filtro → confirmar el alcance real de la exposición (hallazgo §1.3).

---

### 3.9 Consultorios

**Qué hace (RF-A1, RF-A6):** ABM de consultorios físicos, estados (Disponible/Ocupado/Bloqueado/En mantenimiento/Fuera de servicio).

| Endpoint | Rol | Estado |
|---|---|---|
| `GET /api/consultorios` | GERENTE, ADMINISTRATIVO, MEDICO | 🟢 Confirmado 2026-08-10 |
| `POST /api/consultorios` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |
| `PUT /api/consultorios/{id}/estado` | GERENTE, ADMINISTRATIVO | 🔴 **Nunca probado en vivo ni por test automatizado** — H3 se agregó después del último audit; `ConsultorioService` es el único de los 14 servicios sin ningún test |

**Frontend (`arrendamientos.js`, vista Consultorios):** "Nuevo Consultorio" solo GERENTE; selector de cambio de estado por tarjeta visible para GERENTE+ADMINISTRATIVO (MEDICO ve solo lectura).

**Qué falta probar (prioridad alta — cero cobertura de cualquier tipo):**
- [ ] Crear consultorio, confirmar unicidad de número.
- [ ] **Cambiar estado a cada valor (Bloqueado/En mantenimiento/Fuera de servicio) como GERENTE y como ADMINISTRATIVO.**
- [ ] Confirmar que "Ocupado" no es un estado seteable manualmente (debe ser derivado).
- [ ] MEDICO no puede cambiar estado (solo lectura).

---

### 3.10 Reportes y Dashboard Gerencial

**Qué hace (RF-R1 a RF-R4):** dashboard precalculado, reportes de facturación por médico/obra social, reporte de uso de consultorios.

| Endpoint | Rol | Estado |
|---|---|---|
| `GET /api/reportes/dashboard` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |
| `POST /api/reportes/dashboard/recalcular` | Solo GERENTE | 🟢 Confirmado 2026-08-10 (dead binding en frontend, ver §6) |
| `GET /api/reportes/facturacion/medico` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |
| `GET /api/reportes/facturacion/obra-social` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |
| `GET /api/reportes/consultorios` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |

**Nota de código (no confirmada en vivo):** `reporteUsoConsultorios` usa un split 70/30 **hardcodeado** en vez de leer los porcentajes reales del contrato — puede divergir de Liquidación/Facturación para el mismo período. Vale una comparación cruzada.

**Confirmado hoy (M4/M5, sin cambios de código):** los 3 reportes puntuales que el documento original le da a Administrativo ("Turnos por fecha", "Pacientes ausentes", "Cobros pendientes") no viven acá — ya son alcanzables por Administrativo vía `GET /turnos` y `GET /facturacion` con filtros. RF-R3 (exportación a Excel) no está implementado para ningún rol.

**Qué falta probar:**
- [ ] Recalcular dashboard para una fecha con movimiento real del día, comparar contra los números esperados.
- [ ] Comparar `reporteUsoConsultorios` contra un Cierre Diario del mismo período (por el split hardcodeado).
- [ ] ADMINISTRATIVO y MEDICO reciben 403 en los 5 endpoints.

---

### 3.11 Obras Sociales

**Qué hace (RF-OS1 a RF-OS5):** catálogo propio, baja lógica reactivable bloqueada por médicos activos (RN-016), selección desde catálogo (no texto libre).

| Endpoint | Rol | Estado |
|---|---|---|
| `GET /api/obras-sociales` (filtros nombre/requiereBono/incluirInactivas) | GERENTE, ADMINISTRATIVO, MEDICO | ⚪ Módulo agregado después del último audit en vivo (2026-08-19) |
| `POST /api/obras-sociales` | GERENTE, ADMINISTRATIVO | ⚪ |
| `PUT /api/obras-sociales/{id}` | GERENTE, ADMINISTRATIVO | ⚪ |
| `DELETE /api/obras-sociales/{id}` | GERENTE, ADMINISTRATIVO (RN-016) | ⚪ **Sin test automatizado (único caso de las 18 reglas), sin confirmación en vivo — máxima prioridad** |
| `PUT /api/obras-sociales/{id}/reactivar` | GERENTE, ADMINISTRATIVO | ⚪ |

**Regla de negocio:** RN-016 (baja bloqueada si tiene médicos activos asociados) — implementada (`ObraSocialServiceImpl.java:85-87`), **sin ningún test automatizado y nunca probada en vivo.**

**Frontend (`obrasSociales.js`):** CRUD completo, filtros, reactivar. **Sin ningún `getRol()` en el módulo** — a diferencia de Pacientes/Usuarios/Médicos, no hay gating de rol en el JS (depende 100% del backend + sidebar).

**Qué falta probar (prioridad alta — módulo entero sin ninguna verificación):**
- [ ] Alta/edición/baja/reactivación completa.
- [ ] **RN-016: intentar dar de baja una obra social con al menos un médico activo asociado → debe bloquear con el mensaje correspondiente.**
- [ ] Unicidad de nombre (incluso tras edición).
- [ ] Selección desde catálogo (no texto libre) al asociar a un Médico o Paciente.

---

### 3.12 Especialidades (catálogo de apoyo, no es módulo propio en spec.md)

| Endpoint | Rol | Estado |
|---|---|---|
| `GET /api/especialidades` | GERENTE, ADMINISTRATIVO (**MEDICO sin acceso**, aunque los médicos tienen especialidad) | 🟢 Confirmado 2026-08-10 |
| `POST /api/especialidades` | Solo GERENTE | 🟢 Confirmado 2026-08-10 |

**Qué falta probar:**
- [ ] Confirmar que MEDICO no necesita este endpoint en ningún flujo propio (su especialidad ya viene en `GET /medicos/me`).

---

## 4. Matriz de permisos condensada (módulo × rol)

| Módulo | GERENTE | ADMINISTRATIVO | MEDICO |
|---|---|---|---|
| Usuarios | CRUD completo | CRUD completo (no cambia rol de otros) | Sin acceso |
| Pacientes | **Solo lectura** (M1) | CRUD completo | Lectura filtrada a vinculados |
| Médicos | CRUD completo | Ver + editar (sin distinción de campos), sin alta/baja | Autoedición parcial (`/me`) + coseguro propio |
| Turnos | Sin acceso (sin ítem de sidebar) | Reservar, cambiar estado, eliminar | Cambiar estado de los propios |
| Historias Clínicas | **Sin acceso** (ni lectura) | Lectura completa (M3) + adjuntos | Lectura + escritura de las propias |
| Facturación | Ver + cobrar | Ver + cobrar | Sin acceso |
| Liquidaciones | Generar + **anular** | Generar (no anula) | Solo las propias (`/me`) |
| Arrendamiento (contratos) | CRUD completo | Ver + uso/cierre diario, sin alta/baja de contrato | Ver las propias + acceso a "Contratos" (baja sin gating claro, ver hallazgo §1.3) |
| Consultorios | CRUD + cambio de estado | Ver + cambio de estado, sin alta | Solo lectura |
| Reportes/Dashboard | Acceso total | Sin acceso | Sin acceso |
| Obras Sociales | CRUD completo | CRUD completo | Solo lectura (catálogo) |
| Especialidades | Ver + alta | Solo ver | Sin acceso |

---

## 5. Checklist de flujos end-to-end

Expande el checklist ya existente en el plan `donde-esta-mi-web-wise-knuth.md`, con foco en lo nunca reprobado desde el último audit en vivo.

1. **Flujo A (crítico — el que falló el 2026-08-10):** alta paciente → reservar turno → En Espera → evolución clínica → **Atendido → confirmar que la Facturación se genera sola** → cobro → liquidación.
2. **Flujo B:** cancelar antes/después del horario, No Asistió.
3. **Flujo C:** alta médico → login inmediato (password=matrícula) → crear contrato → turnos generados automáticamente.
4. **Obras Sociales (nunca probado):** alta/edición/baja/reactivar; confirmar bloqueo de baja con médico activo asociado (RN-016).
5. **Coseguro médico-obra social (nunca probado):** asociar con coseguro; el médico edita el propio; confirmar 403/400 al intentar editar el de otro.
6. **Permisos M1-M5 (cambios de hoy, nunca probados en vivo):** GERENTE solo-lectura en Pacientes; Administrativo con HC completa; Administrativo sin acceso a Reportes/export.
7. **Consultorios (nunca probado):** cambio de estado a cada valor posible.
8. **Los 3 gaps de ownership del hallazgo §1** (medicos/{id}, turnos/{id}, arrendamientos/contratos) — confirmar alcance real con un MEDICO autenticado.

---

## 6. Apéndice — bindings de API sin ningún botón que los dispare

Encontrados por el agente de frontend — no tienen forma de probarse desde la UI hoy; si se quieren cubrir, hay que golpear el endpoint directamente (Postman/curl) o decidir si son código muerto a eliminar:

- `Api.deleteTurno` → `DELETE /api/turnos/{id}`
- `Api.registrarUso` → `POST /api/arrendamientos/uso`
- `Api.generarCierreDiario` → `POST /api/arrendamientos/cierre-diario`
- `Api.getArrendamientos(idMedico)` → `GET /api/arrendamientos/medico/{id}` (superseded por `getContratos`)
- `Api.recalcularDashboard` → `POST /api/reportes/dashboard/recalcular`
- `Api.getLiquidaciones(idMedico)` → `GET /api/liquidaciones/medico/{id}` (superseded por `buscarLiquidaciones`/`getMisLiquidaciones`)
- `Api.getMedicoObrasSociales(idMedico)` → `GET /api/medicos/{id}/obras-sociales`

---

*Fuentes: 3 agentes de exploración (backend, frontend, cobertura de tests) del 2026-08-23, `docs/spec.md`, `docs/VERIFICACION_MODULAR_POST_SPRINT2.md` (2026-08-10), y confirmación directa por lectura de código de que H1-H5 del plan `donde-esta-mi-web-wise-knuth.md` ya están resueltos.*
