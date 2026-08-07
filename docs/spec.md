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

Al cerrar Etapa 2 el consultorio ya debe poder operar el flujo crítico completo (turnos, pacientes, historias clínicas, cobro básico).

## 4. Requerimientos funcionales por módulo

### 4.1 Usuarios
- RF-U1: Alta manual de usuario (email, password, rol) por Administrativo/Gerente. Alta de rol Médico solo vía módulo Médicos.
- RF-U2: Baja lógica (soft delete vía campo `Visible`/estado), reactivable.
- RF-U3: Modificación de email y rol (rol solo editable por Gerente; Médico/Paciente/Administrativo no pueden auto-modificar su rol).
- RF-U4: Búsqueda por Email, Rol, Matrícula, DNI, con toggle "incluir inactivos".
- RF-U5: Passwords almacenadas con hash.

### 4.2 Pacientes
- RF-P1: Alta de paciente (nombre, apellido, DNI único, teléfono, obra social, credencial, dirección, fecha nacimiento).
- RF-P2: Al guardar, se crea automáticamente la Historia Clínica vacía asociada (1 a 1).
- RF-P3: DNI es inmutable una vez creado (RN-008) y único incluso entre inactivos (RN-009).

### 4.3 Médicos y Agenda
- RF-M1: Alta de médico (nombre, matrícula única, especialidad, importe consulta, fecha inicio actividad). Crea automáticamente su usuario de acceso.
- RF-M2: ~~Configuración de agenda (día, hora inicio/fin, duración de turno, cupo máximo, consultorio) por médico~~ — **decisión de producto:** esto ahora se configura al crear el Contrato de Arrendamiento (§4.8), que es la única fuente de horario del médico y dispara la generación automática de turnos.
- RF-M3: Relación N:M médico–obra social (tabla intermedia).
- RF-M4: Relación médico–prestación.
- RF-M5: Baja de médico bloqueada si tiene turnos futuros sin reasignar (RN-012).

### 4.4 Turnos
- RF-T1: Generación automática de turnos "Disponible" al **crear un Contrato de Arrendamiento** (médico + consultorio + día + horario + duración de turno + cupo). Si el contrato no tiene fecha fin, se generan turnos para los próximos 6 meses desde el inicio. Ya no existe un botón de "Generar Turnos" manual.
- RF-T2: Reserva de turno (búsqueda por fecha/médico/especialidad, tipo de consulta, método de pago, cobertura OS + copago).
- RF-T3: Cancelación — vuelve a "Disponible" solo si es antes del horario de atención; si no, queda "Cancelado".
- RF-T4: Estado "En Espera" al llegar el paciente, visible en tiempo real en la agenda del médico.
- RF-T5: Estados del ciclo de vida: Disponible → Reservado → En Espera → Atendido | Cancelado | No Asistió.
- RF-T6: Un turno "Atendido" es inmutable (RN-004).
- RF-T7: Agenda médica con filtros (fecha, estado) e indicadores (total del día, pendientes).

### 4.5 Historias Clínicas
- RF-H1: Historia clínica única por paciente, autogenerada.
- RF-H2: Evolución clínica por atención (motivo, diagnóstico, tratamiento, indicaciones, estudios solicitados, observaciones) — solo la crea/edita el médico responsable (RN-010).
- RF-H3: Adjuntos por evolución (estudios, recetas, imágenes, laboratorio).
- RF-H4: Evoluciones no se eliminan; se marcan "Anuladas" con motivo obligatorio (RN-011).
- RF-H5: ~~Paciente ve solo adjuntos propios, no el texto clínico~~ — no aplica: el paciente no tiene acceso al sistema.
- RF-H6: Auditoría de acceso: usuario, fecha/hora, acción sobre la historia clínica.

### 4.6 Facturación y Liquidaciones
- RF-F1: Al pasar un turno a "Atendido" se genera automáticamente un registro de facturación pendiente.
- RF-F2: Registro de cobro: método de pago, importe total, cubierto por OS, copago.
- RF-F3: Split automático 70% médico / 30% consultorio, configurado a nivel médico (no editable por turno) (RN-005).
- RF-F4: Liquidación por período (diaria/semanal/mensual) — bloqueada si hay turnos atendidos sin cobro registrado (RN-006).
- RF-F5: Liquidación "Emitida" es inmutable; para corregir se anula y se genera una nueva (RN-007).
- RF-F6: Estados de facturación: Pendiente, Pagado, Parcial, Anulado, Reintegrado.
- RF-F7: Auditoría de modificaciones financieras (usuario, fecha, valores antes/después, motivo).

### 4.7 Arrendamiento de Consultorios
- RF-A1: ABM de consultorios físicos (número, equipamiento, estado, ubicación).
- RF-A2: Asignación médico–consultorio con días/horarios y porcentajes pactados.
- RF-A3: Validación automática de superposición horaria — un consultorio no puede asignarse a dos médicos en el mismo horario (RN-013).
- RF-A4: Tiempo mínimo de uso: 4 horas por jornada (RN-014).
- RF-A5: Registro de uso real (Uso_Consultorio) y cierre diario económico (Cierre_Diario) por médico/consultorio.
- RF-A6: Estados de consultorio: Disponible, Ocupado, Bloqueado, En mantenimiento, Fuera de servicio.

### 4.8 Reportes y Dashboard Gerencial
- RF-R1: Reportes de facturación (por médico, paciente, obra social, día, mes), cobros pendientes, copagos.
- RF-R2: Reportes operativos: uso de consultorios, horas ociosas, rentabilidad por módulo, liquidaciones médicas.
- RF-R3: Exportación a Excel, impresión, visualización gráfica.
- RF-R4: Dashboard con indicadores precalculados (tabla `Dashboard_Gerencial`) para no recalcular en tiempo real.

## 5. Reglas de negocio (backend, no solo frontend)

| ID | Módulo | Regla |
|---|---|---|
| RN-001 | Turnos | Solo se reserva un turno en estado "Disponible" |
| RN-002 | Turnos | Un paciente no puede tener 2 turnos reservados en el mismo horario |
| RN-003 | Turnos | Un médico no puede tener 2 turnos en el mismo horario |
| RN-004 | Turnos | Un turno "Atendido" es inmutable, no vuelve a "Disponible" ni se cancela retroactivamente |
| RN-005 | Facturación | Split 70/30 se configura a nivel médico, no por turno |
| RN-006 | Facturación | No se liquida si hay turnos atendidos sin cobro registrado |
| RN-007 | Facturación | Liquidación "Emitida" es inmutable — se anula y regenera |
| RN-008 | Pacientes | DNI inmutable una vez creado |
| RN-009 | Pacientes | DNI único, incluso entre inactivos |
| RN-010 | Historia Clínica | Solo el médico responsable edita su evolución |
| RN-011 | Historia Clínica | Evoluciones no se eliminan, solo se anulan con motivo |
| RN-012 | Médicos | Baja bloqueada si hay turnos futuros sin reasignar |
| RN-013 | Arrendamiento | Un consultorio no puede asignarse a 2 médicos en el mismo horario |
| RN-014 | Arrendamiento | Uso mínimo de consultorio: 4 horas por jornada |
| RN-015 | Usuarios | Un usuario tiene un único rol activo simultáneo |

Cada regla debe devolver el mensaje de error específico definido en la propuesta original al intentar violarse.

## 6. Flujos integrales (criterios de aceptación end-to-end)

1. **Flujo A — Atención estándar:** Alta paciente → crea HC → Turno reservado → En Espera → Evolución clínica → Turno Atendido → Cobro automático generado → Liquidación periódica.
2. **Flujo B — Cancelación:** con anticipación vuelve a "Disponible"; sin aviso pasa a "No Asistió" (no vuelve a disponible, queda para reportes).
3. **Flujo C — Alta de médico:** Alta médico (crea su usuario) → configura agenda → genera turnos automáticamente → asignación de consultorio validando superposición.

## 7. Fuera de alcance

- Integraciones externas (APIs de obras sociales, AFIP, laboratorios, plataformas gubernamentales).
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

Administrativo: Gestionar Pacientes, Gestionar Turnos, Gestionar Médicos, Gestionar Usuarios, Facturación, Liquidaciones Médicas, Reportes Operativos, Gestionar Arrendamientos, Ver Disponibilidad de Consultorios, Agenda del Día, Recordatorios Pendientes.

Médico: Agenda Médica (en tiempo real: pacientes en espera, atendidos, ausentes, próximos turnos), Pacientes del Día, Historia Clínica, Evoluciones Clínicas, Mis Turnos, Mis Liquidaciones, Mis Datos. Acceso directo a la historia clínica desde cada turno.

Gerente: redirigido directo al Dashboard Gerencial. Incluye Reportes Financieros/Operativos/Médicos, Facturación General, Liquidaciones Médicas, Gestión de Médicos/Usuarios/Arrendamientos/Consultorios, Estadísticas Generales, Configuración del Sistema. Con gráficos, indicadores en tiempo real y alertas operativas.

Paciente: sin Home Page — no tiene usuario ni inicia sesión (ver decisión de producto en §2).