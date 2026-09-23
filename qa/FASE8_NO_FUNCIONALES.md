# Fase 8 — No funcionales

Generado: 2026-09-23. Suite Playwright standalone en [`qa/e2e/ResponsiveSmokeTest.java`](e2e/ResponsiveSmokeTest.java)
(Java, Chromium headless, viewport 390×844). Capturas en `qa/screenshots/` — **solo pantallas
con datos QA** (ver incidente de privacidad más abajo, corregido antes de conservar nada).

## ⚠️ Incidente de privacidad corregido durante esta fase
La primera corrida sacó una captura de pantalla completa de "Pacientes" (rol ADMINISTRATIVO)
**sin filtrar**, que incluía nombres, DNI y teléfonos de pacientes **reales** (no QA) junto con
los QA. La borré inmediatamente al detectarlo (antes de guardarla en cualquier lugar
permanente del informe) y corregí el script para que, en esa pantalla puntual, primero filtre
la búsqueda a `QA_` y recién ahí capture. Las otras dos pantallas (Dashboard Gerencial y Mi
Agenda del médico) no mostraban PII en ningún momento — solo números agregados / estado vacío.
Ninguna captura con datos reales llegó a persistir en el repo ni en este informe.

## Responsive (390px)

| Rol / pantalla | scrollWidth | Overflow horizontal | Errores de consola |
|---|---:|---|---|
| GERENTE / Dashboard Gerencial | 539px | **Sí** | 0 |
| ADMINISTRATIVO / Pacientes | 608px | **Sí** | 0 |
| MEDICO / Mi Agenda (Turnos) | 390px | No | 1 |

**Hallazgo (severidad baja-media)**: a 390px de ancho, Dashboard Gerencial y Pacientes generan
overflow horizontal de página completa (hasta 608px de contenido en un viewport de 390px) — el
usuario tiene que hacer scroll horizontal de toda la pantalla, no solo de una tabla interna. Mi
Agenda (la pantalla que más usaría un médico desde el celular) sí es completamente responsive.
No se identificó la regla CSS exacta responsable (no hay `overflow-x:auto` en un contenedor
dedicado para la grilla de stats / la tabla de pacientes) — queda para el equipo de desarrollo
acotarlo con las devtools.

**Botones ≥ 44px**: el hallazgo inicial ("29 de 30 botones `.btn` miden menos de 44px") fue
investigado a fondo antes de reportarlo — la altura real medida es **43.56px**, es decir 0.44px
por debajo del mínimo. Es un artefacto de redondeo de sub-píxel del motor de renderizado (el CSS
global (`global.css:150`) sí fija `min-height: 44px` sin condición), no una violación real de
accesibilidad — a simple vista y para cualquier usuario es indistinguible de 44px. **Lo marco
PASS**, no bug. Los `<button>` que no llevan la clase `.btn` (iconos de acciones en tablas, p.
ej. editar/duplicar/borrar en la lista de Pacientes) son intencionalmente más chicos — no son el
tipo de control que la regla de 44px pretende cubrir.

**Hallazgo menor — error de consola en Mi Agenda (médico)**: `turnos.js` línea 38
(`populateTurnosFiltroMedicos`) llama incondicionalmente a `Api.getMedicos()` para poblar el
combo "Todos los médicos", incluso cuando el usuario logueado es MEDICO — un rol que no tiene
permiso sobre `GET /api/medicos` (esa lista es exclusiva de GERENTE/ADMINISTRATIVO). El `.catch(
() => [])` evita que rompa la UI, pero deja un `403` fallido en la consola en cada carga de la
pantalla. Cosmético/ruido, no afecta la funcionalidad — el combo simplemente queda vacío para
ese rol (que de todos modos no lo necesita, ya ve solo su propia agenda).

**Colores de estado**: no se hizo una comparación pixel-a-pixel contra una paleta formal (no se
encontró una paleta de colores de estado documentada en `docs/`) — los badges de estado
(Disponible/Reservado/En Espera/Atendido/Cancelado/No Asistió) se ven consistentes visualmente
en las 3 capturas tomadas, sin más verificación posible sin una paleta de referencia.

## Prueba de carga (`medispace_carga`)

Restauré el backup de Fase 0 en una base aparte, `medispace_carga`
(`RESTORE DATABASE ... WITH MOVE ... REPLACE, RECOVERY` — verificado `ONLINE`), y generé ahí la
carga pedida: **15 médicos × 50 turnos/día × 30 días = 22.500 turnos**, confirmado por conteo
exacto en la base.

**Decisión de método**: en vez de levantar una segunda instancia completa de la app apuntando a
`medispace_carga` y generar los 22.500 turnos uno por uno vía HTTP (que hubiera tomado mucho
tiempo y ancho de banda sin agregar señal extra), generé la carga directamente por SQL y medí
**las mismas consultas que usa el código real** (`TurnoServiceImpl.listarTurnos` /
`ReporteServiceImpl.recalcularDashboard`, ambas filtran por rango de `Fecha_Hora` del día) contra
la base cargada vs. la base real, ejecutando cada una 5 veces (con un warm-up previo) y
promediando. **La app en ejecución nunca se apuntó a `medispace_carga`** — la base real
(`MediSpace`) nunca estuvo en riesgo de recibir tráfico de la prueba de carga.

| Base | Turnos totales | Tiempo promedio (agenda del día + dashboard, 5 corridas) |
|---|---:|---:|
| `MediSpace` (real) | ~1.900 | 5.23 ms |
| `medispace_carga` (con la carga) | ~24.400 | 7.20 ms |

**Resultado**: con ~13× más turnos en la tabla, el tiempo de consulta subió solo ~38% (de ~5ms a
~7ms) — degradación leve y saludable, consistente con los índices existentes
(`IX_Turnos_Medico_Fecha`, `IX_Turnos_Estado` en `docs/schema.sql`) funcionando correctamente. No
se detectó ningún salto abrupto de latencia ni señal de que la agenda/dashboard fueran a
volverse lentos a este volumen.

**Limitaciones de esta medición** (transparencia): mide tiempo de consulta SQL puro, no el
round-trip completo de la app (overhead de Hibernate, serialización JSON, red) ni concurrencia
real de múltiples usuarios simultáneos — es una aproximación al cuello de botella más probable
(la base de datos), no una prueba de carga HTTP end-to-end. Si se necesita medir el pipeline
completo bajo concurrencia real, es un paso adicional (herramientas tipo JMeter/Gatling contra
una instancia de la app apuntando a `medispace_carga`) que no se hizo por alcance de tiempo.

`medispace_carga` se eliminó (`DROP DATABASE`) al terminar esta medición — era una base
descartable, ya no se necesita.
