# Verificación Modular Post-Sprint 2 — MediSpace

> Relevamiento ejecutado de verdad (no leído) sobre los 8 módulos de la sección 4 de `docs/MediSpace_Propuesta_Tecnica_y_BD (1).md`, más el módulo transversal de Catálogos de Referencia y los 3 flujos integrales de la sección 9. Diagnóstico puro — **no se modificó ningún archivo de código fuente ni de configuración**. Fecha: 2026-08-10.

## Metodología y entorno de prueba (leer primero — condiciona todo lo demás)

El servidor MCP `graphify-ts` no estuvo disponible en esta sesión (confirmado con búsqueda de herramientas). Siguiendo el fallback de `CLAUDE.md`, se leyó primero `graphify-out/GRAPH_REPORT.md` y luego se procedió con lectura directa de código y, sobre todo, **ejecución real** contra un servidor Spring Boot vivo — vía `curl`, no MockMvc ni tests, porque el proyecto no tiene infraestructura `@SpringBootTest` (ver sección 6).

**Hallazgo de entorno #1 — el proceso que el usuario viene probando está desactualizado.** Antes de poder ejecutar nada, se detectó que el proceso Java ya escuchando en el puerto 8080 (PID 19380) arrancó el **2026-08-05 17:18**, es decir *antes* de que se completara el audit de Sprint 1 (2026-08-06) y *antes* del commit único con el estado final de Sprint 2 (2026-08-07 19:46). Ese proceso sirve bytecode que quedó cargado en memoria esa tarde de agosto; el código en disco (`target/classes`, y el único commit del repo) se recompiló varias veces después, durante Sprint 1 y Sprint 2, sin que ese proceso se reiniciara nunca. Ver sección "Causa raíz #0" para el desarrollo completo — es, con evidencia de primera mano, la explicación de al menos 3 de los 6 reportes del usuario.

**Hallazgo de entorno #2 — el código actual no arranca contra la base real.** Al intentar levantar el código actual (`mvn spring-boot:run`) contra la base `MediSpace` real, Hibernate lo rechazó en el arranque: *"Schema-validation: missing column [Importe_Copago_Planificado] in table [Turnos]"*. La migración que agrega esa columna (`docs/migrations/2026-08-07_turnos_datos_reserva.sql`) existe, está escrita y probada, pero **nunca se ejecutó** contra ninguna base.

**Cómo se resolvió para poder ejecutar el resto del audit (con autorización explícita del usuario):**
1. Se clonó la base real `MediSpace` a una base aislada `MediSpaceTest` vía `BACKUP`/`RESTORE` nativo de SQL Server (copia exacta de datos y estructura al momento del clonado).
2. Se aplicó la migración pendiente **únicamente** sobre `MediSpaceTest` (nunca sobre `MediSpace`).
3. Se levantó el código actual (post Sprint 2) contra `MediSpaceTest`, puerto **8081**. Este es el proceso usado para prácticamente todo el relevamiento de este documento.
4. El proceso real del usuario (puerto **8080**, base `MediSpace`) se dejó intacto — no se lo reinició ni se le aplicó la migración — y se usó puntualmente, de forma controlada, para confirmar 2 de los reportes (#1 y comportamiento general) tal como el usuario los experimenta hoy.

**Usuarios de prueba usados** (documentado para reproducibilidad — todos ya existían en la base real excepto donde se indica):
- GERENTE: `admin@medispace.com` / `admin123` (ya existía).
- ADMINISTRATIVO: `secretaria@medispace.com` / `Test1234!` — password reseteado **solo en el clon `MediSpaceTest`** vía SQL directo, porque no existe ningún endpoint de alta/gestión de usuarios (ver 4.1) y por lo tanto tampoco una vía para conocer o resetear la contraseña real de otro modo.
- MEDICO: `ana.gomez@medispace.com` / `MP1001`, `carlos.ruiz@medispace.com` / `MP2002`, `policarogabriel@gmail.com` / `M-105` — las 3 cuentas ya existían; la contraseña es la matrícula (valor por defecto al alta, `MedicoServiceImpl.java:46`), nunca cambiada porque no existe endpoint de autoedición.
- No se reintrodujo el rol PACIENTE como cuenta de login en ningún momento.

Los porcentajes de la sección siguiente son una estimación cualitativa a partir de la clasificación funcionalidad-por-funcionalidad de cada módulo, igual que en la auditoría previa — no surgen de una métrica automatizada.

---

## 1. Resumen ejecutivo

| Módulo | % funcionalidades verificadas OK | RN que fallan | Reporte del usuario resuelto | Severidad |
|---|---|---|---|---|
| 4.1 Usuarios | ~10% (sin cambios respecto al audit previo) | RN-015 inalcanzable en la práctica (código muerto) | — | Media |
| 4.2 Pacientes | ~85% | Ninguna (RN-008/RN-009 PASAN) | — | Media (gaps de permisos preexistentes, no de Sprint 2) |
| 4.3 Médicos | ~75% | Ninguna (RN-012 corregida y confirmada) | #4 (resuelto — ver Catálogos) | Media |
| 4.4 Turnos | ~65% (la transición más crítica, Atendido, falla 100%) | RN-004 no verificable end-to-end (el estado que protege es inalcanzable) | #2 (**FALLA**, causa raíz confirmada) · #5 (**SOLO FRONTEND**) | **Crítica** |
| 4.5 Historias Clínicas | ~90% | Ninguna | — | Baja-Media |
| 4.6 Facturación | 0% del circuito automático real · ~90% de las piezas aisladas (cobro/liquidación) | RN-005 no confirmable con datos reales · RN-016 no ejecutable | #6 (**FALLA**, misma causa raíz que #2) | **Crítica** |
| 4.7 Reportes | 100% | Ninguna | — | Baja |
| 4.8 Arrendamiento | ~90% en código actual · 0% en el proceso real que usa el usuario | Ninguna en código actual (RN-013/RN-014 PASAN) | #1 (**FALLA en el proceso real** · **PASA en código actual**) | **Crítica** (por el proceso desactualizado) |
| Catálogos (Especialidad/ObraSocial) | Especialidad 100% · ObraSocial 50% (solo lectura) | — | #3 y #4 (**diagnosticados con causa raíz exacta**) | Media |

**Lectura de una línea:** de los 6 reportes del usuario, **4 fallan de verdad hoy** (#1, #2, #5, #6 — #5 es solo un campo de formulario faltante, los otros tres comparten origen), **2 son gaps de diseño confirmados y acotados con precisión** (#3, #4), y **ninguno de los 6 es un misterio** — todos tienen causa raíz identificada con archivo:línea o con evidencia de log real.

---

## 2. Módulo 4.1 — Usuarios

**Funcionalidades (sección 4.1):** Alta / Baja / Modificación / Consulta de usuarios.

`UsuarioController.java` (líneas 8-14) es un `@RestController` con `@RequestMapping("/api/usuarios")` **sin un solo método** — ni un `@GetMapping`, ni `@PostMapping`, nada. Confirmado en vivo, no solo por lectura:

- `GET /api/usuarios` (con JWT válido de GERENTE o ADMINISTRATIVO) → **HTTP 200**, pero `Content-Type: text/html` — el body es literalmente la página de login (`index.html`). **NO FALLA** con un 404 limpio.
- `POST /api/usuarios` → **HTTP 500**, `"Ocurrió un error inesperado. Intente nuevamente."`

Esto no es un matiz cosmético: revela un bug transversal real (ver "Hallazgo transversal" al final) — `WebMvcConfig.java:19-34` registra un resource handler para `/**` con fallback a `index.html` para cualquier `GET` no mapeado, incluso bajo `/api/**`. Cualquier endpoint GET faltante en cualquier módulo del sistema hoy devuelve un falso `200 OK` con HTML en vez de un `404` claro, y cualquier método no-GET a una ruta inexistente cae en un `500` genérico. Esto dificulta severamente que un desarrollador (o un tester manual) note que está pegándole a una URL que no existe.

**Veredicto de funcionalidades:** Alta / Baja / Modificación / Consulta de usuarios — **NO EXISTE, NINGUNA**. Confirmado en vivo. Coincide exactamente con el audit previo — no hay regresión de Sprint 2 porque este módulo nunca se tocó en Sprint 2.

**Reglas de negocio — RN-015** (`UsuarioServiceImpl.java:22-59`): el código de validación existe y es correcto (rechaza rol vacío, con comas/espacios, o inválido). Pero el **único caller de producción** es `MedicoServiceImpl.crearMedico()` (línea 44-48), que siempre pasa `RolEnum.MEDICO.name()` hardcodeado — las tres ramas de rechazo de RN-015 son código muerto, inalcanzable desde ningún flujo real hoy. Sí se confirmó en vivo que la unicidad de email funciona (crear un médico con el email de otro usuario existente → `400 "El email ya se encuentra registrado."`).

**Permisos:** no aplica — no hay endpoints que gatear por rol.

---

## 3. Módulo 4.2 — Pacientes

**Funcionalidades — todas ejecutadas y PASAN:**
- Alta (ADMINISTRATIVO) → `201 Created`.
- Modificación → `200 OK`.
- Baja lógica → `204 No Content`, confirmado que desaparece de la consulta.
- Consulta con filtros → `200 OK`.

**Reglas de negocio:**
- **RN-008** (DNI inmutable): intento de `PUT` cambiando el DNI de un paciente recién creado → `400 "RN-008: El DNI del paciente es inmutable una vez creado."` Confirmado además que un `GET` posterior muestra el DNI sin cambios. **PASA**.
- **RN-009** (DNI único, incluso inactivo): intento de alta con un DNI ya existente → `400 "RN-009: El DNI ya se encuentra registrado (incluso si está inactivo)."` **PASA**.

**Permisos — regresión-check (gaps preexistentes, no tocados por Sprint 2, confirmados que persisten igual):**
- **GERENTE puede crear pacientes** (`POST /pacientes` como GERENTE → `201 Created`), aunque la matriz 3.4 de la propuesta dice que GERENTE solo debería tener "Consulta". El código no distingue GERENTE de ADMINISTRATIVO en este módulo — mismo gap que ya documentaba el audit previo, sin cambios.
- **MEDICO ve TODOS los pacientes**, no solo los vinculados a sus turnos/HC (`GET /pacientes` como médico devuelve los 6 pacientes existentes, sin ningún filtro). Mismo gap ya documentado (`PacienteServiceImpl.listarPacientes()` sigue haciendo `findAll()` sin acotar), sin cambios de Sprint 2.

---

## 4. Módulo 4.3 — Médicos

**Funcionalidades:**
- Alta (GERENTE) → `201 Created`, usuario asociado auto-creado con rol MEDICO y contraseña por defecto = matrícula (confirmado con login real inmediato, ver Flujo C).
- Alta como ADMINISTRATIVO → `403 "No tenés permisos..."`. Correcto, coincide con la propuesta ("Alta médicos: Sí" solo Gerente) y con la corrección ya confirmada en el audit previo (bug histórico de permitir alta a Administrativo, ya resuelto y sigue resuelto).
- Modificación (ADMINISTRATIVO) → `200 OK`, **idéntica** a la de GERENTE (mismo endpoint, mismo `@PreAuthorize`, sin distinción de campos). Confirma el gap ya documentado: "modificación parcial" de la propuesta para Administrativo no está implementada como tal. Sin cambios de Sprint 2.
- Baja (ADMINISTRATIVO) → `403`, correcta (exclusiva de GERENTE).
- `GET /medicos/me` ("Mis Datos", como MEDICO) → `200 OK`, funciona.
- Autoedición del médico (`PUT /medicos/me` o cualquier variante) → **no existe**. El intento cae en `PUT /medicos/{id}` con `id="me"`, que no puede parsearse como entero y produce un `500` (debería ser un `400` — bug menor adicional de manejo de errores, no específico de este módulo). Confirma el gap ya documentado: el médico no puede editar sus propios datos ni su agenda, sigue sin poder hacerlo.

**Reglas de negocio — RN-012 (CORREGIDA, confirmado en vivo con datos reales):** el bug histórico del audit previo (`MedicoRepository.java`, literal SQL `'NO ASISTIO'` con espacio en vez de `'NO_ASISTIO'`) **ya no está presente**. Se probó con el médico real `carlos.ruiz` (id 2), que tiene **208 turnos futuros reales** (generados automáticamente por su propio contrato de arrendamiento) — el intento de baja devuelve correctamente `400 "RN-012: No se puede dar de baja al médico porque tiene 208 turno(s) futuro(s) sin reasignar."` **PASA**.

**Reporte del usuario #4** (obra social por médico): resuelto en la sección de Catálogos — el checkbox de obras sociales al dar de alta un médico **sí funciona** end-to-end (se guarda y se recupera correctamente). El problema real detrás del reporte #4 es otro (ver sección 8).

---

## 5. Módulo 4.4 — Turnos

Este es el módulo con el hallazgo más grave del relevamiento junto con Facturación (comparten la misma causa raíz — ver sección 9).

**Funcionalidades que PASAN, ejecutadas en vivo:**
- Reserva de turno (`DISPONIBLE` → `RESERVADO`), incluyendo con `idPrestacion` en el payload — PASA, y confirma que el reporte #5 es un problema exclusivamente de frontend (ver abajo).
- Cambio a `EN_ESPERA` — PASA.
- Cancelación antes del horario de atención → vuelve automáticamente a `DISPONIBLE` — PASA (ver Flujo B).
- Cancelación después del horario → queda `CANCELADO`, no revierte — PASA (ver Flujo B).
- `NO_ASISTIO` → no revierte — PASA (ver Flujo B).
- Guardia IDOR de Sprint 1 (un MEDICO no puede tocar turnos de otro médico): **reconfirmado intacto** — un médico intentando cambiar el estado de un turno ajeno recibe `403`; pedir `?idMedico=<otro>` en el listado se ignora y siempre devuelve solo los turnos propios.

**Funcionalidad que FALLA — Reporte #2 (marcar Atendido):**

```
PUT /api/turnos/3/estado  {"nuevoEstado":"ATENDIDO"}
→ HTTP 500 {"message":"Ocurrió un error inesperado. Intente nuevamente."}
```

Reproducido dos veces con datos frescos (turno 2 y turno 3, dos pacientes distintos). El turno queda en su estado anterior (`EN_ESPERA`), sin quedar a medias, porque `@Transactional` revierte todo el método al fallar. **Causa raíz completa en la sección 9.**

**RN-004** (Atendido inmutable): el código es correcto por lectura (`TurnoServiceImpl.java:136-138`), pero **no es verificable en runtime hoy**: `GET /turnos?estado=ATENDIDO` devuelve `[]` — hay **cero turnos en estado Atendido en toda la base**, porque ningún turno puede completar esa transición. La regla protege un estado que hoy es inalcanzable.

**Reporte #5 (agregar práctica al reservar turno): SOLO FRONTEND, confirmado con precisión.**
El backend está completo: `ReservarTurnoDTO.idPrestacion` existe (`ReservarTurnoDTO.java:16`) y `TurnoServiceImpl.reservarTurno()` (líneas 107-114) lo persiste correctamente — confirmado insertando un turno con `idPrestacion:1` vía API directa, que quedó guardado con `"nombrePrestacion":"Ecografia"`. El modal de reserva en `app.html` (línea ~582 en adelante) tiene selects para Tipo de Consulta, Método de Pago, Obra Social y Copago, pero **ningún campo para elegir Prestación/Práctica**, y `confirmarReserva()` en `turnos.js:308-319` nunca arma `idPrestacion` en el payload que envía. Clasificación: **SOLO FRONTEND** — es un campo de formulario que falta agregar, cero cambios de backend necesarios.

---

## 6. Módulo 4.5 — Historias Clínicas

**Todo lo ejecutado PASA**, incluyendo los tres fixes de Sprint 1 que el prompt pedía reconfirmar como regresión-check:

- `GET /historias-clinicas/paciente/{id}` como GERENTE → `403`. Correcto ("Ver historias clínicas: No" para Gerente).
- Registrar evolución (MEDICO) → `201`, funciona.
- **Enmascarado por rol (Sprint 1): reconfirmado intacto.** Como ADMINISTRATIVO, todos los campos clínicos (motivo, diagnóstico, tratamiento, indicaciones, estudios, observaciones) vienen en `null`; como MEDICO, el texto completo. Probado con la misma evolución, mismo request, dos tokens distintos.
- **RN-010 (autoría, en editar Y en anular): reconfirmado intacto.** Un médico distinto al responsable, probando ambas operaciones sobre la misma evolución, recibe `400 "RN-010: No tenés permisos..."` en los dos casos.
- **RN-011 (motivo obligatorio):** anular sin motivo → `400`; con motivo → `200`, el motivo queda persistido en observaciones. **PASA**.

**Hallazgo menor de permisos (no confirmado como explotable):** el `@PreAuthorize` de los 3 endpoints de escritura de evoluciones incluye `hasAnyRole('GERENTE', 'MEDICO')` (`HistoriaClinicaController.java:39,51,63`), cuando la propuesta dice que GERENTE debería tener "No" acceso total al módulo. En la práctica un GERENTE que llegara a invocar estos endpoints recibiría un error de negocio ("Médico no encontrado", porque no existe una entidad Médico asociada a su usuario) — no es explotable hoy, pero es una inconsistencia de defensa en profundidad respecto al principio declarado en la sección 4.5.

**Gap ya documentado que persiste sin cambios (no tocado por Sprint 2):** la evolución anulada usa el soft-delete genérico de la entidad (`@SQLDelete Visible=0`) y por eso **desaparece por completo** de la consulta de historia clínica en vez de quedar visible con badge "Anulada" — confirmado en vivo (tras anular la única evolución del paciente de prueba, `GET .../paciente/{id}` devuelve `"evoluciones":[]`).

---

## 7. Módulo 4.6 — Facturación

**Reporte #6 ("todo el flujo de cobro no funciona"): confirmado, y acotado con precisión — no es que el módulo esté "todo roto".**

`GET /api/facturacion` devuelve `[]` — **cero facturaciones existen en toda la base**, pese a que hay pacientes, turnos y médicos reales desde el 2026-08-03. La causa es que el disparador automático (marcar un turno como Atendido, sección 4.6: *"El circuito arranca automáticamente cuando un turno pasa a Atendido"*) falla el 100% de las veces (misma causa raíz que el reporte #2 — sección 9). Como nunca se crea el registro de Facturación, no hay nada sobre lo cual registrar un cobro.

**Para aislar si el resto del circuito funciona independientemente del disparador roto**, se insertó una Facturación de prueba directamente por SQL en el clon aislado `MediSpaceTest` (turno real, paciente real, médico real, $5000, estado PENDIENTE — documentado aquí para reproducibilidad). Con ese único registro de prueba, **el resto del módulo funciona correctamente**:

| Operación | Resultado |
|---|---|
| `POST /facturacion/{id}/cobro` (registrar cobro, ADMIN) | `200 OK`, estado pasa a PAGADO correctamente |
| `GET /facturacion` como MEDICO | `403` (confirmado tras corregir un error propio de URL en un intento anterior) — el permiso está bien restringido |
| `POST /liquidaciones/generar` (RN-006, split desde contrato) | `200`, aritmética correcta (70/30 → $3500/$1500 sobre $5000) |
| `PUT /liquidaciones/{id}/anular` (RN-007, solo GERENTE) | ADMIN → `403`; GERENTE → `200`, estado ANULADA; anular de nuevo → `400 "RN-007: La liquidación ya se encuentra anulada."` |

**RN-005** (no liquidar con facturas pendientes sin cobro): **no confirmable en esta sesión** — la única Facturación de prueba disponible ya estaba PAGADA al momento del test (se cobró antes de intentar liquidar). Pendiente de una verificación específica con una Facturación deliberadamente dejada PENDIENTE.

**RN-016** (bloquear liquidación con turnos atendidos sin cobro): **NO SE PUDO EJECUTAR** — requiere un turno en estado ATENDIDO sin facturación asociada, y hoy ningún turno puede llegar a ATENDIDO (ver sección 9). Revisado solo por lectura de código (`TurnoRepository.countAtendidosSinCobro` + chequeo en `LiquidacionServiceImpl`), no ejecutado.

**Bug menor adicional:** `POST /api/liquidaciones` (sin el sufijo `/generar`) devuelve `500` genérico en vez de `404`/`405` — mismo patrón del bug transversal de enrutamiento (sección 10).

---

## 8. Módulo 4.7 — Reportes

**Todo lo ejecutado PASA, sin excepción:**
- Dashboard (GERENTE) → `200 OK` con datos reales; ADMINISTRATIVO → `403`. Permisos exclusivos de GERENTE, correctos.
- Recalcular dashboard para una fecha → `200`, refleja correctamente cambios hechos durante la sesión de prueba (facturación del día, nuevos pacientes). Confirma que el fix de Sprint 2 (reemplazo de `findAll()` por queries acotadas por fecha) sigue funcionando.
- Reporte de facturación por médico y por obra social → `200`, agregados numéricamente correctos.
- Reporte de uso de consultorios → `200`, vacío (no hay registros de `Uso_Consultorio` reales — probado por separado en el módulo 4.8, el endpoint que los genera sí funciona cuando se lo invoca directamente).

Sin hallazgos nuevos ni regresiones.

---

## 9. Módulo 4.8 — Arrendamiento

**Contraste clave de este módulo: código actual funciona bien; el proceso real que usa el usuario, no.** Ver desarrollo completo en la sección de causa raíz (10).

**Ejecutado contra el código actual (puerto 8081), todo PASA:**
- **RN-013** (superposición): crear un contrato que se solapa con uno activo existente (mismo consultorio, mismo día, horario superpuesto) → `400 "RN-013: El consultorio ya tiene un contrato activo que se superpone..."`. La query que implementa esto (`countSuperposicionesConsultorio`, nativa, con `CAST(... AS TIME)` explícito) es distinta de la que falla en Facturación y no tiene el mismo bug.
- **RN-014** (mínimo 4 horas): contrato de 2 horas → `400 "RN-014: La asignación mínima de consultorio es de 4 horas (240 minutos). Se asignaron solo 120 min."`; de exactamente 4 horas → `201 Created`.
- Crear consultorio (GERENTE) → `201`; como ADMINISTRATIVO → `403`. Correcto.
- **Guardia IDOR de Sprint 1 en `GET /arrendamientos/medico/{idMedico}`: reconfirmado intacto tras el refactor de Sprint 2** (que partió `ArrendamientoServiceImpl` en 3 services) — exactamente lo que pedía verificar el prompt de Sprint 2. Un médico pidiendo los datos de otro → `403`; pidiendo los propios → `200`.
- `POST /arrendamientos/uso` (registrar uso de consultorio) → `201`, persiste correctamente.
- `POST /arrendamientos/cierre-diario` → `201`, cálculo correcto del split (30%/70% sobre la facturación del día). Usa `findContratoVigentePorFecha`, una variante de la query que **no compara horarios** y por eso **no tiene** el bug de tipos que sí tiene la variante usada por Facturación (ver sección 9). Dato clave: Cierre Diario funciona de punta a punta; Facturación automática, no — a pesar de compartir el mismo criterio de matching de contrato.

**Reporte #1 ("no se pueden crear contratos"): FALLA en el proceso real (:8080), PASA en el código actual (:8081), con el MISMO payload exacto.** Ver causa raíz completa en la sección 10 — es la evidencia más contundente de todo el relevamiento.

**Gap ya documentado, no re-verificado en vivo por prioridad de tiempo:** sigue sin existir un endpoint para modificar el estado de un Consultorio (Bloqueado/Mantenimiento) — confirmado exhaustivamente en el audit previo, no tocado por Sprint 2.

---

## 10. Catálogos de referencia — Especialidad y ObraSocial (reportes #3 y #4)

**Inventario real:**

| | GET | POST (alta al catálogo) |
|---|---|---|
| `EspecialidadController` | GERENTE/ADMINISTRATIVO | **Sí**, GERENTE — probado en vivo, `201 Created` |
| `ObraSocialController` | GERENTE/ADMINISTRATIVO/MEDICO | **No existe** — probado en vivo, `POST /api/obras-sociales` → `500` (cae en el bug transversal de enrutamiento, sección 11 — no hay ningún método mapeado para ese verbo/ruta) |

**Reporte #4 ("falta gestión real de obras sociales: alta al catálogo, y qué obra social atiende cada médico") — CONFIRMADO CON CERTEZA TOTAL Y ACOTADO EN DOS PARTES:**
1. **Alta de obra social al catálogo: no existe en absoluto, ni en API ni en UI.** `ObraSocialController.java` tiene únicamente el método `listarObrasSociales()` (líneas 21-25). Las obras sociales están fijas a lo sembrado — hoy solo existe "OSDE" en la base real. Este es el gap real detrás del reporte.
2. **Qué obra social atiende cada médico: SÍ FUNCIONA.** El checkbox de obras sociales dentro del alta/edición de Médico persiste y se recupera correctamente — confirmado con datos reales: el médico `policarogabriel` (matrícula M-105) tiene `"obrasSociales":["OSDE"]` correctamente guardado y devuelto por `GET /medicos`. Esta parte del reporte #4 **no es un bug**.

**Reporte #3 ("la interfaz de obras sociales está rota o es confusa") — causa raíz identificada por inspección de frontend, no es un bug de renderizado.**
Búsqueda de "obras-sociales" / "obrasocial" / "view-obras" en `app.html` y `app.js`: **sin resultados**. No existe ningún módulo ni vista dedicada a "Obras Sociales" en el sidebar ni en el router. ObraSocial aparece únicamente como: (a) un `<select>` dentro del formulario de alta/edición de Paciente, y (b) un selector de checkboxes dentro del formulario de Médico. **No hay una "interfaz de obras sociales" que pueda calificarse de rota — literalmente no existe como pantalla propia.** La percepción del usuario de que está "rota o confusa" es consistente con buscar una pantalla de gestión dedicada que nunca se construyó — es el mismo gap raíz que el reporte #4, visto desde el ángulo de UI en vez de API.

**Clasificación de ambos:** GAP PREEXISTENTE / diseño incompleto. No es una regresión de Sprint 2 — es una funcionalidad que nunca se implementó como módulo propio, en ningún sprint anterior tampoco.

---

## 11. Flujos integrales (sección 9 de la propuesta)

### Flujo A — Atención estándar completa (incluyendo cobro): **FALLA en el paso 4**

Ejecutado end-to-end con datos frescos (paciente y turno nuevos, para evidencia limpia):

1. Registro de paciente nuevo → `201`, historia clínica automática creada. **PASA**.
2. Turno `DISPONIBLE` reservado, **con `idPrestacion` en el payload** → `200`, la prestación queda persistida correctamente. **PASA** (confirma que el backend del reporte #5 funciona perfecto).
3. Estado → `EN_ESPERA` → `200`. **PASA**.
4. El médico registra la evolución clínica vinculada al turno → `201`, **PASA**. Intento de marcar el turno como Atendido (irreversible, dispara la Facturación automática) → **`HTTP 500`, FALLA**. El turno queda en `EN_ESPERA` (rollback transaccional completo — no queda a medias).
5. Registro del cobro: **no se pudo ejecutar como parte del flujo real** (nunca se genera la Facturación automáticamente). Probado por separado con datos insertados manualmente — esa pieza aislada sí funciona (sección 7).
6. Liquidación: mismo caso — no ejecutable como parte del flujo real; funciona aislada.

**Este es el hallazgo más importante de todo el relevamiento.** Los módulos individuales de Turnos y Facturación "casi" pasan por separado — pero el flujo real, de punta a punta, se rompe exactamente en el paso que reportó el usuario (marcar Atendido), y ese único paso roto arrastra consigo todo lo que depende de él (cobro, liquidación, reportes de facturación con datos reales).

### Flujo B — Cancelación de turno: **PASA completo**

1. Turno futuro reservado, cancelado *antes* del horario de atención → vuelve automáticamente a `DISPONIBLE` (paciente, prestación y fecha de reserva limpiados). **PASA**.
2. Turno con fecha ya vencida, cancelado *después* del horario → queda en `CANCELADO`, no revierte. **PASA**.
3. `NO_ASISTIO` sobre un turno reservado vencido → no revierte, queda registrado. **PASA**.

Sin hallazgos.

### Flujo C — Alta de médico nuevo con contrato: **PASA completo**

1. Alta de médico (GERENTE) → `201`, usuario auto-creado con rol MEDICO; login inmediato con contraseña = matrícula → `200`, token válido. **PASA**.
2. Confirmado que el alta de médico *por sí sola* no genera turnos (coincide con el desvío ya documentado del Anexo #2 — turnos y agenda se unificaron en el contrato de arrendamiento).
3. Asignación de consultorio (creación del contrato) → `201`, genera automáticamente los turnos `DISPONIBLE` del rango configurado (16 turnos para 2 semanas × 1 día/semana × 8 turnos/día). **PASA**.
4. RN-013 validado en el mismo flujo: asignar otro médico al mismo consultorio/día con horario superpuesto → `400`, bloqueado correctamente. **PASA**.

**Es el único de los 3 flujos integrales que funciona de punta a punta sin ninguna intervención manual.** Sin hallazgos.

---

## 12. Causa raíz — por qué pasa esto (sección más importante para priorizar el arreglo)

Hay **dos causas raíz distintas**, ambas reales y confirmadas con evidencia directa, que en conjunto explican 4 de los 6 reportes del usuario (#1, #2, #5 es aparte, #6). No son la misma causa, y **arreglar una sin la otra no resuelve todo**.

### Causa raíz A — el proceso que el usuario prueba hoy (puerto 8080) nunca se reinició a través de dos sprints de cambios

Se encontró `/tmp/medispace_server.log` (398 KB), el stdout/stderr real del proceso que sigue escuchando en el puerto 8080 desde el **2026-08-05 17:18:06** (confirmado por la línea de arranque de Spring Boot, que incluye el PID exacto del proceso vivo). Es decir: **es el log real de la aplicación que el usuario estuvo usando**, no una reconstrucción.

En todo el log hay únicamente **6 errores no controlados**, y los 6 ocurrieron **hoy, 2026-08-10**, entre las 20:00:43 y las 21:07:22 — los primeros 5 son la sesión de pruebas real del usuario; el sexto es una reproducción controlada hecha en esta misma sesión de auditoría contra ese mismo proceso, para confirmar la causa.

| Hora | Excepción | Endpoint / flujo | Reporte relacionado |
|---|---|---|---|
| 20:00:43 | `NoSuchMethodError: FacturacionResponseDTO.<init>(...)` en `FacturacionServiceImpl.mapToDTO` (línea 126), llamado desde `crearFacturacionAutomatica` (línea 62) | Marcar turno Atendido / generar Facturación | #2 / #6 |
| 20:02:39, 20:02:45, 20:04:39 (3 intentos seguidos) | `NoClassDefFoundError`/`ClassNotFoundException: com.medispace.app.service.impl.TurnoServiceImpl$1` | `ArrendamientoController.crearArrendamiento` | #1 |
| 20:10:16 | `NoSuchMethodError: FacturacionResponseDTO.<init>(...)` (repetido) | Marcar turno Atendido / generar Facturación | #2 / #6 |
| 21:07:22 | `NoClassDefFoundError: TurnoServiceImpl$1` (reproducción propia) | Creación de contrato con payload idéntico al que **sí funciona** contra el código actual | #1 |

**Diagnóstico técnico:** `NoClassDefFoundError`, `ClassNotFoundException` y `NoSuchMethodError` sobre firmas de constructor son la firma clásica de un JVM de larga duración cuyas clases cargadas en memoria (las que existían en `target/classes` el 5 de agosto a las 17:18) quedaron desincronizadas de lo que hay *hoy* en disco — recompilado varias veces durante Sprint 1 (~08-06) y Sprint 2 (~08-07). Cuando el proceso viejo necesita cargar por primera vez una clase que nunca había tocado (una clase interna generada por el compilador dentro de `TurnoServiceImpl`, o reconstruir `FacturacionResponseDTO` cuyo constructor generado por Lombok ahora tiene más parámetros que cuando el proceso arrancó), el classloader busca en el `target/classes` *actual*, encuentra una versión binaria incompatible con lo que el resto de las clases ya cargadas en memoria esperan, y el JVM lanza estas excepciones. **No es un bug de lógica de negocio. Es 100% un artefacto de nunca haber reiniciado el servidor a través de dos sprints de cambios de código.**

**Evidencia decisiva:** el mismo payload de creación de contrato que falla siempre contra el puerto 8080 (real) se probó contra el código actual (puerto 8081) y funcionó sin ningún error (`201 Created`, turnos generados correctamente). **Reiniciar el servidor real, con el código actual, resolvería el reporte #1 por completo.**

### Causa raíz B — un bug real de tipos en el código actual, que reiniciar el servidor NO alcanza para resolver

Independientemente de la Causa raíz A, existe un bug genuino en el código actual (confirmado contra el puerto 8081, con el código más reciente, sin ningún proceso stale de por medio):

```
PUT /turnos/2/estado {"nuevoEstado":"ATENDIDO"} → HTTP 500
```

Stack trace real: `org.hibernate.exception.SQLGrammarException` → `SQLServerException: "Los tipos de datos time y datetime son incompatibles con el operador less than or equal to."`

La query que falla es `ArrendamientoModuloRepository.findContratoVigente` (`ArrendamientoModuloRepository.java:39-52`), JPQL (no nativa):

```java
"AND a.horaInicio <= :hora AND a.horaFin > :hora "
```

con `:hora` bindeado como `java.time.LocalTime`. Hibernate 6.4 + `SQLServerDialect` no castea correctamente ese parámetro contra la columna `TIME` de `Arrendamiento_Modulo`, y SQL Server rechaza la comparación en tiempo de ejecución — **de cualquier invocación**, no solo cuando no hay contrato. La excepción ocurre en la ejecución misma del `SELECT`, antes de que Hibernate pueda siquiera evaluar si la lista de resultados está vacía.

Cadena completa: `TurnoController.cambiarEstado` → `TurnoServiceImpl.cambiarEstadoTurno` (nuevoEstado=ATENDIDO) → `FacturacionServiceImpl.crearFacturacionAutomatica` (disparada en `TurnoServiceImpl.java:158`) → `resolverContratoVigente` (`FacturacionServiceImpl.java:138-150`) → `arrendamientoModuloRepository.findContratoVigente` → **crash**.

**La hipótesis original del prompt de auditoría** ("¿el matching de contrato tira excepción en vez de caer al fallback 70/30 documentado?") es **cierta en el efecto** (nunca llega al fallback) pero el mecanismo real es más grave de lo hipotetizado: no es una `BusinessRuleException` intencional por "no se encontró contrato" — es un bug de mapeo de tipos de Hibernate/JDBC que rompe la consulta **antes** de poder determinar si hay o no contrato. Afecta incluso a turnos sin ningún contrato de arrendamiento asociado.

**Por qué esto NO es lo mismo que la Causa raíz A:** son dos bugs completamente distintos, confirmados por separado:
- Query hermana `countSuperposicionesConsultorio` (mismo repositorio, RN-013): es **nativa** y castea explícitamente `CAST(:horaFin AS TIME)` — no tiene este problema, confirmado que funciona bien en el módulo Arrendamiento.
- Query hermana `findContratoVigentePorFecha` (usada por Cierre Diario, sección 4.8): **no compara horarios en absoluto** — tampoco tiene este bug, confirmado que Cierre Diario funciona de punta a punta.
- Solo `findContratoVigente` (la variante puntual por-turno, usada exclusivamente por Facturación automática) tiene el problema.

**Consecuencia práctica:** reiniciar el servidor real arreglaría el reporte #1, pero **no** arreglaría #2 ni #6 — esos necesitan además una corrección en `findContratoVigente` (por ejemplo, convertir la comparación a query nativa con `CAST(... AS TIME)` igual que ya hace `countSuperposicionesConsultorio`, o ajustar el tipo de parámetro que Hibernate infiere).

---

## 13. Nota sobre cobertura de tests

El proyecto es, en efecto, **100% tests unitarios con Mockito**, sin ningún test de integración real contra una base de datos — se confirmó ejecutando `mvn test` al final de esta sesión: **BUILD SUCCESS, exit code 0**, todos los tests en verde.

Esto confirma exactamente lo que planteaba la pregunta del prompt: la suite actual es estructuralmente incapaz de detectar cualquiera de los dos bugs raíz de esta auditoría, por razones distintas para cada uno:

- **Causa raíz B** (`findContratoVigente`, tipos TIME/DATETIME) es un bug que solo existe en la traducción de Hibernate a SQL contra un motor SQL Server real. Los tests de `FacturacionServiceImpl` mockean `ArrendamientoModuloRepository` por completo (`@Mock`) — la query JPQL nunca se ejecuta de verdad en ningún test, así que no hay forma de que Mockito la hubiera detectado. Es, por diseño, invisible a un test unitario con mocks.
- **Causa raíz A** (proceso Java desactualizado) ni siquiera es un bug de código — es un problema de ciclo de vida del proceso en el entorno de desarrollo. Ningún test, unitario o de integración, corriendo dentro de la misma ejecución de Maven, podría haberlo detectado nunca, porque por definición un test se ejecuta contra el código recién compilado, no contra un proceso que lleva 5 días corriendo.

**Recomendación (sin implementar, según el alcance de esta auditoría):** una suite mínima de integración con `@SpringBootTest` + `MockMvc` (o Testcontainers con SQL Server real, dado que el dialecto y el driver son parte del problema) para los 3 flujos core —cuando menos, un test que ejecute `reservarTurno → cambiarEstado(ATENDIDO)` de punta a punta contra un contexto Spring real— habría detectado la Causa raíz B en minutos, mucho antes de que llegara a producción. La Causa raíz A no se soluciona con tests — se soluciona con disciplina operativa (reiniciar el proceso de desarrollo después de cada sprint, o mejor aún, usar `spring-boot-devtools` con recarga de clases, o simplemente documentar "reiniciar el server" como paso obligatorio de checklist de fin de sprint).

---

## 14. Apéndice — datos de prueba creados en esta sesión (para reproducibilidad)

Todo lo siguiente se creó **solo en el clon aislado `MediSpaceTest`**, salvo que se indique lo contrario. La base real `MediSpace` no fue modificada, con la única excepción del par de intentos de creación de contrato ejecutados directamente contra el proceso real (puerto 8080) para confirmar la Causa raíz A — esos sí quedaron persistidos en `MediSpace` real (un contrato válido de 4hs, médico 1 / consultorio 1 / viernes 08-12hs, con sus turnos generados; es de bajo impacto y reversible vía "dar de baja").

- Pacientes: DNI `99887766` (dado de baja en la misma sesión), DNI `55443322` ("GerTest"), DNI `77889900` ("FlujoA Test", usado en el Flujo A).
- Médico nuevo: matrícula `MP-FLUJOC-1`, email `flujoc.nuevo@medispace.com` (Flujo C).
- Especialidad: "Cardiologia Test".
- Consultorio: `AUDIT-1`.
- Contratos de arrendamiento adicionales: médico 1/consultorio 1/martes 14-18hs; médico 4 (FlujoC)/consultorio AUDIT-1/miércoles 09-13hs (16 turnos generados).
- 1 Facturación de prueba insertada directamente por SQL (turno 2, $5000, luego cobrada y liquidada) — la única inserción SQL directa de datos de negocio en toda la sesión, documentada en la sección 7.
- Password de `secretaria@medispace.com` reseteado a `Test1234!` **solo en `MediSpaceTest`**.
