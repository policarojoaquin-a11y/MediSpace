# Fase 7 — ABM, reglas restantes y permisos

Generado: 2026-09-23. Pruebas por API directa sobre entidades QA exclusivamente.

## Resumen — todo PASS salvo lo indicado

| Regla | Resultado |
|---|---|
| Usuarios: Admin solo puede crear ADMINISTRATIVO, incluso forzando el rol por API | **PASS** — `400` |
| RN-020 (no baja de usuario de médico activo) | **PASS** |
| Baja lógica + reactivación de Usuario | **PASS** (`Visible` en false/true correctamente; `GET /{id}` 404 tras baja por `@SQLRestriction`, hay que usar `?incluirInactivos=true` para verlo) |
| RN-008 (DNI de paciente inmutable) | **PASS** — `400 RN-008` |
| RN-009 (DNI único incluso inactivo) | **PASS** — `400 RN-009` |
| RN-012 (baja médico bloqueada si hay turnos futuros) | **PASS tras corregir el escenario de prueba** — ver nota |
| Matrícula de médico única | **PASS** |
| RN-017 (médico no ve/edita cartilla ajena) | **PASS** — `403` |
| Obra social: nombre único | **PASS** |
| RN-016 (baja de OS bloqueada con médicos activos) | **PASS** |
| Consultorio: cambio de estado con turnos reservados → advertencia sin bloqueo | **PASS** |
| RN-001 (concurrencia: 2 reservas simultáneas al mismo turno) | **PASS** (lock pesimista funciona) |
| RN-002 / RN-003 (paciente/médico sin doble turno al mismo horario) | **PASS**, con una nota técnica importante — ver abajo |
| Password hasheado en la base | **PASS** — BCrypt (`$2a$10$...`, 60 caracteres) |
| Recuperación de contraseña (link 20 min) | **NO IMPLEMENTADA** (confirmado en Fase 0, no hay ningún endpoint) |
| Logout | No hay endpoint server-side — JWT es stateless por diseño, el cliente descarta el token. No es un gap, es el patrón esperado |
| Sesión expirada (JWT 24hs) | Verificado por código (`JwtUtils`, `jwtExpirationMs=86400000`) — no probado en vivo (requeriría esperar 24hs; no se fabricó un JWT expirado manualmente por alcance/tiempo) |

## Nota importante — RN-012: mi primera prueba estaba mal diseñada, no es un bug
Intenté dar de baja a `QA_Dr_Clinico` cuando **ninguno** de sus turnos futuros estaba reservado
(todos `DISPONIBLE`) y la baja tuvo éxito. Al leer `MedicoRepository.countTurnosFuturosActivos`
encontré la explicación en un comentario del propio código: la cuenta de "turnos futuros"
**excluye intencionalmente `DISPONIBLE`** — es el fix de un bug real reportado el 27/08 ("nunca
se puede dar de baja a un médico porque siempre tiene cientos de turnos disponibles a futuro").
Repetí la prueba reservando un turno real (`QA_P1` en un turno de `QA_Dr_Clinico`) y ahí **sí**
se bloqueó correctamente: `400 RN-012: ... tiene 1 turno(s) futuro(s) sin reasignar.` — correcto.

## Nota técnica — RN-002/RN-003 y precisión de `DATETIME`
Al armar el escenario de prueba (dos turnos QA con el **mismo** horario exacto, corridos a hoy
vía SQL usando `GETDATE()`), las dos reservas duplicadas **no** fueron rechazadas — parecía un
bug. Investigué y until: la columna `Turnos.Fecha_Hora` es `DATETIME` (no `DATETIME2`), que tiene
redondeo no uniforme a ~3.33ms. `GETDATE()` produce timestamps con fracción de segundo (ej.
`.083`), y ese valor no viaja idéntico en el round-trip JDBC/Hibernate al comparar
`LocalDateTime` en la consulta `existsBy...FechaHora`. Repetí la prueba con un timestamp "limpio"
(sin milisegundos, como los que genera la app realmente — todos los turnos reales nacen en
`generarTurnosParaContrato` con horarios en punto de minuto exacto) y **ahí RN-002 rechazó
correctamente**: `400 RN-002: El paciente ya posee un turno reservado en este mismo horario.`
**Conclusión: RN-002/RN-003 funcionan correctamente para cualquier dato que la aplicación pueda
generar por sí misma.** El escenario de falla solo es alcanzable manipulando `Fecha_Hora`
directamente en la base con un valor sub-segundo, algo que ningún flujo de la UI/API permite
hacer. Lo dejo documentado como observación técnica (posible mejora: migrar a `DATETIME2` es la
recomendación estándar de Microsoft de todos modos), no como bug de negocio.

## RN-001 — concurrencia real
Dos requests HTTP paralelas (`Start-Job`) reservando el mismo turno QA con pacientes distintos:
una devolvió `200 RESERVADO`, la otra `400` (RN-001, el turno ya no estaba `DISPONIBLE`). El lock
pesimista (`findByIdForUpdate`, comentado explícitamente en el código como protección de
concurrencia) funciona correctamente.

## Matriz de permisos (por rol × módulo, relevada del código de los 13 `@RestController`)
Extraída de `@PreAuthorize` en cada endpoint (Fase 0) + confirmada en vivo endpoint por endpoint
a lo largo de todas las fases anteriores. Resumen por módulo (rol → acceso):

| Módulo | GERENTE | ADMINISTRATIVO | MEDICO | Sin sesión |
|---|---|---|---|---|
| Usuarios (ABM) | Sí | Sí (solo crea ADMINISTRATIVO) | No (403) | 401 |
| Pacientes (ABM) | Solo lectura (GET) | Sí (ABM completo) | Solo lectura de vinculados | 401 |
| Médicos (ABM) | Sí | Editar/leer (no crear/baja) | Solo lectura/gestión propia (`/me/*`) | 401 |
| Turnos | Sí | Sí | Lectura/gestión de los propios | 401 |
| Historias Clínicas | **No accede (403)** | Ve y adjunta, no evoluciona | Evoluciona solo pacientes vinculados | 401 |
| Facturación | Sí | Sí | No (403) | 401 |
| Liquidaciones | Generar/anular/buscar | Generar/buscar (no anular) | Solo las propias (`/me`) | 401 |
| Arrendamientos | Sí (crear/baja) | Ver/uso/cierre (no crear/baja) | Ver contratos con % ajenos ocultos | 401 |
| Consultorios | Sí (crear) | Ver/cambiar estado | Solo lectura | 401 |
| Obras Sociales | Sí | Sí | Solo lectura | 401 |
| Especialidades | Sí (crear) | Solo lectura | **No listado (403)** — posible gap, ver Fase 0 | 401 |
| Reportes/Dashboard | **Exclusivo** | 403 (gap vs. spec.md línea 227) | 403 | 401 |

Confirmé en vivo el acceso `401` sin token para una muestra de endpoints (`/api/turnos`,
`/api/pacientes`, `/api/medicos`) — Spring Security responde `401 Unauthorized` uniformemente
sin sesión, consistente con lo esperado.

## Hallazgo de seguridad — password inicial del médico predecible (reportado, NO corregido)
Confirmado en Fase 1: al dar de alta un médico, la contraseña inicial de su usuario es
**exactamente su matrícula** (ej. `QA_Dr_Cardio` / matrícula `QA-1001` → password `QA-1001`).
La matrícula:
- Es un dato **público/semi-público** dentro del sistema — visible en listados de médicos para
  GERENTE/ADMINISTRATIVO, y potencialmente conocida por el propio médico, colegas o pacientes
  (aparece en recetas, credenciales profesionales, etc. en el mundo real).
- No se fuerza cambio de contraseña en el primer login (no se encontró ningún flag
  `debeCambiarPassword` ni redirección forzada a un formulario de cambio de contraseña).
- No hay política de complejidad de password separada — cualquiera que conozca la matrícula de
  un médico (o pueda adivinarla, dado que este proyecto las usa con un patrón `QA-100X`
  secuencial para los médicos QA, y podría haber patrones similares en matrículas reales) puede
  loguearse como ese médico.

**Severidad**: Media — requiere que el atacante ya conozca (o adivine) la matrícula, que no es
tan secreta como una contraseña debería ser, pero tampoco es trivialmente pública para un
atacante externo sin acceso previo al sistema. No se corrige en esta sesión (pedido explícito del
usuario: "repórtalo... no lo corrijas todavía") — queda documentado para decisión de producto
(alternativas: contraseña aleatoria generada + envío por email/link de activación, o forzar
cambio de contraseña en el primer login).

## No cubierto en esta fase (alcance/tiempo)
- **Menús de Home por rol**: no se verificó en vivo la SPA (`app.js`/`NAV_CONFIG`) — es un chequeo
  de UI que se hará junto con la pasada de Playwright (Fase 8).
- **Mensajes de error comparados textualmente contra la tabla 1.7** de la propuesta original: los
  mensajes de cada RN fueron capturados y quedaron documentados en cada fase de este informe,
  pero no se hizo el diff línea por línea contra el documento original
  (`docs/Entrega primer cuatrimestre Policaro (1).md`, sección 1.7) por alcance de tiempo — queda
  como tarea pendiente si se necesita ese nivel de detalle.
- **JWT expirado real**: no se esperaron las 24hs ni se fabricó un token firmado manualmente con
  fecha vencida; el mecanismo está verificado por código únicamente.
