# Prompt — Sprint 2: Refactors de modularidad (MediSpace)

Continúo trabajando sobre MediSpace (Spring Boot + SQL Server, frontend
HTML/CSS/JS vanilla). Roles: GERENTE, ADMINISTRATIVO, MEDICO (PACIENTE sin
login). Adjunto `docs/AUDITORIA_MEDISPACE.md` (auditoría completa) y
`docs/PROPUESTA_TECNICA.md` (spec + Anexo de desvíos confirmados).

Ya se completó el **Sprint 1 — Bloqueantes** (los 7 primeros ítems de la
sección 6 de la auditoría: IDOR en Arrendamiento/Liquidaciones/Turnos,
RN-010 en `anularEvolucion`, enmascarado de Historias Clínicas por rol,
condición de carrera en `reservarTurno` con lock pesimista, pérdida de
datos monetarios en Facturación, bug de string en RN-012, y persistencia
de datos de reserva de turno) — todos con tests, `mvn clean test` corrido
al final. **No hace falta volver a auditarlos.**

**Nota importante:** el ítem 8 de la sección 6 ("Historias Clínicas —
centralizar la autorización de lectura/enmascarado en un único punto del
servicio; extraer la validación de autoría de `editarEvolucion` a un
método reutilizado por `anularEvolucion`") **ya quedó resuelto como efecto
colateral del Sprint 1**: `HistoriaClinicaServiceImpl.validarAutoriaMedico()`
(líneas 136-142) ya es un método privado único usado por `editarEvolucion`
(línea 93) y `anularEvolucion` (línea 113), y `mapEvolucionToDTO()` (líneas
144-182) ya centraliza el enmascarado contra `esPaciente`/`esAdministrativo`
en un único lugar. Verificar que sigue así al arrancar, pero no es trabajo
pendiente — saltar directo al ítem 9.

Ahora necesito el **Sprint 2 — Refactors de modularidad**: los ítems 9 a 14
de la sección "Plan de acción priorizado" de la auditoría. Encarar en este
orden (S antes que L). Nota: Facturación (ítem 4) y Arrendamiento (ítem 6)
no tienen una dependencia de secuencia real entre sí — Facturación lee
`ArrendamientoModuloRepository` directamente, sin pasar por
`ArrendamientoServiceImpl` — pero Facturación sí debe resolverse antes que
Arrendamiento porque en el ítem 4 se extrae el `SplitFinancieroCalculator`
que el ítem 6 reutiliza (ver detalle ahí).

---

## 1. Turnos — código muerto de acciones (Esfuerzo: S)

**Archivo:** `src/main/resources/static/js/modules/turnos.js`

Hay dos funciones que construyen los botones de acción de un turno:
- `buildTurnoAccionesCompacto(t)` (líneas 202-219) — la que **realmente se
  usa** (línea 180: `${buildTurnoAccionesCompacto(t)}`). Cubre Reservar,
  pasar a En Espera, marcar Atendido. Le faltan **Cancelar** y **No
  Asistió**.
- `buildTurnoAcciones(t)` (líneas 221-235) — **sin ningún call site**
  (código muerto). Sí tiene Cancelar (botón sobre estado RESERVADO,
  `cambiarEstado(id,'CANCELADO')`) y No Asistió (botón sobre estado
  EN_ESPERA, `cambiarEstado(id,'NO_ASISTIO')`) que `Compacto` no tiene.

**Hacer:**
1. Eliminar `buildTurnoAcciones()` por completo (líneas 221-235).
2. Portar las acciones que le faltan a `buildTurnoAccionesCompacto()`:
   agregar un botón-ícono "Cancelar" en el bloque `RESERVADO` y uno "No
   Asistió" en el bloque `EN_ESPERA`, manteniendo el estilo ícono-compacto
   (`btn-icon`, mismo patrón SVG que ya usa el resto de la función) en vez
   del estilo botón-texto que tenía la función muerta.
3. Verificar manualmente en el navegador (`/run` o levantar el server) que
   desde "Mi Agenda"/Turnos se puede cancelar un turno reservado y marcar
   "no asistió" uno en espera, y que el resto de las transiciones sigue
   funcionando.

No requiere tests backend (es solo frontend). No hay lógica de negocio
nueva, así que no hace falta test unitario nuevo.

---

## 2. Médicos — lógica de negocio fuera de módulo (Esfuerzo: S)

**Archivos:** `src/main/resources/static/js/app.js` →
`src/main/resources/static/js/modules/medicos.js`

`app.js` tiene dos funciones que son 100% del módulo Médicos y no deberían
vivir ahí (la sección 3.3 de la propuesta reserva `app.js` solo para
config de sidebar por rol):
- `loadMisPrestaciones()` (`app.js:170-195`) — fetch + render de tabla de
  prestaciones del médico autenticado (`Api.getMisPrestaciones()`).
- `loadMisDatos()` (`app.js:198-228`) — fetch + render de card de datos
  personales del médico autenticado (`Api.getMisDatosMedico()`), con un
  branch por `rol` que hoy solo maneja `MEDICO` (el resto ve un
  empty-state).

El router en `app.js:164-165` las invoca así:
```js
case 'mis-datos':      loadMisDatos(); break;
case 'mis-prestaciones': loadMisPrestaciones(); break;
```

**Hacer:**
1. Mover el cuerpo completo de ambas funciones a `medicos.js` (revisar el
   archivo para mantener las convenciones de nombres/estilo que ya usa
   ese módulo, p. ej. `loadMedicos()`, `guardarMedico()`, etc.).
2. Dejar en `app.js` únicamente la invocación desde el router (las líneas
   164-165 quedan igual, solo que ahora llaman a funciones definidas en
   `medicos.js`, que ya se carga como `<script>` en `app.html` junto con
   el resto de los módulos — confirmar el orden de carga de scripts en
   `app.html` para que `medicos.js` esté disponible antes de que el router
   de `app.js` pueda necesitarlo).
3. No cambiar comportamiento: mismo fetch, mismo render, mismo manejo de
   error.
4. Probar en el navegador: como MEDICO, entrar a "Mis Datos" y "Mis
   Prestaciones" y confirmar que renderizan igual que antes.

---

## 3. Facturación/Liquidación — numeración RN-005/RN-006 invertida (Esfuerzo: S)

**Confirmado que sigue sin corregir** tras Sprint 1. La regla del split
70/30 y la regla de "no liquidar con facturas pendientes" tienen la
etiqueta cruzada entre código/tests y el documento fuente:

| Archivo | Línea | Dice en código | Debería decir (doc fuente) |
|---|---|---|---|
| `FacturacionServiceImpl.java` | 38 | `// RN-005: Split 70% médico / 30% consultorio...` | `RN-006` |
| `FacturacionServiceTest.java` | 52 | `testRN005_SplitFinanciero7030Configurado()` | `testRN006_...` |
| `LiquidacionServiceImpl.java` | 43 | `// RN-006: No se liquida si hay turnos atendidos / facturas sin cobro...` | `RN-005` |
| `LiquidacionServiceTest.java` | 54 | `testRN006_NoLiquidaSiHayFacturasPendientes()` | `testRN005_...` |
| `LiquidacionServiceImpl.java` | 71 | `// RN-005: Split 70% medico / 30% consultorio` | `RN-006` |

**Hacer:**
1. En `FacturacionServiceImpl.java:38` y `LiquidacionServiceImpl.java:71`,
   cambiar el comentario de `RN-005` a `RN-006` (regla del split).
2. En `LiquidacionServiceImpl.java:43-48`, cambiar `RN-006` a `RN-005` en
   el comentario **y** en el mensaje de la excepción
   (`BusinessRuleException("RN-006: No se puede liquidar...`).
3. Renombrar `testRN005_SplitFinanciero7030Configurado` →
   `testRN006_SplitFinanciero7030Configurado` en `FacturacionServiceTest.java`.
4. Renombrar `testRN006_NoLiquidaSiHayFacturasPendientes` →
   `testRN005_NoLiquidaSiHayFacturasPendientes` en `LiquidacionServiceTest.java`,
   y actualizar el `assertTrue(ex.getMessage().contains("RN-006"))` (línea
   69) a `"RN-005"`.
5. Confirmar contra `docs/PROPUESTA_TECNICA.md` que RN-005/RN-006 son
   efectivamente esas dos reglas en ese orden antes de tocar nada (por si
   el documento fuente tiene una numeración distinta a la que asume la
   auditoría) — si coincide, aplicar el swap tal cual arriba.

Es un rename puro, no debería cambiar comportamiento ni requerir tests
nuevos — correr la suite después para confirmar que nada quedó roto por
referencias cruzadas al nombre del método de test.

---

## 4. Facturación — Pago_Facturacion huérfano + split hardcodeado + idArrendamiento sin poblar (Esfuerzo: L)

**Decisión ya tomada:** eliminar `Pago_Facturacion` formalmente (no
retomarlo). Verificado que **no tiene ningún caller** fuera de sus propios
archivos (`grep -r PagoFacturacion src/main` solo encuentra
`PagoFacturacion.java` y `PagoFacturacionRepository.java` a sí mismos) —
es seguro borrarlo sin tocar ningún otro service/controller/test.

### 4a. Eliminar Pago_Facturacion

**Hacer:**
1. Borrar `src/main/java/com/medispace/app/model/PagoFacturacion.java`.
2. Borrar `src/main/java/com/medispace/app/repository/PagoFacturacionRepository.java`.
3. Dejar la tabla `Pago_Facturacion` tal cual en `docs/schema.sql` (es la
   fuente de verdad del schema real de la base, no se toca desde acá; solo
   se retira el mapeo JPA que no se usaba). No hace falta migración
   porque `ddl-auto: validate` no exige que toda tabla del schema tenga
   entidad — solo al revés.
4. Actualizar `docs/AUDITORIA_MEDISPACE.md` sección 5 (tabla de modelo de
   datos, fila `Pago_Facturacion`) y sección 2.2 (Facturación/Liquidaciones)
   para reflejar la decisión: "eliminado del modelo JPA en Sprint 2 — el
   flujo de pagos parciales/comprobante no se implementa, `Cobros` cubre
   el caso real 1:1 usado en producción."

### 4b. Split 70/30 hardcodeado

**Problema:** `FacturacionServiceImpl.crearFacturacionAutomatica()`
(líneas 38-40) hardcodea el split en vez de leerlo del contrato de
arrendamiento vigente del médico:
```java
// RN-006 (tras el fix del ítem 3): Split 70% médico / 30% consultorio configurado a nivel médico
BigDecimal porcentajeMedico = new BigDecimal("70.00");
BigDecimal porcentajeConsultorio = new BigDecimal("30.00");
```
El dato real vive en `ArrendamientoModulo.porcentajeMedico` /
`.porcentajeConsultorio` (ver cómo se completa en
`ArrendamientoServiceImpl.crearArrendamiento()` líneas 79-80, con default
70/30 si no se especifica al crear el contrato).

`ReporteServiceImpl.reporteUsoConsultorios()` (líneas 166-167) también
hardcodea 30/70 para calcular `importeConsultorio`/`importeMedico` del
reporte de uso de consultorios — mismo problema, mismo origen de verdad
debería usarse ahí si se toca este archivo en el ítem 5 (Reportes).

**Criterio de matching del contrato vigente** (aplica también a 4c y al
ítem 6): no alcanza con "el médico tiene un contrato ACTIVO" — un médico
puede tener varios contratos activos simultáneos en distintos
consultorios y con distintos horarios (confirmado en
`docs/PROPUESTA_TECNICA.md`, relevamiento original: un mismo consultorio
puede compartirse entre médicos en turnos distintos del día). El criterio
correcto es: `ArrendamientoModulo.idMedico == turno.idMedico` **Y**
`ArrendamientoModulo.idConsultorio == turno.idConsultorio` **Y** el día de
semana/horario de `turno.fechaHora` cae dentro del rango del contrato
(`Dia_Semana`, `Hora_Inicio`, `Hora_Fin`). Es el mismo criterio que ya usa
el sistema para generar los turnos automáticamente al crear un contrato
(`ArrendamientoServiceImpl.crearArrendamiento()` → `TurnoService
.generarTurnosParaContrato()`) — revisar esa lógica de generación en
`TurnoServiceImpl` y reusar el mismo matching en sentido inverso (dado un
turno, encontrar su contrato), no reinventarlo. Si dos contratos
matchearan igual (no debería pasar si RN-013 se cumple), desempatar por
fecha de inicio más reciente y documentar el criterio en un comentario.

**Hacer:**
1. Inyectar `ArrendamientoModuloRepository` en `FacturacionServiceImpl`.
2. En `crearFacturacionAutomatica(Turno turno)`, antes de armar el
   `Facturacion.builder()`, buscar el contrato de arrendamiento vigente
   del médico del turno con el criterio de matching de arriba (médico +
   consultorio + día/horario del turno). Si no se encuentra contrato
   vigente, mantener el fallback 70/30 actual (no romper turnos
   facturados sin arrendamiento asociado, p. ej. datos de test/seed).
3. Extraer el cálculo del split a una clase utilitaria nueva,
   `SplitFinancieroCalculator` (paquete `com.medispace.app.util` o
   similar), con un método del tipo
   `calcular(BigDecimal importeTotal, BigDecimal porcentajeMedico,
   BigDecimal porcentajeConsultorio)` que devuelva ambas partes con
   `RoundingMode.HALF_UP` — es el mismo cálculo que hoy vive inline en
   `FacturacionServiceImpl` (líneas 38-40 antes del fix),
   `LiquidacionServiceImpl.generarLiquidacion()` (líneas 72-73) y que el
   ítem 6 va a necesitar para `CierreDiarioServiceImpl`. Usarlo acá y
   migrar también `LiquidacionServiceImpl` a este helper en este mismo
   ítem (aunque su cálculo ya era correcto, para que los tres consumidores
   no puedan volver a divergir entre sí). El tercer consumidor
   (`CierreDiarioServiceImpl`) se conecta en el ítem 6, cuando ese service
   se crea.
4. Usar `arrendamiento.getPorcentajeMedico()` /
   `.getPorcentajeConsultorio()` (vía el calculator) en vez de las
   constantes hardcodeadas.
5. Completar `Facturacion.idArrendamiento` (campo ya existe en la entidad,
   `Facturacion.java:39-40`, nunca seteado) con el ID del contrato
   encontrado.
6. Tests:
   - `SplitFinancieroCalculatorTest.java` nuevo: casos simples de split
     (70/30, 60/40, redondeo HALF_UP en un importe con centavos).
   - En `FacturacionServiceTest.java`: mockear un
     `ArrendamientoModuloRepository` con un contrato que tenga un split
     distinto al default (p. ej. 60/40) y verificar que
     `crearFacturacionAutomatica` usa ese split y no 70/30, y que
     `idArrendamiento` queda seteado. Mantener también un test del caso
     sin contrato vigente (fallback a 70/30, `idArrendamiento` null).
   - En `LiquidacionServiceTest.java`: no debería hacer falta un test
     nuevo si el resultado numérico no cambia al migrar al calculator
     (los tests existentes deben seguir pasando tal cual — son la
     verificación de que la migración no alteró el resultado).

### 4c. CierreDiario.idArrendamiento tampoco se puebla

Mismo patrón: `CierreDiario.idArrendamiento` (`CierreDiario.java:36-38`)
existe pero `ArrendamientoServiceImpl.generarCierreDiario()` (líneas
162-205) nunca lo setea en el `CierreDiario.builder()`. Reusar el mismo
criterio de matching (médico + consultorio + día/horario) definido en 4b
para completar el campo. Si el ítem 6 (separar `CierreDiarioService`) se
hace en la misma sesión, resolver esto directamente en el service nuevo
en vez de en `ArrendamientoServiceImpl`.

---

## 5. Reportes — findAll() + filtrado en memoria (Esfuerzo: L)

**Archivo:** `src/main/java/com/medispace/app/service/impl/ReporteServiceImpl.java`

Es el god node de mayor acoplamiento del sistema (confirmado por
`graphify-out/GRAPH_REPORT.md`: 3406-3436 de betweenness centrality, el
más alto de los tres). Inyecta 5 repositorios (líneas 26-30) y **todos**
sus métodos públicos hacen `findAll()` + filtrado/agrupado en memoria con
streams en vez de queries agregadas:

- `recalcularDashboard()` (líneas 42-84): `facturacionRepository.findAll()`
  línea 54, `pacienteRepository.findAll()` línea 65 — filtra por rango de
  fecha en memoria. `turnoRepository.findByFechaHoraBetween()` (línea 47)
  ya es una query filtrada, ese no hace falta tocarlo.
- `reporteFacturacionPorMedico()` (líneas 87-118): `findAll()` línea 91 +
  `groupingBy` en memoria por médico.
- `reporteFacturacionPorObraSocial()` (líneas 121-144): `findAll()` línea
  125 + `groupingBy` en memoria por obra social.
- `reporteUsoConsultorios()` (líneas 147-170): `usoConsultorioRepository
  .findAll()` línea 148 + `groupingBy` en memoria por consultorio+médico.
  (Además hardcodea 30/70 en líneas 166-167 — ver nota del ítem 4b, decidir
  si se conecta a `ArrendamientoModulo` acá también o se documenta que
  queda pendiente.)

**Hacer:**
1. `FacturacionRepository`: agregar métodos con `@Query` (JPQL) que
   devuelvan agregados por rango de fecha:
   - Uno para el dashboard: `findByFechaFacturacionBetween(desde, hasta)`
     (reemplaza el `findAll()+filter` de `recalcularDashboard`, ya sin
     agregación porque el cálculo de totales/pendientes sigue en Java
     sobre una lista más chica — mejora real aunque no sea 100% SQL).
   - Uno agregado por médico: `SUM(f.importeTotal) GROUP BY f.medico` con
     filtro de fecha y, si permite expresar el `CASE WHEN estadoPago =
     'PAGADO'` en JPQL, mejor; si no, traer los agregados base por
     `GROUP BY` y hacer el split PAGADO/PENDIENTE con un segundo `@Query`
     o aceptar una lista ya acotada por fecha en vez de la tabla completa.
   - Ídem agrupado por obra social.
2. `PacienteRepository`: `@Query` o `countByFechaCreacionBetween` para
   reemplazar el `findAll()` de nuevos pacientes del día.
3. `UsoConsultorioRepository`: query agregada `GROUP BY consultorio,
   médico` con filtro de fecha para `reporteUsoConsultorios`.
4. Reescribir los 4 métodos de `ReporteServiceImpl` para consumir estas
   queries en vez de `findAll()+stream`. Mantener las mismas firmas
   públicas (`ReporteService` no cambia) y los mismos DTOs de salida.
5. Tests: los reportes hoy no tienen test unitario dedicado (no aparece
   `ReporteServiceTest` en la auditoría) — agregar uno nuevo,
   `ReporteServiceTest.java`, con mocks de los repositorios nuevos,
   cubriendo al menos: `reporteFacturacionPorMedico` con 2 médicos
   distintos, `reporteFacturacionPorObraSocial` agrupando por obra
   social, y `recalcularDashboard` con conteo de estados de turno.
6. Correr `mvn clean test` y confirmar que ningún test de integración
   existente que dependa de `ReporteServiceImpl` (revisar si hay alguno en
   `ReporteControllerTest` o similar) se rompe por el cambio de queries.

---

## 6. Arrendamiento — god node con 4 responsabilidades (Esfuerzo: L)

**Archivo:** `src/main/java/com/medispace/app/service/impl/ArrendamientoServiceImpl.java`
(244 líneas, 7 dependencias inyectadas: `ArrendamientoModuloRepository`,
`UsoConsultorioRepository`, `CierreDiarioRepository`, `MedicoRepository`,
`ConsultorioRepository`, `FacturacionRepository`, `TurnoService`).

Responsabilidades actuales mezcladas en una sola clase:
1. **Contratos** — `crearArrendamiento()` (38-104), `obtenerArrendamiento()`
   (106-111), `listarPorMedico()` (113-118), `listarContratos()` (120-128),
   `darDeBajaArrendamiento()` (130-137), `mapArrendamientoToDTO()`
   (207-226).
2. **Uso operativo** — `registrarUso()` (139-160).
3. **Cierre económico diario** — `generarCierreDiario()` (162-205),
   `mapCierreToDTO()` (228-243). Reimplementa un cálculo de liquidación
   (split médico/consultorio sobre facturación del día, líneas 178-188)
   que **ya existe y diverge** en `LiquidacionServiceImpl.generarLiquidacion()`
   (líneas 64-77 — misma fórmula, pero sobre un rango de fechas elegido
   por el usuario en vez de "hoy", y sin relación entre ambos: un
   `CierreDiario` no genera ni se vincula a ninguna `LiquidacionMedica`).
4. **Disparo de generación de turnos** — delega a `TurnoService
   .generarTurnosParaContrato()` desde dentro de `crearArrendamiento()`
   (líneas 94-99).

**Hacer:**
1. Crear `UsoConsultorioService`/`UsoConsultorioServiceImpl` con
   `registrarUso(UsoConsultorioDTO)`, moviendo el método tal cual
   (líneas 139-160) — solo depende de `ConsultorioRepository`,
   `MedicoRepository`, `UsoConsultorioRepository`.
2. Crear `CierreDiarioService`/`CierreDiarioServiceImpl` con
   `generarCierreDiario(CierreDiarioRequestDTO)`, moviendo
   `generarCierreDiario()` + `mapCierreToDTO()` (líneas 162-205, 228-243)
   — depende de `MedicoRepository`, `ConsultorioRepository`,
   `FacturacionRepository`, `CierreDiarioRepository`, y (nuevo)
   `ArrendamientoModuloRepository` para resolver `idArrendamiento` (ver
   ítem 4c).
3. `ArrendamientoServiceImpl` queda acotado a gestión de contratos:
   `crearArrendamiento`, `obtenerArrendamiento`, `listarPorMedico`,
   `listarContratos`, `darDeBajaArrendamiento`, `mapArrendamientoToDTO` —
   dependencias se reducen a `ArrendamientoModuloRepository`,
   `MedicoRepository`, `ConsultorioRepository`, `TurnoService`.
4. Actualizar `ArrendamientoController` para inyectar los tres services
   nuevos según qué endpoint llame a qué: `POST /uso` (líneas 58-63) pasa
   a `UsoConsultorioService`, `POST /cierre-diario` (líneas 65-70) pasa a
   `CierreDiarioService`, el resto (`POST /`, `GET /{id}`, `GET
   /medico/{idMedico}`, `GET /contratos`, `PUT /{id}/baja`) sigue en
   `ArrendamientoService`. Mantener las mismas rutas y `@PreAuthorize` —
   esto es un refactor interno, no debe cambiar contrato HTTP.

   **Ojo con el guard de IDOR del Sprint 1:** `GET /medico/{idMedico}`
   (líneas 36-42) NO se extrae — queda en `ArrendamientoController` /
   `ArrendamientoServiceImpl` tal cual. Ese endpoint ya tiene aplicado
   `medicoAccessGuard.verificarAccesoPropio(idMedico, authentication)`
   (línea 40, inyectado en línea 21) para el fix de IDOR de Sprint 1. Al
   reordenar el resto de los métodos del controller para inyectar los
   services nuevos, confirmar explícitamente que esa línea y esa inyección
   no se tocan. Ya existen los tests de regresión de este guard en
   `ArrendamientoControllerTest.java`:
   `listarPorMedico_MedicoPidiendoIdDeOtroMedicoRecibe403()` (línea 39) y
   `listarPorMedico_MedicoPidiendoSuPropioIdRecibeSusDatos()` (línea 49) —
   correrlos explícitamente al final del refactor del controller (no solo
   como parte de la suite completa) para confirmar que siguen en verde
   después de reordenar las dependencias inyectadas.
5. **Conectar `CierreDiario` con `LiquidacionMedica`** en vez de mantener
   dos cálculos económicos independientes: `CierreDiarioServiceImpl` debe
   usar el `SplitFinancieroCalculator` extraído en el ítem 4b (ya
   consumido ahí por Facturación y Liquidación) en vez de reimplementar el
   cálculo `importeTotal * porcentaje / 100` inline — no volver a crear el
   helper, solo inyectarlo/reusarlo. No es necesario que un `CierreDiario`
   genere una `LiquidacionMedica` automáticamente (son conceptos con
   cardinalidad distinta: diario vs. por período), pero sí que los tres
   (Facturación, Liquidación, Cierre Diario) calculen con el mismo código.
6. Tests: mover/duplicar los tests de `ArrendamientoServiceTest.java` que
   correspondan a `registrarUso`/`generarCierreDiario` a
   `UsoConsultorioServiceTest.java`/`CierreDiarioServiceTest.java` nuevos.
   Los tests de RN-013/RN-014 (superposición horaria, duración mínima)
   quedan en `ArrendamientoServiceTest` porque son de `crearArrendamiento`.
   No hace falta un test nuevo de equivalencia de split acá — ya está
   cubierto por `SplitFinancieroCalculatorTest` (ítem 4b), que es la única
   fuente del cálculo para los tres consumidores.

---

## Validación final

Al terminar los 6 ítems (o los que se completen en la sesión):
1. `mvn clean test` — debe pasar completo, incluyendo los tests nuevos de
   Reportes, Uso Consultorio, Cierre Diario y los renombrados de RN-005/006.
2. `mvn clean compile` como chequeo rápido si se está iterando ítem por
   ítem antes de correr toda la suite.
3. Levantar el server y probar a mano en el navegador: Turnos (cancelar/no
   asistió desde la agenda), Médicos (Mis Datos/Mis Prestaciones como
   MEDICO), Reportes (dashboard + los 3 reportes con filtro de fecha,
   confirmar que los números no cambiaron respecto a antes del refactor —
   mismo dataset, misma salida, solo cambia cómo se calcula), Arrendamiento
   (crear contrato, registrar uso, generar cierre diario). Facturación:
   atender un turno de un médico con un contrato de arrendamiento con
   split distinto al default (p. ej. 60/40) y confirmar que la factura
   generada automáticamente usa ese split y no 70/30; confirmar que
   `idArrendamiento` queda poblado; y confirmar que no quedó ningún rastro
   roto de `Pago_Facturacion` en la UI de Facturación/Liquidaciones (no
   debería haber ninguno, pero es la única forma de confirmarlo a nivel de
   producto y no solo de compilación).
4. Actualizar `docs/AUDITORIA_MEDISPACE.md` tachando/marcando como
   resueltos los ítems 8-14 de la sección 6, con una nota breve de qué se
   hizo distinto a lo propuesto si hubo algún desvío durante la
   implementación (igual que se documentó para el Sprint 1).
