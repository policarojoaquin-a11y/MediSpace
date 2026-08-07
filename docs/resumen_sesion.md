# MediSpace — Resumen completo de la sesión

## 1. Usuarios y contraseñas

Estos son **todos** los usuarios que existen hoy en la base, con las contraseñas que se definieron al crearlos (las contraseñas están hasheadas con bcrypt en la base — estas son las que yo mismo asigné, no hay forma de "recuperar" un hash ajeno):

| Email | Contraseña | Rol | Origen |
|---|---|---|---|
| `admin@medispace.com` | `admin123` | GERENTE | Sembrado automático la primera vez que corre la app (`DataSeedConfig`) |
| `ana.gomez@medispace.com` | `MP1001` | MEDICO | Test — password por defecto = matrícula (regla de negocio) |
| `carlos.ruiz@medispace.com` | `MP2002` | MEDICO | Test — password por defecto = matrícula |
| `secretaria@medispace.com` | `test123` | ADMINISTRATIVO | Creada manualmente por SQL para poder probar el rol ADMINISTRATIVO |

**Importante:** el rol PACIENTE ya no existe como cuenta de login (ver sección 5). Los pacientes son solo registros de datos, sin usuario ni contraseña.

Todos los usuarios de MEDICO/ADMINISTRATIVO de esta lista son **datos de prueba míos**, no gente real — quedan mezclados con lo que cargues vos si no los limpiás antes de usar el sistema en serio.

---

## 2. Favicon, logo y arranque del proyecto

- Se integró el logo (`logo-medispace.jpg`) como favicon y como imagen en el login y el sidebar.
- Se explicó cómo correr la app (`mvn spring-boot:run`, con SQL Server ya corriendo localmente en el puerto 1433).

---

## 3. Bug de login — causa raíz y fix (varios problemas encadenados)

El síntoma reportado era: "no puedo iniciar sesión, aparece el error y se elimina en un milisegundo". Se encontraron y corrigieron **cinco problemas reales**, todos verificados con un navegador automatizado real (no solo curl):

1. **Faltaba `GlobalExceptionHandler`** — el resumen del proyecto decía que existía pero nunca se había creado. Sin él, credenciales incorrectas devolvían 403 con body vacío (sin mensaje), y cualquier `BusinessRuleException` en cualquier módulo caía en un 500 genérico. Se creó, manejando `BusinessRuleException` (400), `AuthenticationException` (401) y errores genéricos (500).
2. **401 vs 403 inconsistentes** — sin token o token inválido devolvía 403, pero el frontend solo redirige al login en 401. Se agregaron `authenticationEntryPoint`/`accessDeniedHandler` explícitos en `SecurityConfig`.
3. **Bug de encoding UTF-8** — los mensajes de error salían con las tildes corruptas (caracteres de reemplazo en vez de "ó", "é", etc.) porque `response.getWriter()` usa ISO-8859-1 por defecto en Tomcat. Corregido escribiendo a `getOutputStream()` con UTF-8 explícito.
4. **La causa real del "aparece y desaparece"** — en `api.js`, la función genérica de requests recargaba la página (`window.location.href = '/'`) en cualquier 401, pensado para sesión expirada, pero se disparaba también en el propio login al fallar. Se excluyó `/auth/login` de esa regla.
5. **Bug adicional descubierto en la verificación**: en `app.html`, `app.js` cargaba *antes* que los scripts de los módulos, pero se ejecutaba inmediatamente, tirando `loadDashboard is not defined` y rompiendo la carga de datos tras el login para cualquier rol. Se reordenaron los `<script>`.

---

## 4. Limpieza de marca

- Se eliminaron las menciones a "Consultorios Mitre" de `index.html`, `app.html` y `pom.xml` (por decisión tuya, se mantuvieron en `docs/spec.md`, `plan.md`, `constitution.md` y `schema.sql` como registro histórico de los requerimientos originales).

---

## 5. Cambio grande de arquitectura: pacientes sin login

A partir de la propuesta técnica original (`Propuesta_tecnica_V_Final.md`), corregiste varias cosas que divergían de lo que realmente querés (aunque el documento original y `spec.md` sí especificaban un rol Paciente con login de solo lectura):

- **Se quitó el rol PACIENTE del sistema de login por completo**: se eliminó la FK `Pacientes.ID_Usuario` de la base de datos, el rol `PACIENTE` del enum, y todo el código de backend/frontend asociado (endpoints, DTOs, vistas "Mis Turnos"/"Mis Datos"/"Cartilla Médica" para paciente).
- El alta de paciente ya no crea ninguna cuenta ni pide email/contraseña.
- **Reportes**: los 4 endpoints ahora exigen `hasRole('GERENTE')` (antes también dejaban entrar a ADMINISTRATIVO), y se sacó "Reportes" del menú de ADMINISTRATIVO.
- **Contratos de Arrendamiento**: la creación quedó restringida a GERENTE (antes también podía ADMINISTRATIVO); el botón se oculta para cualquier otro rol.
- **Alta rápida de paciente durante "Reservar Turno"**: botón "+ Nuevo paciente" dentro del modal de reserva, con mini-formulario (nombre, apellido, DNI, fecha nacimiento) que no interrumpe el flujo.
- **Buscador de pacientes por DNI**: se reemplazó el dropdown de selección de paciente en "Reservar Turno" por un buscador con resultados en vivo por DNI o nombre.

### Bugs adicionales encontrados en el camino
- La obra social en el alta de paciente era un campo de texto libre sin ningún endpoint para listarlas (mismo patrón de bug que Especialidad/Consultorio, ver más abajo). Se agregó `ObraSocialController` y se convirtió en un select real.

---

## 6. Rediseño visual completo ("que no se vea hecho con IA")

Dirección elegida: *"Institucional clínico con acento editorial"*, manteniendo la paleta exacta del spec (azul `#1A5276`, verde `#1E8449`, rojo `#C0392B`, naranja `#E67E22`) pero cambiando todo lo demás:

- **Tipografía nueva**: Source Serif 4 (títulos/marca) + IBM Plex Sans (UI) + IBM Plex Mono (horarios, montos, badges) — cero Inter/system-ui.
- **Radios de borde variables**: rectos en botones/inputs, curvos en cards — no todo `rounded-xl` parejo.
- **Motivo distintivo**: retícula de cruces (referencia al ícono de marca) en login y sidebar.
- **Asimetría real**: stat card destacado en el dashboard, page-header con barra lateral de acento, login con layout roto (barra naranja, texto en mono).
- Se aplicó de forma consistente en login, dashboard, médicos, pacientes, turnos, arrendamiento y contratos.

### Reestructuración de Turnos a columnas por médico
Basado en una foto de referencia de un sistema real (Treelan) que compartiste: se cambió la vista de Turnos de una tabla plana a **una columna por médico en paralelo**, cada una con su agenda del día, contador "X disponibles · Y/Z", header que se pone rojo cuando la agenda está completa, y acciones como íconos compactos con borde de color por estado.

### Bugs reales encontrados durante el rediseño (no relacionados al diseño en sí)
1. **Generar Turnos (manual, ya eliminado luego) rompía con 500** — el frontend mandaba `fechaInicio`/`fechaFin`, el backend esperaba `fechaDesde`/`fechaHasta`. Nunca había funcionado desde la UI.
2. **Especialidad de médico rota** — el campo era texto libre pero el backend necesita un ID real, y no existía endpoint para listarlas. Se agregó `GET /api/especialidades` y se convirtió en select. Sin esto, **crear un médico desde la UI nunca funcionó**.
3. **"Inicio de Actividad" no marcado como obligatorio** en el formulario pese a que la base lo exige `NOT NULL` → tiraba un 500 crudo si se dejaba vacío.
4. Columna "Especialidad" en la tabla de médicos siempre mostraba "—" por un nombre de campo mal escrito.

---

## 7. Módulo de Arrendamiento: split y consultorios

- Se separó en dos secciones de menú: **Arrendamiento** (consultorios y quién los ocupa) y **Contratos** (alta/baja de contratos).
- **Creación de consultorios**: antes no existía ningún endpoint para crearlos (solo listarlos). Se agregó `POST /api/consultorios` (solo GERENTE).
- **Vista de Arrendamiento rediseñada como cards por consultorio**: cada card muestra DISPONIBLE/OCUPADO, ubicación, equipamiento, y la lista de médicos que lo ocupan con su día y horario.
- **Separación por día de la semana**: el modelo de contrato no tenía columna de día (solo un rango de fechas + horario, aplicado como si fuera todos los días). Se agregó `Dia_Semana` a la tabla `Arrendamiento_Modulo`, a la entidad, DTOs, y a la validación RN-013 (ahora exige superposición de *consultorio + día + horario*, no solo consultorio + horario). Antes esto hacía que, por ejemplo, el Consultorio 101 apareciera "ocupado" todos los días cuando en realidad solo lo estaba los lunes de 8 a 12.
- Se encontró y arregló un **bug de backend preexistente**: la query nativa de RN-013 comparaba tipos `TIME` con `DATETIME` directamente, algo que SQL Server rechaza — nunca se había ejecutado contra una base real porque no existía UI para crear contratos hasta esta sesión.

---

## 8. Cambio de arquitectura: los contratos generan los turnos automáticamente

Este fue el cambio más grande de la sesión. Antes, "Agenda Médico" (un formulario aparte en Médicos) y "Contratos" de Arrendamiento eran dos cosas separadas que se superponían en concepto pero solo Agenda generaba turnos, mediante un botón manual "Generar Turnos".

**Decisiones tomadas (confirmadas con vos):**
- Se eliminó por completo "Agenda Semanal" (modal, backend, base — el contrato la reemplaza).
- Se eliminó el botón manual "Generar Turnos" de la sección Turnos.
- Si un contrato no tiene fecha de fin (indefinido), se generan turnos para los próximos **6 meses** desde el inicio del contrato.

**Qué se implementó:**
- Se agregaron `Duración de Turno` y `Cupo Máximo Diario` al contrato de arrendamiento.
- Al crear un contrato, el backend genera automáticamente los turnos "Disponible" para ese médico/consultorio/día/horario, respetando la fecha fin (o el horizonte de 6 meses).
- RN-013 sigue validando *antes* de crear el contrato — un turno nunca puede generarse en un horario donde el consultorio ya está ocupado por otro médico ese mismo día. Se probó creando dos contratos superpuestos (mismo consultorio/día/horario, distinto médico) y el segundo fue rechazado correctamente.
- Se eliminaron del backend: `AgendaMedico` (entity, repository, service, controller, DTOs) y `GenerarTurnosDTO`.

**Bug encontrado durante esta última verificación:** después del refactor grande de `TurnoServiceImpl`, quedó una clase compilada vieja huérfana (`TurnoServiceImpl$1.class`) de una compilación incremental anterior, que rompía en tiempo de ejecución con `NoClassDefFoundError`. Se resolvió con `mvn clean` antes de reconstruir.

---

## 9. Cosas pendientes / no auditadas en esta sesión

- El detalle fino de las 15 reglas de negocio (RN-001 a RN-015) más allá de las tocadas directamente en esta sesión (RN-001 a RN-004, RN-008, RN-009, RN-013, RN-014, RN-015).
- No se corrieron los 4 test suites del proyecto de punta a punta después de todos estos cambios (se actualizaron los tests que rompían por los refactors — `PacienteServiceTest`, `ArrendamientoServiceTest` — pero no se ejecutó `mvn test` completo al final).
- El módulo de gestión de Usuarios (alta/baja/modificación/consulta desde una pantalla dedicada) que describe la propuesta técnica no está construido — hoy los usuarios de médicos/administrativos se crean indirectamente (al crear el médico, o por SQL manual).
- Los consultorios de prueba que creé durante la sesión (202, 303, 404) desaparecieron en algún momento sin que yo pueda explicar por qué — no parece ser un bug del producto (las altas reales vía UI persistieron correctamente cuando las verifiqué de nuevo), pero lo marco por transparencia.
