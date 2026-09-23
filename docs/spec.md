# Spec — Sistema de Gestión Integral Consultorios Mitre

## 1. Overview

Sistema web de gestión integral para un centro médico (Consultorios Mitre), que reemplaza planillas Excel, Word y fichas físicas por una plataforma centralizada. Organizado en 8 módulos independientes con acceso diferenciado por rol.

**Objetivo de negocio:** eliminar duplicación de datos, pérdida de información y errores humanos en la operatoria diaria (turnos, historias clínicas, facturación, arrendamiento de consultorios).

## 2. Actores / Roles

| Rol | Descripción |
|---|---|
| Gerente (dueña) | Acceso total: gestión, reportes, dashboard, configuración |
| Administrativo (secretaria) | Gestión operativa diaria: pacientes, turnos, cobros |
| Médico | Agenda propia, historia clínica de sus pacientes, evoluciones |

**Decisión de producto (post-propuesta):** el paciente NO tiene usuario ni inicia sesión en el sistema. Es un registro de datos gestionado por Gerente/Administrativo (alta, edición, asignación de turnos), sin login, sin email de acceso y sin pantallas propias ("Mis Turnos", "Mis Datos", "Cartilla Médica" no existen). Esto difiere de la propuesta técnica original (`Propuesta_tecnica_V_Final.md`), que sí contemplaba un rol Paciente con acceso de solo lectura.

Un usuario tiene **un único rol activo a la vez** (RN-015). El rol determina las pantallas y permisos accesibles.

### 2.1 Desviaciones de permisos respecto al documento original (decisiones de producto, 2026-08-23)

| Módulo | Desviación | Decisión |
|---|---|---|
| Pacientes | El documento solo da acceso completo (alta/baja/edición) a Administrativo, sin permisos de alta para Gerente | Gerente queda **solo lectura** en Pacientes (`PacienteController`: POST/PUT/DELETE restringidos a `ADMINISTRATIVO`) |
| Médicos / Agenda | El documento dice que Administrativo puede "Gestionar agenda", pero la agenda depende de crear/dar de baja un contrato de arrendamiento, restringido a Gerente | Se mantiene **GERENTE estricto** para alta/baja de contratos. "Gestionar agenda" de Administrativo se limita a consultar/ver, no a crear o dar de baja contratos |
| Historias Clínicas | El código enmascaraba el contenido clínico (motivo/diagnóstico/tratamiento/etc.) para Administrativo | Se **alineó con el documento**: Administrativo ve el contenido clínico completo, igual que Médico (`HistoriaClinicaServiceImpl.mapEvolucionToDTO` ya no enmascara) |
| Reportes | El documento le da a Administrativo acceso puntual a "Turnos por fecha", "Pacientes ausentes" y "Cobros pendientes" | **No se tocó `ReporteController`** — sus 5 endpoints (`dashboard`, `dashboard/recalcular`, `facturacion/medico`, `facturacion/obra-social`, `consultorios`) son reportes gerenciales agregados, no los 3 reportes puntuales del documento, que ya son alcanzables por Administrativo hoy vía `GET /api/turnos?fecha=`/`?estado=NO_ASISTIO` (`TurnoController`) y `GET /api/facturacion` filtrable por `estadoPago` (`FacturacionController`) |
| Facturación (exportación) | El documento da "Exportar reportes: Sí" a Administrativo (RF-R3, exportación a Excel) | **Gap pre-existente, no resuelto acá**: RF-R3 no está implementado para ningún rol (no hay endpoint de exportación en todo el backend). Queda pendiente como feature nueva, fuera del alcance de esta limpieza de permisos |

## 3. Módulos y prioridad de desarrollo

| Módulo | Prioridad | Etapa |
|---|---|---|
| Usuarios y Autenticación | Alta / Crítica | 1 |
| Pacientes | Alta / Crítica | 1 |
| Médicos y Agenda | Alta / Crítica | 1 |
| Turnos | Alta / Crítica | 2 |
| Historias Clínicas | Alta / Crítica | 2 |
| Facturación y Liquidaciones | Media / Importante | 2 |
| Arrendamiento de Módulos | Media / Importante | 3 |
| Reportes y Dashboard Gerencial | Media / Importante | 3 |
| Obras Sociales | Media / Importante | 1 |

Al cerrar Etapa 2 el consultorio ya debe poder operar el flujo crítico completo (turnos, pacientes, historias clínicas, cobro básico).

## 4. Requerimientos funcionales por módulo

La Entrega original describe las funcionalidades en prosa, por módulo y por etapa ("Esta funcionalidad permitirá…", bloques "Funcionalidad" / "Restricciones de Integridad"); no numera requerimientos funcionales. Los códigos `RF-XX` de esta sección se introdujeron acá, en `spec.md`, para poder trazar y testear cada punto funcional — no existen en la propuesta técnica. La mayoría están destilados de esa prosa; los que no corresponden a nada del documento original son funcionalidad agregada después de la Entrega: todo §4.9 (Obras Sociales), RF-T8 (cancelar un día de la agenda del médico) y RF-F8 (filtro por fecha en Facturación), todos registrados también en `docs/ANEXO_CAMBIOS_POST_ENTREGA.md`. Los ítems tachados o re-scopeados (RF-M2, RF-F1) marcan desvíos respecto del texto original, no requerimientos nuevos.

### 4.1 Usuarios
- RF-U1: Alta manual de usuario (email, password, rol) por Administrativo/Gerente. Alta de rol Médico solo vía módulo Médicos.
- RF-U2: Baja lógica (soft delete vía campo `Visible`/estado), reactivable.
- RF-U3: Modificación de email y rol (rol solo editable por Gerente; Médico/Paciente/Administrativo no pueden auto-modificar su rol).
- RF-U4: Búsqueda por Email, Rol, Matrícula, DNI / Pasaporte, con toggle "incluir inactivos".
- RF-U5: Passwords almacenadas con hash.

### 4.2 Pacientes
- RF-P1: Alta de paciente (nombre, apellido, **DNI / Pasaporte** único, teléfono, obra social, credencial, dirección, fecha nacimiento). El identificador es un único campo (columna `Pacientes.DNI`) que admite el DNI argentino o, para un paciente extranjero sin DNI, el número de pasaporte (Entrega §2; ver Anexo §I). Sin validación de formato.
- RF-P2: Al guardar, se crea automáticamente la Historia Clínica vacía asociada (1 a 1).
- RF-P3: El documento (DNI / Pasaporte) es inmutable una vez creado (RN-008) y único incluso entre inactivos (RN-009).

### 4.3 Médicos y Agenda
- RF-M1: Alta de médico (nombre, matrícula única, especialidad, importe consulta, fecha inicio actividad). Crea automáticamente su usuario de acceso.
- RF-M2: ~~Configuración de agenda (día, hora inicio/fin, duración de turno, cupo máximo, consultorio) por médico~~ — **decisión de producto:** esto ahora se configura al crear el Contrato de Arrendamiento (§4.8), que es la única fuente de horario del médico y dispara la generación automática de turnos.
- RF-M3: Relación N:M médico–obra social. La gestiona **tanto el propio médico** (agregar/quitar obras sociales de su cartilla y fijar el coseguro de cada una, desde "Mis Datos") **como Gerente/Administrativo** en nombre de cualquier médico (módulo Médicos, p. ej. durante el alta). El coseguro es un dato propio de esa relación puntual (no de la obra social ni fijo por médico). Alinea con el relevamiento original: la hoja del médico con sus obras sociales "es actualizable cada vez que el médico lo desee" (ver Anexo §G.1). Crear una obra social nueva en el catálogo sigue siendo admin-only.
- RF-M4: Relación médico–prestación. Igual que RF-M3: el propio médico da de alta/edita/quita sus prestaciones (nombre, duración estimada, importe particular) desde "Mis Prestaciones"; Gerente/Administrativo también pueden hacerlo por él. El `importeParticular` **es el importe que se factura** cuando un turno se reserva para esa prestación (ver RF-F1 / Anexo §H); si el turno no tiene prestación específica se usa `importeConsulta` del médico (admin-only, RN-006). El campo `Tipo` de una prestación ya no se usa en la UI (la columna sigue en la base; ver Anexo §G.1.7).
- RF-M5: Baja de médico bloqueada si tiene turnos futuros sin reasignar (RN-012).

### 4.4 Turnos
- RF-T1: Generación automática de turnos "Disponible" al **crear un Contrato de Arrendamiento** (médico + consultorio + día + horario + duración de turno + cupo). Si el contrato no tiene fecha fin, se generan turnos para los próximos 6 meses desde el inicio. Ya no existe un botón de "Generar Turnos" manual.
- RF-T2: Reserva de turno (búsqueda por fecha/médico/especialidad, tipo de consulta, método de pago, cobertura OS + copago). La obra social se elige de un **desplegable acotado a la cartilla del médico** (`GET /medicos/{id}/obras-sociales`, RF-M3) — no texto libre ni el catálogo completo. Al elegirla, el monto a cobrar al paciente se **autocompleta como el importe de la consulta/prestación elegida menos el coseguro** que ese médico pactó para esa obra social puntual (el coseguro es lo que la obra social le paga al médico directamente, no lo que paga el paciente — RN-025), quedando editable para ajustar un caso excepcional. Si es "Particular" (sin obra social), se autocompleta con el importe completo. Si la ficha del paciente ya tiene cargada una obra social que el médico acepta, se preselecciona sola al elegir el paciente (ver Anexo §L, §M).
- RF-T3: Cancelación — vuelve a "Disponible" solo si es antes del horario de atención; si no, queda "Cancelado".
- RF-T4: Estado "En Espera" al llegar el paciente, visible en tiempo real en la agenda del médico.
- RF-T5: Estados del ciclo de vida: Disponible → Reservado → En Espera → Atendido | Cancelado | No Asistió.
- RF-T6: Un turno "Atendido" es inmutable (RN-004).
- RF-T7: Agenda médica con filtros (fecha, estado) e indicadores (total del día, pendientes).
- RF-T8: Cancelación de un día completo (o rango) de la agenda de un médico — ej. vacaciones/licencia. `POST /api/turnos/cancelar-dia` cancela en lote los turnos futuros del médico en el período (los "Disponible" y los "Reservado"/"En Espera" pasan a "Cancelado"; los "Atendido" y ya cerrados no se tocan — RN-021) y devuelve la lista de pacientes con turno reservado para contactarlos. GERENTE/ADMINISTRATIVO sobre cualquier médico; MEDICO solo sobre su propia agenda. El motivo es informativo, no se persiste.

### 4.5 Historias Clínicas
- RF-H1: Historia clínica única por paciente, autogenerada.
- RF-H2: Evolución clínica por atención (motivo, diagnóstico, tratamiento, indicaciones, estudios solicitados, observaciones) — solo la crea/edita el médico responsable (RN-010).
- RF-H3: Adjuntos por evolución (estudios, recetas, imágenes, laboratorio). Subir/descargar: ADMINISTRATIVO (cualquiera) y MEDICO (solo de pacientes propios). Extensiones permitidas: PDF, JPG, PNG.
- **Acceso (§4.2 / Constitución §3):** GERENTE no accede a Historias Clínicas (403). ADMINISTRATIVO ve el contenido clínico completo de cualquier paciente. Un MEDICO solo abre la HC y descarga adjuntos de **pacientes vinculados a algún turno propio** — `GET /api/historias-clinicas/paciente/{id}` y `.../adjuntos/{id}/descargar` validan pertenencia con el mismo criterio RN-010 que `agregarEvolucion` (antes no lo hacían: cualquier médico leía la HC de cualquier paciente por id — ver Anexo §J.2).
- RF-H4: Evoluciones no se eliminan; se marcan "Anuladas" con motivo obligatorio (RN-011).
- RF-H5: ~~Paciente ve solo adjuntos propios, no el texto clínico~~ — no aplica: el paciente no tiene acceso al sistema.
- RF-H6: Auditoría de acceso: usuario, fecha/hora, acción sobre la historia clínica.

### 4.6 Facturación y Liquidaciones
- RF-F1: Se genera automáticamente un registro de facturación pendiente cuando el paciente pasa a **"En Espera"** (así la administración puede cobrar mientras el paciente espera) o directo a "Atendido". Es idempotente: marcar "Atendido" después de "En Espera" no duplica la factura. El **`importeTotal`** nace del `importeParticular` de la prestación con la que se reservó el turno; si el turno no tiene prestación específica o el médico no le puso precio, se usa `importeConsulta` del médico. Ese importe puede ajustarse a mano al registrar el cobro (p. ej. varias prestaciones en una misma atención). Si el turno se cancela o se marca "No Asistió" habiendo generado la factura, ésta se anula (para no bloquear la liquidación — RN-005); un cobro ya registrado queda para reintegro manual. Si más tarde el **mismo cupo** se re-reserva para otra atención, esa factura `ANULADO` se **reutiliza** con los datos nuevos y vuelve a `PENDIENTE` (una sola factura por turno; ver Anexo §J.1).
- RF-F2: Registro de cobro: método de pago, importe total, copago (ambos editables — cubre el caso de ajustar el importe a mano, p. ej. varias prestaciones en una misma atención). "Cubierto por OS" ya **no se carga a mano**: es un valor derivado de solo lectura (`Total − Copago`), recalculado en vivo. Se puede disparar desde el módulo Facturación o desde el propio turno "En Espera" en el tablero. No se puede cobrar dos veces una factura ya `PAGADO`/`ANULADO`. Sin `importeTotal` responde 400 (antes 500 — Anexo §J.3).
- RF-F3: Split automático 70% médico / 30% consultorio, configurado a nivel médico (no editable por turno) (RN-006), calculado sobre lo efectivamente cobrado en mano en el consultorio — no sobre el precio de lista de la consulta cuando hay obra social de por medio (RN-025, Anexo §M).
- RF-F4: Liquidación por período (diaria/semanal/mensual) — bloqueada si hay turnos atendidos sin cobro registrado (RN-005).
- RF-F5: Liquidación "Emitida" es inmutable; para corregir se anula y se genera una nueva (RN-007).
- RF-F6: Estados de facturación: Pendiente, Pagado, Parcial, Anulado, Reintegrado.
- RF-F7: Auditoría de modificaciones financieras (usuario, fecha, valores antes/después, motivo).
- RF-F8: Listado de Facturación con filtro por fecha — `GET /api/facturacion?desde=&hasta=` (un día o un rango; sin parámetros, todas). El frontend arranca en el día de hoy y suma un filtro por Estado de Pago. Reusa `FacturacionRepository.findByFechaFacturacionBetween`.

### 4.7 Arrendamiento de Consultorios
- RF-A1: ABM de consultorios físicos (número, equipamiento, estado, ubicación).
- RF-A2: Asignación médico–consultorio con días/horarios y porcentajes pactados.
- RF-A3: Validación automática de superposición horaria — un consultorio no puede asignarse a dos médicos en el mismo horario (RN-013).
- RF-A4: Tiempo mínimo de uso: 4 horas por jornada (RN-014).
- RF-A5: Registro de uso real (Uso_Consultorio) y cierre diario económico (Cierre_Diario) por médico/consultorio.
- RF-A6: Estados de consultorio: Disponible, Ocupado, Bloqueado, En mantenimiento, Fuera de servicio.

### 4.8 Reportes y Dashboard Gerencial
- RF-R1: Reportes de facturación (por médico, paciente, obra social, día, mes), cobros pendientes, copagos. Todos los reportes de período usan `LocalTime.MAX` como límite superior del día "hasta" — incluyen lo facturado en la última fracción del día (antes "por médico" y "por obra social" cortaban en `23:59:59`; Anexo §J.4).
- RF-R2: Reportes operativos: uso de consultorios, horas ociosas, rentabilidad por módulo, liquidaciones médicas.
- RF-R3: Exportación a Excel, impresión, visualización gráfica.
- RF-R4: Dashboard con indicadores del día (turnos del día, atendidos, cancelados/no asistió, facturación del día, cobros pendientes, nuevos pacientes). La tabla `Dashboard_Gerencial` guarda una fila por fecha como registro histórico. **El dashboard del día en curso siempre se recalcula al consultarlo** (`GET /api/reportes/dashboard`): la fila cacheada solo se servía tal cual para fechas pasadas; para "hoy" quedaba congelada en la primera consulta de la jornada (ver Anexo §F.1). La facturación del día excluye las facturas `ANULADO`/`REINTEGRADO`.

### 4.9 Obras Sociales
- RF-OS1: Alta de obra social (Gerente/Administrativo): nombre (único, requerido), código/sigla (opcional), plan (opcional), requiere bono de consulta previo (Sí/No), observaciones administrativas.
- RF-OS2: Baja lógica (`Visible`), reactivable. Bloqueada si la obra social tiene médicos activos asociados (RN-016) — el sistema advierte antes de confirmar.
- RF-OS3: Modificación de todos los campos del alta (nombre, código/sigla, plan, requiere bono, observaciones); el nombre debe seguir siendo único tras la edición.
- RF-OS4: Búsqueda/listado con filtros por nombre, "requiere bono" y estado (activa/inactiva); resultado muestra nombre, código/sigla, plan, requiere bono, cantidad de médicos asociados y estado.
- RF-OS5: Selección desde catálogo (no texto libre) al asociar obras sociales a un Médico (§4.3) o a un Paciente (§4.2).

## 5. Reglas de negocio (backend, no solo frontend)

| ID | Módulo | Regla |
|---|---|---|
| RN-001 | Turnos | Solo se reserva un turno en estado "Disponible" |
| RN-002 | Turnos | Un paciente no puede tener 2 turnos reservados en el mismo horario |
| RN-003 | Turnos | Un médico no puede tener 2 turnos en el mismo horario |
| RN-004 | Turnos | Un turno "Atendido" es inmutable, no vuelve a "Disponible" ni se cancela retroactivamente |
| RN-005 | Facturación | No se liquida si hay turnos atendidos sin cobro registrado |
| RN-006 | Facturación | Split 70/30 se configura a nivel médico, no por turno |
| RN-007 | Facturación | Liquidación "Emitida" es inmutable — se anula y regenera |
| RN-008 | Pacientes | Documento (DNI / Pasaporte) inmutable una vez creado |
| RN-009 | Pacientes | Documento (DNI / Pasaporte) único, incluso entre inactivos |
| RN-010 | Historia Clínica | Un médico solo accede (lee, crea, edita, anula, adjunta) a la HC de pacientes vinculados a un turno propio; solo el médico responsable edita/anula su evolución |
| RN-011 | Historia Clínica | Evoluciones no se eliminan, solo se anulan con motivo |
| RN-012 | Médicos | Baja bloqueada si hay turnos futuros sin reasignar |
| RN-013 | Arrendamiento | Un consultorio no puede asignarse a 2 médicos en el mismo horario |
| RN-014 | Arrendamiento | Uso mínimo de consultorio: 4 horas por jornada |
| RN-015 | Usuarios | Un usuario tiene un único rol activo simultáneo |
| RN-016 | Obras Sociales | No se da de baja una obra social con médicos activos asociados |
| RN-017 | Médicos | Un médico solo gestiona su propia cartilla: sus prestaciones y las obras sociales con las que trabaja (incluido el coseguro de cada una), nunca las de otro médico |
| RN-018 | Facturación | No se liquida si hay turnos "Atendido" sin ningún cobro asociado directamente (aunque no exista factura pendiente) |
| RN-019 | Obras Sociales | No se da de baja una obra social con pacientes activos asociados |
| RN-020 | Usuarios | No se da de baja un usuario que pertenece a un médico activo |
| RN-021 | Turnos | Cancelar un día (o rango) de la agenda de un médico solo afecta turnos futuros del período: los "Disponible" y los "Reservado"/"En Espera" pasan a "Cancelado"; los "Atendido" y ya cerrados no se modifican (RN-004). Los pacientes de turnos reservados se listan para contacto manual. |
| RN-022 | Arrendamiento | No se puede asignar un contrato a un consultorio que no está "Disponible" (Bloqueado, En mantenimiento, Fuera de servicio) |
| RN-023 | Arrendamiento | Un médico no puede tener 2 contratos activos que se superpongan en día/horario, ni siquiera en consultorios distintos |
| RN-024 | Facturación | No se puede generar una liquidación cuyo período se superponga con una liquidación "Emitida" existente del mismo médico |
| RN-025 | Facturación | El split 70/30 médico/consultorio (RN-006) y las sumas de Liquidación, Cierre de Caja y Reportes se calculan sobre lo efectivamente cobrado en mano en el consultorio (Importe Total menos lo cubierto por la obra social), no sobre el precio de lista total — el coseguro que la obra social le paga al médico directamente nunca entra a la caja del consultorio y es 100% del médico |

Cada regla debe devolver el mensaje de error específico definido en la propuesta original al intentar violarse.

RN-016 a RN-025 son funcionalidad agregada en sesiones posteriores a la Entrega original (Obras Sociales y coseguro del médico; RN-019/RN-020 tras un incidente en producción; RN-021 con la cancelación de días del médico el 2026-09-01; RN-022 a RN-024 encontradas en la verificación de QA end-to-end del 2026-09-23, ver Anexo Parte K; RN-025 al corregir la fórmula del coseguro OS el mismo 2026-09-23, ver Anexo Parte M), sin número oficial en la Entrega original — se numeraron a continuación de las 15 reglas fuente para no colisionar con ellas. RN-017 se amplió el 2026-09-02: de "solo el coseguro de su propia relación" a "toda su propia cartilla" (prestaciones + obras sociales), al habilitar que el médico gestione su cartilla como en el relevamiento original (ver Anexo §G). RN-005 y RN-018 son reglas distintas aunque similares en redacción: RN-005 bloquea por facturas con `estadoPago = PENDIENTE`; RN-018 bloquea por turnos "Atendido" sin ningún `Cobro` asociado en absoluto (falla incluso si nunca se generó una Facturación), un caso que RN-005 no cubre.

**RN-019 y RN-020 (2026-08-27) — agregadas tras un incidente real, no una revisión preventiva.** RN-016 solo bloqueaba la baja de una ObraSocial si tenía médicos activos asociados, sin chequear pacientes; no existía ningún chequeo simétrico para bloquear la baja de un Usuario si pertenecía a un Médico activo. En ambos casos, `@SQLRestriction("Visible = 1")` hace que una referencia colgante (`Paciente.ID_ObraSocial` o `Medico.ID_Usuario` apuntando a una fila dada de baja) tire `EntityNotFoundException` al resolver la relación — y como el error ocurre dentro de un `map a DTO` de un listado completo, **una sola fila colgante rompe el endpoint entero para todos los usuarios**, no solo el registro afectado. Así se manifestó en producción: `GET /api/pacientes` y `GET /api/medicos` devolviendo 500 sin ningún cambio de código de por medio — el dato quedó en ese estado por una baja hecha antes de que existiera este chequeo. Reparado en caliente reactivando la ObraSocial y el Usuario puntuales (`PUT .../reactivar`, reversible), y agregadas las reglas para que no vuelva a pasar.

## 6. Flujos integrales (criterios de aceptación end-to-end)

1. **Flujo A — Atención estándar:** Alta paciente → crea HC → Turno reservado → En Espera (se genera la facturación pendiente y se puede **registrar el cobro** acá) → Evolución clínica → Turno Atendido → Liquidación periódica. El cobro también puede registrarse después de "Atendido" si no se hizo en la espera.
2. **Flujo B — Cancelación:** con anticipación vuelve a "Disponible"; sin aviso pasa a "No Asistió" (no vuelve a disponible, queda para reportes). Cancelación masiva: si el médico no atiende un día (vacaciones/licencia), se cancela el día completo (o un rango) desde el tablero de turnos — todos sus turnos futuros de ese período pasan a "Cancelado" y se lista a los pacientes reservados para reprogramarlos (RF-T8 / RN-021).
3. **Flujo C — Alta de médico:** Alta médico (crea su usuario) → configura agenda → genera turnos automáticamente → asignación de consultorio validando superposición.

## 7. Fuera de alcance

- Integraciones externas (APIs de verificación online con obras sociales reales, AFIP, laboratorios, plataformas gubernamentales) — no incluye el catálogo interno de Obras Sociales (§4.9), que es dato maestro propio del sistema.
- Apps móviles nativas (solo web responsivo).
- Compra de hardware / hosting empresarial dedicado (despliegue estándar en Apache Tomcat).

## 8. Requerimientos no funcionales

- Passwords hasheadas, control de acceso por rol en cada endpoint (no solo UI).
- Trazabilidad/auditoría en historia clínica y en movimientos financieros.
- Pruebas unitarias, de integración (turnos↔facturación↔HC↔liquidaciones↔reportes), funcionales, rendimiento (concurrencia) y seguridad.
- Confidencialidad de datos médicos sensibles según rol.

## 9. Frontend — Interfaces y Diseño
## 9.2 Identidad visual

Paleta de colores:

Color	Uso
Azul institucional 
#1A5276	Navegación, encabezados, botones primarios, menú lateral
Azul claro 
#D6EAF8	Fondos de secciones activas, hover de filas
Verde confirmación 
#1E8449	Turnos confirmados, cobros registrados, acciones exitosas
Verde claro 
#D5F5E3	Fondo de mensajes de éxito, badge "Activo"
Rojo error/alerta 
#C0392B	Cancelaciones, errores de validación, deuda vencida
Rojo claro 
#FADBD8	Fondo de mensajes de error inline
Naranja aviso 
#E67E22	Transferencias pendientes, turnos por vencer, cobros demorados
Amarillo claro 
#FEF9E7	Fondo de alertas operativas no críticas
Gris oscuro 
#2C3E50	Texto principal
Gris claro 
#F4F6F7	Fondos neutros, paneles, filas alternas
Blanco 
#FFFFFF	Fondo base de pantallas y formularios

Sistema de estados visuales (consistente en todos los módulos):

Estado	Color
Disponible	Verde 
#1E8449
Reservado / Pendiente	Naranja 
#E67E22
Confirmado / Activo	Azul 
#1A5276
Atendido / Completado	Verde claro 
#D5F5E3 + texto gris
Cancelado / Inactivo	Rojo claro 
#FADBD8 + texto rojo
En mantenimiento	Gris claro 
#F4F6F7 + texto gris

Tipografía: Inter. Tamaños mínimos: cuerpo de texto 14px, etiquetas de formulario 13px, datos en tablas 14px. Botones táctiles: mínimo 44px de alto (uso desde tablet/móvil).

## 9.3 Principios de diseño (criterios de aceptación de UX)
Claridad sobre decoración: cada pantalla tiene un único objetivo obvio; el botón de acción principal ocupa la posición más prominente, sin elementos decorativos que distraigan.
Color como lenguaje universal: verde = confirmar/completado, rojo = cancelar/error, naranja = aviso preventivo, azul = crear/guardar/información — consistente en todos los módulos.
Mínima cantidad de acciones: operaciones frecuentes accesibles desde la Home Page de cada rol, sin menús anidados.
Validación preventiva: errores inline mientras se escribe, no después de perder la información; botón "Guardar" deshabilitado hasta que el formulario sea válido.
Feedback inmediato: toda acción genera respuesta visual instantánea (loading, confirmación verde, error rojo).
Diseño responsive real: escritorio = 2-3 columnas; tablet horizontal = 2 columnas; móvil = 1 columna, formularios apilados, sin scroll horizontal.
8.4 Home Page por rol

Administrativo: Gestionar Pacientes, Gestionar Turnos, Gestionar Médicos, Gestionar Obras Sociales, Gestionar Usuarios, Facturación, Liquidaciones Médicas, Reportes Operativos, Gestionar Arrendamientos, Ver Disponibilidad de Consultorios, Agenda del Día, Recordatorios Pendientes.

Médico: Agenda Médica (en tiempo real: pacientes en espera, atendidos, ausentes, próximos turnos), Pacientes del Día, Historia Clínica, Evoluciones Clínicas, Mis Turnos, Mis Liquidaciones, Mis Datos. Acceso directo a la historia clínica desde cada turno.

Gerente: redirigido directo al Dashboard Gerencial. Incluye Reportes Financieros/Operativos/Médicos, Facturación General, Liquidaciones Médicas, Gestión de Médicos/Usuarios/Obras Sociales/Arrendamientos/Consultorios, Estadísticas Generales, Configuración del Sistema. Con gráficos, indicadores en tiempo real y alertas operativas.

Paciente: sin Home Page — no tiene usuario ni inicia sesión (ver decisión de producto en §2).