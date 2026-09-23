# Diff de menús de Home por rol — código (`app.js` `NAV_CONFIG`) vs. propuesta original

Fuente: `docs/Entrega primer cuatrimestre Policaro (1).md`, sección **"1.4 Interfaces"**
("Interfaz para Personal Administrativo", "Interfaz para Médicos", "Interfaz de Gerencia").

## GERENTE

| Documento | Código (`NAV_CONFIG.GERENTE`) | Estado |
|---|---|---|
| Dashboard Gerencial | Dashboard | ✅ |
| Reportes Financieros | — | Colapsado en un solo ítem "Reportes" genérico |
| Reportes Operativos | — | ídem |
| Reportes Médicos | — | ídem |
| Facturación General | Facturación | ✅ |
| Liquidaciones Médicas | Liquidaciones Médicas | ✅ |
| Gestión de Médicos | Médicos | ✅ |
| Gestión de Usuarios | Gestionar Usuarios | ✅ |
| Gestión de Arrendamientos | Arrendamiento, Contratos | ✅ (dividido en 2 ítems) |
| Gestión de Consultorios | — | **NO IMPLEMENTADO como ítem de menú** (el CRUD existe vía API, sin entrada dedicada en el nav) |
| Estadísticas Generales | — | **NO IMPLEMENTADO** |
| Configuración del Sistema | — | **NO IMPLEMENTADO** |
| Cerrar Sesión | (en el topbar, no en el nav lateral) | ✅ funcionalmente, distinta ubicación de UI |
| — | Pacientes | Agregado, no está en la lista original (razonable — Gerente tiene lectura de pacientes) |
| — | Obras Sociales | Agregado, no está en la lista original |

## ADMINISTRATIVO

| Documento | Código (`NAV_CONFIG.ADMINISTRATIVO`) | Estado |
|---|---|---|
| Gestionar Pacientes | Pacientes | ✅ |
| Gestionar Turnos | Turnos | ✅ |
| Gestionar Médicos | Médicos | ✅ |
| Gestionar Usuarios | Gestionar Usuarios | ✅ |
| Facturación | Facturación | ✅ |
| Liquidaciones Médicas | Liquidaciones Médicas | ✅ |
| Reportes Operativos | — | **Contradicción del propio documento** — ver `qa/DIFF_MENUS_HOME.md` nota abajo; reclasificado A DEFINIR |
| Gestionar Arrendamientos | Arrendamiento, Contratos | ✅ |
| Ver Disponibilidad de Consultorios | — | No hay ítem de nav dedicado (la disponibilidad se ve indirectamente en Turnos/Arrendamientos) |
| Agenda del Día | — | No hay vista dedicada separada de "Turnos" |
| Recordatorios Pendientes | — | **NO IMPLEMENTADO** — no se encontró ningún endpoint ni concepto de "recordatorio" en el backend |
| — | Obras Sociales | Agregado, no está en la lista original |

**Nota — reclasificación (pedido del usuario)**: la sección 1.3.7/1.7 del mismo documento
("El acceso a información equivalente para Administrativo se realiza desde los módulos de
Turnos... y Facturación..., **no** desde el módulo de Reportes") **contradice** la lista del menú
de arriba, que sí incluye "Reportes Operativos". El código (403 para Administrativo en
`/api/reportes/*`) coincide con la sección 1.3.7, no con la lista de menú. Se reclasifica de
"gap de permisos" a **A DEFINIR** — el documento original se contradice a sí mismo, no hay un
bug del lado del código.

## MEDICO

| Documento | Código (`NAV_CONFIG.MEDICO`) | Estado |
|---|---|---|
| Agenda Médica | Mi Agenda | ✅ (mismo concepto, distinto label) |
| Pacientes del Día | Mis Pacientes | Parcial — el código muestra TODOS sus pacientes, no solo los del día (alcance más amplio que lo pedido) |
| Historia Clínica | Historias Clínicas | ✅ |
| Evoluciones Clínicas | — | Sin ítem de nav separado — las evoluciones se gestionan dentro de Historias Clínicas, no como vista propia |
| Mis Turnos | — | Sin ítem separado — cubierto por "Mi Agenda" |
| Mis Liquidaciones | Mis Liquidaciones | ✅ |
| Mis Datos | Mis Datos | ✅ |
| Cerrar Sesión | (topbar) | ✅ funcionalmente |
| — | Mis Prestaciones | Agregado (RF-M4, el médico gestiona su propia cartilla) |
| — | Disponibilidad | Agregado |
| — | Contratos | Agregado |

## Resumen
Ningún rol tiene un menú **idéntico** al documento, pero todos cubren el núcleo funcional
esperado. Las diferencias son en su mayoría de **granularidad** (varios ítems del documento
colapsados en uno, o viceversa) y **2 funcionalidades ausentes confirmadas**: "Recordatorios
Pendientes" (Administrativo) y "Estadísticas Generales" / "Configuración del Sistema" /
"Gestión de Consultorios" como ítem de menú (Gerente) — todas NO IMPLEMENTADAS.
