# MediSpace — Propuesta Técnica y Modelo de Datos

> Documento de referencia consolidado en Markdown. Fuente: *Propuesta Técnica* (Poli Software, 12/05/2026) para Consultorios Mitre. Este archivo es la especificación funcional y de datos "de origen" del proyecto — se usa como línea base para auditar qué está implementado, qué falta y qué se desvió (ver Anexo al final).

**Roles del sistema:** Gerente (dueña), Administrativo (secretaria), Médico. El rol **Paciente no tiene login** (ver Anexo — desvío confirmado respecto al texto original de esta propuesta, que sí lo contemplaba).

**Principio de arquitectura:** el sistema se programa **por módulo, no por rol** (ver sección 3). El rol determina permisos y visibilidad dentro de cada módulo, no una implementación separada por rol.

---

## Índice

1. [Introducción](#1-introducción)
2. [Herramientas a utilizar](#2-herramientas-a-utilizar)
3. [Arquitectura del Sistema — Principio de Modularidad](#3-arquitectura-del-sistema--principio-de-modularidad)
4. [Módulos del Sistema](#4-módulos-del-sistema)
5. [Interfaces](#5-interfaces)
6. [Alcance del Proyecto](#6-alcance-del-proyecto)
7. [Aseguramiento de la Calidad del Software](#7-aseguramiento-de-la-calidad-del-software)
8. [Reglas del Negocio](#8-reglas-del-negocio)
9. [Flujo Integral del Sistema](#9-flujo-integral-del-sistema)
10. [Base de Datos](#10-base-de-datos)
11. [Anexo — Desvíos y decisiones confirmadas durante el desarrollo](#11-anexo--desvíos-y-decisiones-confirmadas-durante-el-desarrollo)

---

## 1. Introducción

Sistema de gestión integral diseñado para cubrir las necesidades operativas y funcionales identificadas durante el relevamiento realizado en Consultorios Mitre. Se estructura en módulos independientes con acceso diferenciado según el rol de cada usuario.

Objetivo: reemplazar los procesos manuales vigentes (planillas Excel, documentos Word, fichas de papel, carpetas físicas) por una solución centralizada que elimine duplicación de datos, pérdida de información y errores humanos.

**Áreas críticas identificadas:**
- Módulo de usuarios
- Módulo de pacientes
- Módulo de médicos
- Módulo de turnos
- Módulo de historias clínicas
- Módulo de facturación
- Módulo de reportes
- Módulo de arrendamiento

**Por rol (visión funcional, no de arquitectura — ver sección 3):**
- Pacientes: consultan sus datos personales.
- Médicos: actualizan historias clínicas y gestionan sus agendas.
- Personal administrativo: maneja registros, asigna turnos y autorizaciones de forma centralizada.
- Gerentes: monitorean rendimiento y gestionan al personal.

---

## 2. Herramientas a utilizar

| Categoría | Tecnología | Justificación |
|---|---|---|
| Lenguaje de programación | **Java** | Robustez, portabilidad, adopción empresarial |
| Base de datos | **SQL Server (SSMS)** | Fiabilidad, escalabilidad, rendimiento adecuado para historial clínico, turnos y facturación |
| Despliegue / hosting | **Apache Tomcat** | Entorno estable para apps Java, acceso controlado por rol |

Los archivos asociados a historias clínicas (imágenes, recetas, estudios) se almacenan digitalmente dentro de la infraestructura del sistema.

---

## 3. Arquitectura del Sistema — Principio de Modularidad

> Sección incorporada a partir de la devolución de la cátedra: el sistema debe programarse **por módulo**, no por rol. Los 8 módulos de la sección 4 ya definían "qué puede hacer cada rol" — esta sección aclara **cómo se construye eso en código** para que un mismo módulo no termine reimplementado tres veces (una por rol).

### 3.1 El módulo es la unidad de desarrollo, no el rol

Cada uno de los 8 módulos (Usuarios, Pacientes, Médicos, Turnos, Historias Clínicas, Facturación, Reportes, Arrendamiento) se implementa **una sola vez**, como una porción vertical completa:

`Modelo de datos → Repositorio → Servicio → Controlador/API → Vista de frontend`

El rol **no genera una copia distinta** de esa porción vertical. Genera, dentro de esa misma porción, una **restricción de acceso**: qué endpoints puede invocar, qué acciones puede disparar en la UI, y qué campos puede ver o editar. Esa restricción es exactamente la que ya está tabulada en cada "Acceso y Permisos" de la sección 4 — la tabla de permisos no es documentación aparte, es el contrato que el código de cada módulo debe cumplir.

**Antipatrón a evitar:** construir un módulo de "Turnos para Administrativo" y otro distinto de "Turnos para Médico", cada uno con su propia lógica de negocio. Esto duplica reglas de negocio (por ejemplo RN-001 a RN-004) en dos lugares que inevitablemente se desincronizan con el tiempo — es el problema concreto que señaló la cátedra.

**Patrón correcto:** un único módulo de Turnos, con un único servicio que aplica RN-001 a RN-004 sin importar quién llama, y un único controlador cuyos endpoints están guardados por rol (`@PreAuthorize`) según la tabla de permisos de la sección 4.4.

### 3.2 Cómo se traduce en el backend (Spring Boot)

Por cada módulo:

- **1 conjunto de entidades JPA** (ej.: `Turno`, `Paciente`, `EvolucionClinica`).
- **1 repositorio** por entidad.
- **1 interfaz de servicio + 1 implementación**, donde vive toda la lógica de negocio y las reglas (RN-XXX) — sin ramas de código separadas por rol dentro del servicio.
- **1 controlador**, con endpoints anotados por permiso (`@PreAuthorize("hasRole('GERENTE')")`, `hasAnyRole(...)`, etc.) siguiendo exactamente la tabla de permisos del módulo correspondiente en la sección 4.

El rol autenticado (vía JWT) determina **qué endpoints puede alcanzar** y, dentro de un mismo endpoint, **qué subconjunto de datos le corresponde** (ej.: un Médico que llama a `GET /api/turnos` recibe solo los suyos; un Administrativo recibe todos) — pero es la misma consulta/servicio parametrizada, no una consulta distinta por rol.

### 3.3 Cómo se traduce en el frontend

Por cada módulo, **una única vista/archivo** (el proyecto ya sigue mayormente este patrón: `turnos.js`, `medicos.js`, `pacientes.js`, `arrendamientos.js`, etc. — es el patrón correcto, hay que sostenerlo a medida que se agregan módulos).

El rol determina:
- Qué módulos aparecen en el menú lateral (sidebar) — esto ya está resuelto por rol en `app.js`.
- Qué controles se muestran habilitados/deshabilitados u ocultos **dentro** de la misma vista de un módulo (ej.: el botón "Dar de alta médico" solo se renderiza si el rol es GERENTE, pero es la misma pantalla de Médicos para todos los roles que pueden verla).

No se crean archivos de frontend paralelos por rol para el mismo módulo (ej.: no debe existir `turnos-medico.js` y `turnos-administrativo.js` por separado) — se crea **un** archivo de módulo que lee el rol del usuario autenticado y ajusta qué muestra.

### 3.4 Matriz consolidada Módulo × Rol

Vista resumen de alto nivel (el detalle método por método sigue estando en cada tabla de permisos de la sección 4 — esta matriz es para visualizar de un vistazo que el eje primario son los módulos, no los roles):

| Módulo | Gerente | Administrativo | Médico |
|---|---|---|---|
| Usuarios | Alta / Baja / Modificación / Consulta | Alta (no médicos) / Consulta | Sin acceso |
| Pacientes | Consulta | Alta / Baja / Modificación / Consulta completos | Consulta de pacientes vinculados |
| Médicos | Alta / Baja / Modificación / Consulta completos | Modificación parcial / Consulta | Modificación de datos y agenda propios |
| Turnos | Reservar / Cancelar / Consulta global | Reservar / Cancelar / Sala de espera / Consulta global | Consulta y gestión de agenda propia |
| Historias Clínicas | Sin acceso | Adjuntar estudios / Consulta parcial | Registrar y modificar evoluciones propias |
| Facturación | Consulta global / Liquidaciones / Exportar | Registrar cobros / Liquidaciones / Exportar | Consulta propia |
| Reportes | Dashboard gerencial completo | Sin acceso | Sin acceso |
| Arrendamiento | Crear consultorio / Asignar módulo / Modificar disponibilidad | Asignar módulo / Ver disponibilidad / Modificar disponibilidad | Ver disponibilidad / Ver liquidaciones propias |

Leída por fila (módulo) en vez de por columna (rol), esta tabla es la misma información que ya está en la sección 4 — el cambio es de enfoque de implementación, no de alcance funcional. **Ningún permiso cambia** respecto a lo ya especificado.

---

## 4. Módulos del Sistema

8 módulos, cada uno con restricciones de acceso por rol. Roles: **Gerente**, **Administrativo**, **Médico**, **Paciente**.

### Priorización y clasificación

| Módulo | Prioridad | Clasificación | Etapa |
|---|---|---|---|
| Usuarios y Autenticación | Alta | Crítica | Etapa 1 |
| Pacientes | Alta | Crítica | Etapa 1 |
| Médicos y Agenda | Alta | Crítica | Etapa 1 |
| Turnos | Alta | Crítica | Etapa 2 |
| Historias Clínicas | Alta | Crítica | Etapa 2 |
| Facturación y Liquidaciones | Media | Importante | Etapa 2 |
| Arrendamiento de Módulos | Media | Importante | Etapa 3 |
| Reportes y Dashboard Gerencial | Media | Importante | Etapa 3 |

Al finalizar la Etapa 2 el consultorio ya puede operar sus procesos más críticos (turnos, pacientes, historias clínicas); las etapas posteriores agregan valor sin interrumpir la operatoria.

---

### 4.1 Módulo de Usuarios

4 roles: Gerente, Administrativo, Médico, Paciente.

**Alta de Usuarios**
- Roles **Administrativo** o **Gerente** dan de alta usuarios: Email (requerido), Contraseña (requerido), Rol (Administrativo / Gerente / Médico).
- El alta de usuarios con rol **Médico** es exclusiva de personal gerencial, al momento de registrar la entidad Médico (integridad referencial).

**Baja de Usuarios**
- Baja lógica (estado inactivo, no eliminación física). Reactivables vía checkbox "Incluir usuarios inactivos".

**Modificación de Usuarios**
- Formulario con email y rol editable (condicional).
- Restricciones: roles Médico/Paciente/Administrativo no pueden modificar su propio rol. Solo **Gerente** puede modificar roles.

**Consulta de usuarios**
- Filtros: Email, Rol, Matrícula, DNI/Pasaporte. Botón "Buscar". Botón "Incluir usuarios inactivos".

**Acceso:** Administrativo accede vía botón "Gestionar Usuarios" (alta, baja, modificación, consulta).

Contraseñas almacenadas con hash.

**Permisos**

| Funcionalidad | Gerente | Administrativo | Médico | Paciente |
|---|---|---|---|---|
| Alta usuarios | Sí | Sí (prohibido médicos) | No | No |
| Modificar usuarios | Sí | Parcial | No | No |
| Ver usuarios | Sí | Sí | No | No |

El rol Paciente tiene acceso de solo lectura a su información médica (cartilla y estudios adjuntos). No opera sobre ningún módulo.

---

### 4.2 Módulo de Pacientes

Reemplaza el sistema de fichas de papel. Identificación unívoca por DNI o pasaporte.

**Alta de Pacientes** — Botón "Registrar Paciente". Datos: Nombre y Apellido (req.), DNI/Pasaporte (req., único), Fecha de Nacimiento (req.), Obra Social y N° de Credencial, Teléfono y Dirección (opcional). Registro exclusivo de personal administrativo.

**Baja de Pacientes** — Baja lógica (no eliminación física), botón "Eliminar", reactivable vía "Incluir usuarios inactivos".

**Acceso y Permisos**
- Secretarias: acceso completo (altas, bajas, modificaciones, botón "Dar Turno").
- Pacientes: sin acceso a este módulo.

**Modificación de Pacientes** — Editable: Nombre y Apellido, Teléfono, Dirección, Obra Social, N° de Credencial, Plan, Fecha de Nacimiento.
Restricciones: DNI/Pasaporte no modificable una vez registrado. Email del usuario no modificable desde este módulo.

**Consulta de Pacientes** — Filtros: Nombre y Apellido, DNI/Pasaporte, Obra Social, Teléfono. Resultado: Nombre y Apellido, DNI/Pasaporte, Obra Social, Teléfono, Estado. Opción "Incluir pacientes inactivos".

**Restricciones de integridad**
- DNI/Pasaporte único por paciente.
- Cada paciente tiene un único usuario del sistema *(ver Anexo — desvío: ya no aplica, Paciente no tiene usuario)*.
- Las obras sociales deben existir previamente para poder asignarse.

**Acceso a la información**
- Administrativo: visualiza y gestiona todos los pacientes.
- Médico: solo pacientes asociados a turnos o historias clínicas vinculadas.
- Paciente: sin acceso a este módulo.

**Estados posibles:** Activo, Inactivo, Fallecido.

**Relación con Historias Clínicas:** cada paciente puede tener múltiples registros de historias clínicas asociados.

---

### 4.3 Módulo de Médicos

Gestiona datos personales, especialidades, obras sociales asociadas y configuración de agenda.

**Alta de Médicos** — Rol **Gerente**, botón "Registrar Médico". Datos: Nombre y Apellido, Matrícula Profesional (req., única), Especialidad, Importe de Consulta Particular, Obras Sociales con las que trabaja, Días y horarios de atención, Duración de turnos, Cupo máximo diario, Fecha de inicio de actividad.
Al completarse el alta: se registra la entidad Médico, se genera el usuario asociado, se asigna el rol Médico.

**Baja de Médicos** — Baja lógica, botón "Eliminar", reactivable vía "Incluir médicos inactivos".

**Modificación de Médicos** — Dos categorías:
- *Datos Personales:* Nombre y Apellido, Especialidad, Importe de Consulta Particular, Obras Sociales asociadas.
- *Configuración de Agenda:* Días de atención, Horarios, Duración de turnos, Cupo máximo diario.

**Restricciones**
- Matrícula única en el sistema.
- Médicos solo modifican su propia información y agenda.
- Administrativos modifican cualquier registro médico.
- La especialidad debe existir previamente en el sistema.

**Consulta de Médicos** — Filtros: Nombre y Apellido, Especialidad, Matrícula, Obra Social. Resultado: Nombre y Apellido, Especialidad, Matrícula, Obras Sociales asociadas, Horarios, Estado.

**Cartilla Médica** — Rol Paciente accede a "Ver Cartilla": Especialidad, Horarios, Obras sociales aceptadas.

**Acceso y Permisos**

| Funcionalidad | Gerente | Administrativo | Médico | Paciente |
|---|---|---|---|---|
| Alta médicos | Sí | No | No | No |
| Modificar médicos | Sí | Parcial | Solo propios datos | No |
| Gestionar agenda | Sí | Sí | Solo propia agenda | No |
| Ver médicos | Sí | Sí | Sí | Cartilla médica |
| Baja médicos | Sí | No | No | No |

**Restricciones de integridad**
- Cada médico tiene un único usuario del sistema.
- Un médico puede trabajar con múltiples obras sociales; una obra social con múltiples médicos.

**Prestaciones Médicas** — Cada médico puede tener asociadas distintas prestaciones/estudios/tratamientos: Nombre de la práctica, Duración estimada, Importe particular, Tipo de prestación, Disponibilidad según especialidad. Se usan en la reserva de turnos.

**Información Contractual** — Fecha de inicio de actividad, Fecha de finalización (opcional), Estado contractual, Observaciones administrativas.

**Relación con Turnos** — La configuración de agenda del médico se usa para generar y gestionar turnos.

---

### 4.4 Módulo de Turnos

Generación automática de disponibilidad, reserva, cancelación, sala de espera y seguimiento de atención.

**Generación Automática de Turnos** — Al configurar la agenda médica (días/horarios de atención, duración de turno, fecha inicio, fecha fin opcional), el sistema genera automáticamente todos los turnos del rango en estado "Disponible".

**Restricciones de integridad**
- Un turno solo puede ser reservado por un paciente a la vez.
- Un paciente no puede tener múltiples turnos reservados en el mismo horario.
- Turnos "Atendido" nunca vuelven a "Disponible".
- Turnos cancelados antes del horario de atención vuelven automáticamente a "Disponible".
- Solo se reservan turnos en estado "Disponible".
- Los médicos solo ven/gestionan turnos de su propia agenda.

**Reserva de Turnos** — Rol Administrativo, botón "Dar Turno". Filtros de búsqueda: Fecha, Médico, Especialidad. Botón "Reservar" → estado "Reservado". Datos solicitados: Tipo de Consulta, Método de Pago, Cobertura por Obra Social (+ copago si aplica).
Tipos de consulta: Consulta Convencional, Estudios, Tratamiento (algunas restringidas según especialidad).

**Cancelación de Turnos** — Botón "Cancelar" → estado "Cancelado", deja de estar disponible para reserva automática.

**Sala de Espera / Tiempo Real** — Administrativo actualiza estado a "En Espera" al llegar el paciente; visible en tiempo real para el médico. Estados: Reservado, En Espera, Atendido, No Asistió — representados con indicadores de color.

**Consulta de Turnos**
- *Administrativa:* filtros DNI paciente, matrícula médico, fecha, estado. Muestra: paciente, DNI, obra social, médico, especialidad, fecha/hora, estado, info de cobro.
- *Pacientes:* sección "Mis Turnos" (médico, especialidad, fecha/hora, estado) + botón "Cancelar".
- *Agenda Médica (rol Médico):* filtrable por fecha y estado (Todos/Pendientes/En Espera/Atendidos/No Asistió). Muestra paciente, fecha/hora, obra social, tipo de consulta, estado. Indicadores: cantidad total del día, cantidad pendientes.

**Gestión de Cobros** — Método de Pago, Importe Total, Importe cubierto por OS, Copago, Fecha, tipo (Consulta/Estudio/Tratamiento). Métodos permitidos: Efectivo, Transferencia Bancaria. El importe total se obtiene automáticamente de la configuración del médico.

**Acceso y Permisos**

| Funcionalidad | Gerente | Administrativo | Médico | Paciente |
|---|---|---|---|---|
| Reservar turnos | Sí | Sí | No | No |
| Cancelar turnos | Sí | Sí | Solo propios | No |
| Gestionar sala de espera | Sí | Sí | No | No |
| Consultar turnos | Sí | Sí | Solo propios | Solo propios |
| Ver agenda médica | Sí | Sí | Solo propia | No |

**Relación con otros módulos:** turnos asociados a pacientes y médicos previamente registrados; disponibilidad depende de la agenda del médico; cobros alimentan reportes y liquidaciones.

---

### 4.5 Módulo de Historias Clínicas

Gestión digital integral, reemplaza fichas físicas.

Objetivos: acceso rápido, trazabilidad, almacenamiento seguro, disponibilidad histórica de evoluciones/diagnósticos/tratamientos.

**Generación de Historia Clínica** — Una única historia clínica por paciente, creada automáticamente al registrarlo (integridad referencial).

**Registro de Evoluciones Clínicas** — Cada atención genera una evolución clínica. Solo el médico responsable de la atención puede registrarla. Datos: Fecha/hora, Médico responsable, Motivo de consulta, Observaciones generales, Diagnóstico, Tratamiento indicado, Indicaciones médicas, Estudios solicitados, Prestación realizada, Observaciones adicionales.

**Consulta de Historias Clínicas** — Filtros: Nombre y Apellido paciente, DNI/Pasaporte, Médico, Fecha de atención. Muestra: datos personales, historial de evoluciones, diagnósticos, tratamientos, estudios, médico responsable.

**Adjuntos y Estudios** — Archivos digitales por evolución clínica: estudios médicos, recetas, imágenes, resultados de laboratorio, documentación complementaria.

**Restricciones de integridad**
- Una historia clínica activa por paciente.
- Cada evolución asociada a una historia clínica existente.
- Solo médicos habilitados registran evoluciones.
- Historias clínicas no se eliminan permanentemente.
- Evoluciones clínicas no se eliminan físicamente (preservan trazabilidad).

**Seguridad y Confidencialidad** — Acceso restringido por rol. El sistema registra: usuario que modificó, fecha/hora de acceso, acciones realizadas.

**Acceso y Permisos**

| Funcionalidad | Gerente | Administrativo | Médico | Paciente |
|---|---|---|---|---|
| Ver historias clínicas | No | Parcial | Sí | No |
| Registrar evoluciones | No | No | Sí | No |
| Adjuntar estudios | No | Sí | Sí | No |
| Modificar evoluciones | No | No | Solo propias | No |

El rol Paciente solo visualiza adjuntos vinculados a sus propias evoluciones (estudios, laboratorio, recetas, imágenes) — no accede al texto de evoluciones, diagnósticos, tratamientos ni indicaciones.

**Estados posibles:** Activa, Inactiva.

---

### 4.6 Módulo de Facturación

Reemplaza carpetas físicas, hojas impresas y seguimiento manual. Vincula facturación con turnos y prestaciones.

**Funcionamiento General** — El circuito arranca automáticamente cuando un turno pasa a "Atendido": se genera un registro de facturación (paciente, médico, consulta). Administrativo completa/valida: método de pago, cobertura OS, copago, estado del cobro, observaciones.

**Automatización de Liquidaciones** — El sistema calcula automáticamente: importe total facturado, % consultorio, % profesional, copagos, totales por OS, totales particulares, ingresos pendientes.
Lógica de negocio: **70% médico / 30% consultorio**. Liquidaciones diarias, semanales o mensuales. Permite recalcular liquidaciones históricas ante anulaciones/reintegros.

**Gestión de Cobros y Métodos de Pago** — Efectivo, Transferencias bancarias, Cobertura OS, Copagos. Transferencias pendientes quedan en estado "Pendiente" hasta validación.

**Trazabilidad y Auditoría** — Se almacena: usuario que operó, fecha/hora, tipo de modificación, valores anteriores/nuevos, motivo del ajuste.

**Consulta de Facturación** — Por médico, paciente, obra social, diaria, mensual, cobros pendientes, copagos, historial. Filtros: médico, paciente, OS, fecha desde/hasta, método de pago, estado del cobro, tipo de consulta.

**Estados de Facturación:** Pendiente, Pagado, Parcial, Anulado, Reintegrado.

**Acceso y Permisos**

| Funcionalidad | Gerente | Administrativo | Médico | Paciente |
|---|---|---|---|---|
| Registrar cobro | Sí | Sí | No | No |
| Ver facturación global | Sí | No | No | No |
| Ver facturación propia | Sí | Sí | Sí | No |
| Generar liquidaciones | Sí | Sí | No | No |
| Exportar reportes | Sí | Sí | No | No |
| Ver comprobantes | Sí | Sí | Sí | Solo propios |

---

### 4.7 Módulo de Reportes

Transforma la información operativa en indicadores para toma de decisiones.

**Dashboard Gerencial** — Exclusivo del rol **Gerente**. Panel centralizado (tarjetas de resumen, gráficos, indicadores comparativos, tablas dinámicas, alertas operativas).

**Indicadores Operativos** — Turnos del día, pacientes atendidos, pacientes ausentes, cancelaciones, especialidades más solicitadas, horarios de mayor demanda.

**Indicadores Médicos** — Pacientes por médico, tasa de ausentismo, agenda ocupada, consultas particulares vs. OS, facturación generada, ranking de facturación.

**Indicadores Financieros** — Ingresos diarios/mensuales, facturación acumulada, cobros pendientes, copagos, OS con mayor volumen, rentabilidad mensual. Por períodos personalizados.

**Indicadores de Arrendamiento** — Horas utilizadas, consultorios más utilizados, rentabilidad por módulo, uso promedio semanal, distribución horaria.

**Reportes disponibles**

*Operativos*

| Reporte | Filtros | Roles |
|---|---|---|
| Turnos por fecha | Fecha, médico, especialidad | Gerente, Administrativo |
| Pacientes ausentes | Fecha, médico | Gerente, Administrativo |
| Cancelaciones | Fecha, motivo | Gerente |
| Tiempo de espera | Médico, fecha | Gerente |

*Médicos*

| Reporte | Filtros | Roles |
|---|---|---|
| Pacientes atendidos | Médico, fecha | Gerente, Médico |
| Historial de consultas | Paciente, fecha | Gerente, Médico |
| Especialidades más demandadas | Fecha | Gerente |
| Agenda ocupada | Médico, período | Gerente, Médico |

*Financieros*

| Reporte | Filtros | Roles |
|---|---|---|
| Facturación total | Fecha | Gerente |
| Facturación por médico | Médico, período | Gerente |
| Cobros pendientes | Estado, fecha | Gerente, Administrativo |
| Ingresos por obra social | Obra social, fecha | Gerente |
| Copagos registrados | Médico, fecha | Gerente |

*Arrendamiento*

| Reporte | Filtros | Roles |
|---|---|---|
| Uso de consultorios | Consultorio, fecha | Gerente |
| Horas ociosas | Consultorio, período | Gerente |
| Rentabilidad por módulo | Consultorio, período | Gerente |
| Liquidaciones médicas | Médico, fecha | Gerente |

**Exportación:** Excel, impresión, compartir, visualización gráfica.

---

### 4.8 Módulo de Arrendamiento

Administra la utilización de consultorios por parte de los profesionales, de forma centralizada.

**Gestión de Consultorios** — Registro por consultorio físico: número identificatorio, equipamiento, estado operativo, observaciones, disponibilidad horaria.

**Asignación de Consultorios** — Configuración: consultorio, días de uso, horarios, tiempo mínimo de utilización, estado contractual, observaciones. El sistema impide superposiciones horarias automáticamente.

**Tiempo mínimo de utilización:** 4 horas por jornada.

**Prevención de conflictos** — Valida automáticamente: doble asignación, superposición de horarios, uso fuera de horario permitido, consultorios bloqueados/en mantenimiento.

**Historial de Utilización** — Registra: médico, consultorio, fecha, horario, cantidad de pacientes, facturación generada, liquidación correspondiente.

**Estados del Consultorio:** Disponible, Ocupado, Bloqueado, En mantenimiento, Fuera de servicio.

**Acceso y Permisos**

| Funcionalidad | Gerente | Administrativo | Médico | Paciente |
|---|---|---|---|---|
| Crear consultorio | Sí | No | No | No |
| Asignar módulo | Sí | Sí | No | No |
| Ver disponibilidad | Sí | Sí | Sí | No |
| Ver liquidaciones | Sí | Sí | Solo propias | No |
| Modificar disponibilidad | Sí | Sí | No | No |

---

## 5. Interfaces

### Interfaz General

Login: Email + Contraseña. El sistema determina el rol y redirige al Home Page correspondiente.
Botón "Registrarse como Paciente" (autogestión de alta). Recuperación de contraseña vía email: enlace único, validez 20 minutos.

### Paleta de Colores

| Color | Nombre | HEX | Uso |
|---|---|---|---|
| Azul Institucional | Principal | `#1A5276` | Nav, encabezados, botones primarios, menú lateral |
| Azul Claro | Secundario | `#D6EAF8` | Fondos activos, hover de filas |
| Verde | Confirmación | `#1E8449` | Turnos confirmados, cobros, acciones exitosas |
| Verde Claro | Fondo éxito | `#D5F5E3` | Confirmaciones, badge "Activo" |
| Rojo | Error / Alerta crítica | `#C0392B` | Cancelaciones, errores, deuda vencida |
| Rojo Claro | Fondo error | `#FADBD8` | Mensajes de error inline |
| Naranja | Aviso preventivo | `#E67E22` | Transferencias pendientes, turnos próximos a vencer |
| Amarillo Claro | Fondo aviso | `#FEF9E7` | Alertas operativas no críticas |
| Gris Oscuro | Texto principal | `#2C3E50` | Contenido, datos |
| Gris Claro | Fondos neutros | `#F4F6F7` | Paneles, filas alternas |
| Blanco | Base | `#FFFFFF` | Fondo principal |

### Sistema de estados visuales (consistente en todos los módulos)

| Estado | Color | Uso típico |
|---|---|---|
| Disponible | Verde `#1E8449` | Turno libre, consultorio disponible, médico activo |
| Reservado / Pendiente | Naranja `#E67E22` | Turno reservado sin confirmar, transferencia pendiente |
| Confirmado / Activo | Azul `#1A5276` | Turno confirmado, médico habilitado, cobro validado |
| Atendido / Completado | Verde claro `#D5F5E3` + texto gris | Turno realizado, evolución cerrada |
| Cancelado / Inactivo | Rojo claro `#FADBD8` + texto rojo | Turno cancelado, paciente de baja, consultorio bloqueado |
| En mantenimiento | Gris claro `#F4F6F7` + texto gris | Consultorio no disponible temporalmente |

### Tipografía

Fuente principal: **Inter**. Cuerpo de texto 14px, etiquetas de formulario 13px, datos en tablas 14px. Botones táctiles mínimo 44px de alto.

*(Ver Anexo — desvío: la implementación final usa Source Serif 4 + IBM Plex Sans/Mono, no Inter, manteniendo la paleta de colores exacta.)*

### Principios de diseño

| Principio | Descripción | Implementación |
|---|---|---|
| Claridad sobre decoración | Cada pantalla, un objetivo obvio | Botón principal en posición prominente, sin decoración distractiva |
| Color como lenguaje universal | Verde=confirmar, Rojo=cancelar/error, Naranja=aviso, Azul=crear/info | Consistente en todos los módulos |
| Mínima cantidad de acciones | Operaciones frecuentes accesibles sin menús anidados | Registrar turno/buscar paciente en pocos clics |
| Validación preventiva | Validar antes de guardar, no después | Borde rojo + mensaje inline, botón Guardar deshabilitado hasta completar |
| Feedback inmediato | Respuesta visual instantánea a cada acción | Confirmación verde / error rojo tras cada acción |
| Diseño responsive real | Adaptación sin pérdida de funcionalidad | 2-3 columnas desktop, 2 tablet, 1 móvil sin scroll horizontal |

### Home Page — Administrativo

Gestionar Pacientes · Gestionar Turnos · Gestionar Médicos · Gestionar Usuarios · Facturación · Liquidaciones Médicas · Reportes Operativos · Gestionar Arrendamientos · Ver Disponibilidad de Consultorios · Agenda del Día · Recordatorios Pendientes · Cerrar Sesión

### Home Page — Médico

Agenda Médica · Pacientes del Día · Historia Clínica · Evoluciones Clínicas · Mis Turnos · Mis Liquidaciones · Mis Datos · Cerrar Sesión

Agenda médica en tiempo real: pacientes en espera, turnos atendidos, pacientes ausentes, próximos turnos. Acceso directo a historia clínica desde cada turno.

### Home Page — Gerencia

Dashboard Gerencial · Reportes Financieros · Reportes Operativos · Reportes Médicos · Facturación General · Liquidaciones Médicas · Gestión de Médicos · Gestión de Usuarios · Gestión de Arrendamientos · Gestión de Consultorios · Estadísticas Generales · Configuración del Sistema · Cerrar Sesión

### Home Page — Paciente *(ver Anexo — rol sin login en la implementación actual)*

Mis Turnos (solo lectura) · Ver Cartilla Médica · Mis Estudios · Mis Datos Personales · Cerrar Sesión

> Nota de arquitectura: estos "Home Page" son configuraciones de **menú por rol** (qué módulos aparecen en el sidebar), no implementaciones separadas de cada módulo — ver sección 3.3.

---

## 6. Alcance del Proyecto

**Incluye:** gestión de usuarios, pacientes, médicos, turnos; historias clínicas digitales; evoluciones clínicas; facturación; liquidaciones médicas; gestión de arrendamiento; dashboard gerencial; reportes operativos y financieros; gestión de consultorios.

**Exclusiones explícitas:**
- Integración con APIs de obras sociales, AFIP, sistemas externos de laboratorio, plataformas gubernamentales.
- Aplicaciones móviles nativas (Android/iOS) — el sistema es web responsivo.
- Hardware: compra de computadoras, servidores físicos, impresoras, equipamiento médico.
- Hosting empresarial dedicado — despliegue estándar en Apache Tomcat.

---

## 7. Aseguramiento de la Calidad del Software

Objetivo: estabilidad, seguridad, rendimiento, escalabilidad, correcto funcionamiento operativo.

- **Pruebas Unitarias** — componentes y módulos aislados.
- **Pruebas de Integración** — Turnos, Facturación, Historias Clínicas, Liquidaciones, Reportes.
- **Pruebas Funcionales** — cumplimiento de requisitos del relevamiento.
- **Pruebas de Rendimiento** — usuarios concurrentes, grandes volúmenes.
- **Pruebas de Seguridad** — autenticación, permisos, encriptación, protección de contraseñas, validación de accesos.
- **Pruebas de Usabilidad** — interfaces intuitivas para todos los roles.

---

## 8. Reglas del Negocio

Deben implementarse a nivel **backend** (no solo frontend). Toda violación debe mostrar el mensaje de error correspondiente.

| ID | Módulo | Regla | Mensaje de error |
|---|---|---|---|
| RN-001 | Turnos | Un turno solo puede reservarse si está "Disponible" | "El turno seleccionado ya no está disponible. Por favor seleccioná otro horario." |
| RN-002 | Turnos | Un paciente no puede tener dos turnos reservados en el mismo día y horario | "El paciente ya tiene un turno reservado en este horario. Verificar en Mis Turnos." |
| RN-003 | Turnos | Un médico no puede tener dos turnos asignados en el mismo horario | "Conflicto de horario: el médico ya tiene un turno programado en este horario." |
| RN-004 | Turnos | Un turno "Atendido" no puede volver a "Disponible" ni cancelarse retroactivamente | "El turno ya fue atendido y no puede modificarse." |
| RN-005 | Facturación | No se puede liquidar a un médico con turnos atendidos sin método de pago registrado | "Existen [N] atenciones sin cobro registrado para este médico. Completar antes de liquidar." |
| RN-006 | Facturación | El % 70/30 no se modifica por turno; se configura a nivel médico | — (validación silenciosa, no editable en el formulario de cobro) |
| RN-007 | Facturación | Una liquidación "Emitida" no puede modificarse; debe anularse y regenerarse | "La liquidación ya fue emitida. Para corregirla, primero anulala desde el historial." |
| RN-008 | Pacientes | El DNI/Pasaporte no puede modificarse una vez registrado | "El DNI no puede editarse. Si hay un error de carga, contactar a administración." |
| RN-009 | Pacientes | No pueden existir dos pacientes con el mismo DNI, aunque uno esté inactivo | "Ya existe un paciente registrado con ese DNI. Verificar en la búsqueda incluyendo inactivos." |
| RN-010 | Historia Clínica | Solo el médico que atendió puede registrar/modificar la evolución correspondiente | "No tenés permisos para modificar esta evolución clínica." |
| RN-011 | Historia Clínica | Las evoluciones no se eliminan; solo se anulan con motivo obligatorio | "Las evoluciones no pueden eliminarse. Podés marcarla como anulada indicando el motivo." |
| RN-012 | Médicos | Un médico no puede darse de baja con turnos reservados/atendidos futuros sin reasignar | "El médico tiene [N] turnos activos. Reasignalós o cancelalos antes de proceder con la baja." |
| RN-013 | Arrendamiento | Un consultorio no puede asignarse a dos médicos en el mismo día y horario | "El consultorio [N] ya está asignado a otro médico en ese horario." |
| RN-014 | Arrendamiento | Tiempo mínimo de uso de consultorio: 4 horas por jornada | "El tiempo de uso mínimo es de 4 horas por jornada." |
| RN-015 | Usuarios | Un usuario no puede tener más de un rol activo simultáneamente | — (validación en alta de usuario; campos mutuamente excluyentes) |

*(Ver Anexo — RN-016 se incorporó durante el desarrollo, fuera de esta numeración original de 15 reglas.)*

> Nota de arquitectura: cada regla vive **una sola vez**, en el servicio del módulo correspondiente (ej.: RN-001 a RN-004 en el servicio de Turnos) — no se reimplementa por rol. El rol solo decide quién puede llegar a disparar la acción que la regla valida.

---

## 9. Flujo Integral del Sistema

### Flujo A — Atención estándar de un paciente

1. **Registro del paciente** — Alta en módulo Pacientes → se crea automáticamente la historia clínica vacía asociada.
2. **Asignación del turno** — Búsqueda por nombre/DNI, "Dar Turno" → médico, especialidad, fecha → turno "Disponible" → "Reservado". Visible en la agenda del médico.
3. **Llegada al consultorio** — Estado → "En Espera", visible en tiempo real para el médico.
4. **Atención médica** — El médico registra la evolución clínica (motivo, diagnóstico, tratamiento, indicaciones, estudios); puede adjuntar archivos. Al cerrar → turno "Atendido" (irreversible).
5. **Registro del cobro** — Facturación genera cobro pendiente automáticamente. Se completa método de pago y copago. Aplica 70/30 automáticamente.
6. **Liquidación** — Se emite la liquidación del período (diaria/semanal/mensual); queda disponible en Reportes.

### Flujo B — Cancelación de turno

1. **Con anticipación** — "Cancelar" → estado "Cancelado" → si es antes del horario de atención, vuelve automáticamente a "Disponible".
2. **No asistencia sin aviso** — Estado → "No Asistió" (no vuelve a "Disponible"); queda registrado para Reportes (ausentismo).

### Flujo C — Incorporación de un médico nuevo

1. **Alta del médico** — Datos personales, matrícula, especialidad, importe, obras sociales, días/horarios, duración de turno → se crea automáticamente el usuario con credenciales.
2. **Generación automática de turnos** — Con la agenda configurada, se generan todos los turnos "Disponible" del rango.
3. **Asignación de consultorio** — Se asigna consultorio físico; el sistema valida que no haya superposición con otro médico en el mismo horario.

### Mapa de dependencias entre módulos

| Módulo | Depende de | Alimenta a |
|---|---|---|
| Usuarios | — | Todos los módulos (control de acceso) |
| Pacientes | Usuarios | Turnos, Historias Clínicas, Facturación |
| Médicos | Usuarios | Turnos, Arrendamiento, Facturación, Reportes |
| Turnos | Pacientes, Médicos | Historias Clínicas, Facturación |
| Historias Clínicas | Pacientes, Turnos | Reportes |
| Facturación | Turnos, Médicos | Reportes |
| Arrendamiento | Médicos | Reportes |
| Reportes | Todos los módulos | — |

---

## 10. Base de Datos

### Usuarios
```
ID_Usuario (PK) Serial NOT NULL
Email VARCHAR(255) NOT NULL UNIQUE
Password_hash VARCHAR(255) NOT NULL
Rol String NOT NULL
Visible BIT
Fecha_creacion DATE NOT NULL
```
Cuentas de acceso al sistema: email, contraseña hasheada, rol.

### Pacientes
```
ID_Paciente (PK) Serial NOT NULL
ID_Usuario (FK) NOT NULL
Nombre VARCHAR(50) NOT NULL
Apellido VARCHAR(50) NOT NULL
DNI VARCHAR(20) NOT NULL UNIQUE
Telefono VARCHAR(20)
ID_ObraSocial (FK)
Numero_Credencial VARCHAR(50)
Direccion VARCHAR(150)
Plan_OS VARCHAR(50)
Fecha_Nacimiento DATE NOT NULL
Estado VARCHAR(20) NOT NULL
Visible BIT
Fecha_Creacion TIMESTAMP NOT NULL
```
Datos personales y de cobertura del paciente. *(Ver Anexo — el FK a Usuario se eliminó en la implementación: Paciente ya no tiene login.)*

### Obra Social
```
ID_ObraSocial (PK) Serial NOT NULL
Nombre String NOT NULL
Visible BIT
```
Tabla de referencia, asignable a pacientes y médicos.

### Médicos
```
ID_Medico (PK) Serial NOT NULL
ID_Usuario (FK) NOT NULL
Nombre VARCHAR(50) NOT NULL
Apellido VARCHAR(50) NOT NULL
Matricula VARCHAR(50) NOT NULL UNIQUE
ID_Especialidad (FK) NOT NULL
Importe_Consulta DECIMAL(10,2)
Estado VARCHAR(20) NOT NULL
Visible BIT
Fecha_Creacion TIMESTAMP NOT NULL
Fecha_Inicio_Actividad DATE NOT NULL
ID_Prestacion (FK)
```
Datos profesionales, ligado a un usuario. Datos contractuales (fechas, %) en `Arrendamiento_Modulo`.
*(Ver Anexo — `ID_Prestacion` como columna suelta es un error de modelado del documento original: solo permite una prestación por médico. Se corrige con la tabla `Medico_Prestacion`.)*

### Especialidades
```
ID_Especialidad (PK) Serial NOT NULL
Nombre VARCHAR(50) NOT NULL UNIQUE
Visible BIT
```
Catálogo de especialidades, asignable a cada médico.

### Agenda_Medico
```
ID_Agenda (PK) Serial NOT NULL
ID_Medico (FK) NOT NULL
ID_Consultorio (FK)
Dia_Semana VARCHAR(20) NOT NULL
Hora_Inicio TIME NOT NULL
Hora_Fin TIME NOT NULL
Duracion_Turno_Min INTEGER NOT NULL
Cupo_Maximo_Diario INTEGER NOT NULL
Visible BIT
```
Disponibilidad horaria por médico y día. Base para generar turnos.
*(Ver Anexo — esta tabla/módulo se eliminó en la implementación: la agenda y generación de turnos ahora dependen de `Arrendamiento_Modulo`.)*

### Medico_ObraSocial
```
ID_Medico (FK) NOT NULL
ID_ObraSocial (FK) NOT NULL
```
Tabla intermedia N:N — qué obras sociales acepta cada médico.

### Prestaciones Médicas
```
ID_Prestacion (PK) NOT NULL
Nombre VARCHAR(50)
```
Catálogo de prestaciones que realiza cada médico.
*(Ver Anexo — se migra a relación real `Medico_Prestacion` para permitir múltiples prestaciones por médico con precio propio.)*

### Turnos
```
ID_Turno (PK) Serial NOT NULL
ID_Medico (FK) NOT NULL
ID_Paciente (FK)
ID_Consultorio (FK)
Fecha_Hora TIMESTAMP NOT NULL
ID_Prestacion (FK)
Estado VARCHAR(20) NOT NULL
Fecha_Reserva TIMESTAMP
Visible BIT
Fecha_Creacion TIMESTAMP NOT NULL
```
Cada turno asignado o disponible: fecha/hora, médico, paciente (vacío si no reservado), prestación, consultorio, estado.

### Cobros
```
ID_Cobro (PK) Serial NOT NULL
ID_Turno (FK) NOT NULL
Metodo_Pago VARCHAR(30) NOT NULL
Importe_Total DECIMAL(10,2) NOT NULL
Importe_Cubierto_OS DECIMAL(10,2)
Importe_Copago DECIMAL(10,2)
Fecha_Cobro TIMESTAMP NOT NULL
Visible BIT
```
Cobro asociado a un turno.

### Historia_Clinica
```
ID_HistoriaClinica (PK) Serial NOT NULL
ID_Paciente (FK) NOT NULL UNIQUE
Fecha_Creacion DATETIME NOT NULL
Estado VARCHAR(20) NOT NULL
Visible BIT
```
Encabezado 1:1 con el paciente.

### Evolucion_Clinica
```
ID_Evolucion (PK) Serial NOT NULL
ID_HistoriaClinica (FK) NOT NULL
ID_Medico (FK) NOT NULL
ID_Turno (FK)
ID_Prestacion (FK)
Fecha_Hora TIMESTAMP NOT NULL
Motivo_Consulta TEXT
Diagnostico TEXT
Tratamiento TEXT
Indicaciones TEXT
Estudios_Solicitados TEXT
Observaciones TEXT
Visible BIT
```
Cada entrada de la historia clínica: qué ocurrió en una consulta.

### Adjuntos_HistoriaClinica
```
ID_Adjunto (PK) Serial NOT NULL
ID_Evolucion (FK) NOT NULL
Nombre_Archivo VARCHAR(100) NOT NULL
Ruta_Archivo VARCHAR(255) NOT NULL
Tipo_Archivo VARCHAR(30)
Fecha_Carga TIMESTAMP NOT NULL
Visible BIT
```
Archivos adjuntos a una evolución (estudios, imágenes, laboratorio).

### Facturacion
```
ID_Facturacion (PK) Serial NOT NULL
ID_Turno (FK) NOT NULL
ID_Paciente (FK) NOT NULL
ID_Medico (FK) NOT NULL
ID_Arrendamiento (FK)
Fecha_Facturacion TIMESTAMP NOT NULL
Tipo_Consulta VARCHAR(50) NOT NULL
Metodo_Pago VARCHAR(30) NOT NULL
Obra_Social VARCHAR(100)
Importe_Total DECIMAL(10,2) NOT NULL
Importe_Copago DECIMAL(10,2)
Porcentaje_Consultorio DECIMAL(5,2) NOT NULL
Porcentaje_Medico DECIMAL(5,2) NOT NULL
Estado_Pago VARCHAR(20) NOT NULL
Observaciones TEXT
Visible BIT
```
Documento de facturación por consulta; referencia el contrato de arrendamiento vigente para auditar el split ante renegociaciones futuras.

### Liquidacion_Medica
```
ID_Liquidacion (PK) Serial NOT NULL
ID_Medico (FK) NOT NULL
Fecha_Desde DATE NOT NULL
Fecha_Hasta DATE NOT NULL
Total_Facturado DECIMAL(10,2) NOT NULL
Total_Consultorio DECIMAL(10,2) NOT NULL
Total_Medico DECIMAL(10,2) NOT NULL
Fecha_Generacion TIMESTAMP NOT NULL
Estado VARCHAR(20) NOT NULL
Observaciones TEXT
Visible BIT
```
Resumen de lo adeudado a un médico en un período.

### Pago_Facturacion
```
ID_Pago (PK) Serial NOT NULL
ID_Facturacion (FK) NOT NULL
Metodo_Pago VARCHAR(30) NOT NULL
Importe DECIMAL(10,2) NOT NULL
Fecha_Pago TIMESTAMP NOT NULL
Estado VARCHAR(20) NOT NULL
Numero_Comprobante VARCHAR(100)
Observaciones TEXT
Visible BIT
```
Pagos efectivos realizados contra una factura.

### Reporte_Generado
```
ID_Reporte (PK) Serial NOT NULL
ID_Usuario (FK) NOT NULL
Tipo_Reporte VARCHAR(50) NOT NULL
Fecha_Generacion TIMESTAMP NOT NULL
Parametros_Utilizados TEXT
Formato_Exportacion VARCHAR(20)
Ruta_Archivo VARCHAR(255)
Visible BIT
```
Historial de reportes generados por usuario.

### Dashboard_Gerencial
```
ID_Dashboard INT IDENTITY(1,1) (PK)
Fecha DATE NOT NULL
Total_Turnos_Dia INT
Turnos_Atendidos INT
Turnos_Cancelados INT
Turnos_No_Asistio INT
Turnos_Disponibles INT
Facturacion_Total_Dia DECIMAL(12,2)
Cobros_Pendientes DECIMAL(12,2)
Nuevos_Pacientes INT
Fecha_Actualizacion DATETIME NOT NULL
Visible BIT NOT NULL DEFAULT 1
```
Indicadores precalculados para el panel de gestión, sin recalcular en tiempo real.

### Consultorio
```
ID_Consultorio (PK) Serial NOT NULL
Numero_Consultorio VARCHAR(20) NOT NULL UNIQUE
Descripcion TEXT
Estado VARCHAR(20) NOT NULL
Equipamiento TEXT
Ubicacion VARCHAR(100)
Visible BOOLEAN
```
Datos físicos de cada consultorio.

### Arrendamiento_Modulo
```
ID_Arrendamiento (PK) Serial NOT NULL
ID_Medico (FK) NOT NULL
ID_Consultorio (FK) NOT NULL
Fecha_Inicio DATE NOT NULL
Fecha_Fin DATE
Hora_Inicio TIME NOT NULL
Hora_Fin TIME NOT NULL
Porcentaje_Consultorio DECIMAL(5,2) NOT NULL
Porcentaje_Medico DECIMAL(5,2) NOT NULL
Estado VARCHAR(20) NOT NULL
Observaciones TEXT
Visible BIT
```
Contrato de uso de un consultorio: fechas, horario, % de facturación acordado.
*(Ver Anexo — se agregó columna `Dia_Semana`, no contemplada en el diseño original, y el contrato ahora dispara la generación automática de turnos.)*

### Uso_Consultorio
```
ID_Uso (PK) Serial NOT NULL
ID_Consultorio (FK) NOT NULL
ID_Medico (FK) NOT NULL
Fecha DATE NOT NULL
Hora_Inicio TIME NOT NULL
Hora_Fin TIME NOT NULL
Cantidad_Pacientes INTEGER
Facturacion_Generada DECIMAL(10,2)
Visible BOOLEAN
```
Registro operativo real del uso de un consultorio en un día/horario.

### Cierre_Diario
```
ID_Cierre (PK) Serial NOT NULL
ID_Medico (FK) NOT NULL
ID_Consultorio (FK) NOT NULL
ID_Arrendamiento (FK)
Fecha DATE NOT NULL
Total_Facturado_Dia DECIMAL(10,2) NOT NULL
Importe_Consultorio DECIMAL(10,2) NOT NULL
Importe_Medico DECIMAL(10,2) NOT NULL
Cantidad_Turnos INT NOT NULL
Estado VARCHAR(20) NOT NULL
Observaciones TEXT
Fecha_Registro DATETIME NOT NULL
Visible BIT
```
Cierre económico de cada jornada por médico y consultorio, según el contrato de arrendamiento vigente.

---

## 11. Anexo — Desvíos y decisiones confirmadas durante el desarrollo

Estas decisiones ya fueron tomadas y confirmadas durante las sesiones de desarrollo. **No deben reportarse como "faltantes" o "bugs"** en ningún audit posterior — son cambios de alcance deliberados respecto al texto original de esta propuesta.

| # | Ítem original | Decisión confirmada | Motivo |
|---|---|---|---|
| 1 | Rol Paciente con login de solo lectura | **Eliminado por completo.** Paciente es solo un registro de datos, sin usuario ni contraseña. FK `Pacientes.ID_Usuario` eliminada. | Decisión de producto del cliente |
| 2 | Módulo "Agenda_Medico" independiente + botón manual "Generar Turnos" | **Eliminado.** Los turnos se generan automáticamente al crear un Contrato de Arrendamiento (médico + consultorio + días + horarios). | Unificar dos conceptos que se superponían |
| 3 | Acceso a Reportes: no restringido explícitamente para Administrativo | **Restringido a solo GERENTE** (los 4 endpoints exigen `hasRole('GERENTE')`) | Decisión de producto |
| 4 | Alta de Contratos de Arrendamiento: Gerente y Administrativo | **Restringido a solo GERENTE** | Decisión de producto |
| 5 | RN-013: valida consultorio + horario | **Valida también por día de semana** (columna `Dia_Semana` agregada a `Arrendamiento_Modulo`, no contemplada en el modelo original) | El modelo original no distinguía días, generaba falsos positivos de "ocupado" |
| 6 | `Prestaciones_Medicas`: un médico, una prestación (FK suelta en `Medicos`) | **Corregido a relación real** vía tabla `Medico_Prestacion`, con `Importe_Particular`, `Duracion_Estimada_Min` y `Tipo` específicos por médico | Error de modelado del documento original — la propuesta pedía "múltiples prestaciones por médico" pero el schema solo permitía una |
| 7 | Tipografía: Inter | **Source Serif 4 (marca/títulos) + IBM Plex Sans (UI) + IBM Plex Mono (datos)** | Decisión de diseño — se mantiene la paleta de colores exacta del documento original |
| 8 | Alta de médicos: solo Gerente (tabla de permisos, sección 4.3) | **Confirmado y reforzado** — se detectó que el endpoint backend permitía también a Administrativo por error de implementación; se corrigió para que coincida con la propuesta | Bug de implementación, no cambio de alcance |
| 9 | RN-005 al RN-015 (numeración original) | Se incorporó una regla adicional durante el desarrollo (bloquear liquidación con turnos atendidos sin cobro asociado), etiquetada **RN-016** para no romper la numeración original | Necesidad detectada durante el desarrollo, no prevista en el documento original |
| 10 | Arquitectura no explicitada en el documento original | Se agrega el **Principio de Modularidad** (sección 3): el sistema se programa por módulo, con el rol como filtro de permisos, no como eje de implementación | Devolución de la cátedra tras la presentación del proyecto |

### Bugs de implementación corregidos (no son desvíos de alcance)

- RN-010 comparaba el ID de Usuario del JWT contra el ID de Médico de la evolución clínica (espacios de IDs distintos) — la autorización nunca funcionaba correctamente.
- RN-011 no persistía el motivo de anulación antes de dar de baja la evolución clínica.
- `docs/schema.sql` es la fuente de verdad real del proyecto; `src/main/resources/db/schema.sql` quedó desactualizado y no se ejecuta (`ddl-auto: validate`) — deuda técnica pendiente de resolver (dos schemas, uno inconsistente).

### Pendiente de auditar / no confirmado como implementado

- Módulo de gestión de Usuarios dedicado (alta/baja/modificación desde pantalla propia) — hoy los usuarios de médicos se crean indirectamente al crear el médico.
- Recuperación de contraseña vía email con enlace temporal de 20 minutos (sección 5).
- Registro de "quién accedió" a una historia clínica (solo se trackea autoría de escritura, no lectura) — sección 4.5 / seguridad.
- Exportación de reportes a Excel / impresión / visualización gráfica (sección 4.7).
- Cobertura completa de tests: no se confirmó una corrida final de `mvn clean test` sobre el estado más reciente del proyecto.
- **Auditoría de modularidad real** (nuevo, a raíz de la sección 3): confirmar que ningún módulo del código actual tiene lógica duplicada por rol — ver prompt de Claude Code sugerido más abajo en la conversación.
