# Changelog de Base de Datos — MediSpace

Registro de todo cambio de esquema aplicado **directamente contra una base de datos viva** (real o de prueba), sea vía `docs/migrations/*.sql` o vía un comando suelto ejecutado en una sesión de trabajo. `docs/schema.sql` describe el estado *deseado* del esquema; este archivo describe *qué se ejecutó, cuándo, contra qué base y quién/qué lo hizo* — son complementarios, no lo mismo.

Ver `docs/constitution.md` §2 para la regla que exige mantener este archivo actualizado.

---

## 2026-08-19 (2) — Ajustes de ObraSocial + coseguro por médico-obra social

- **Scripts:** `docs/migrations/2026-08-19_obra_social_ajustes_plan.sql`, `docs/migrations/2026-08-19_medico_obra_social_coseguro.sql`
- **Base afectada:** `MediSpace` (real, `localhost\SQLEXPRESS01`)
- **Aplicado por:** Claude (Sonnet 5), a pedido explícito del usuario en esta sesión, vía `sqlcmd`
- **Qué hace:**
  - `ObraSocial`: quita `Importe_Bono` y `Datos_Contacto` (agregados hace unos minutos en la migración anterior, revertidos por el usuario), agrega `Plan` (VARCHAR(50), citada como `[Plan]` por ser palabra reservada en SQL Server — la entidad JPA usa `@Column(name = "\`Plan\`")` para que Hibernate también la cite en cada query, no solo en el DDL).
  - `Medico_ObraSocial`: pasa de tabla puente simple (PK compuesta `ID_Medico`+`ID_ObraSocial`, sin datos propios) a relación con datos propios — mismo patrón que `Medico_Prestacion`. Se le quita la PK compuesta, se agrega `ID_Medico_ObraSocial` (IDENTITY, nueva PK), `Importe_Coseguro` (decidido por el médico, no por la obra social) y `Visible` (baja lógica), más un índice único filtrado `UX_MedicoObraSocial_Activo` sobre (`ID_Medico`, `ID_ObraSocial`) WHERE `Visible = 1`.
- **Motivo:** cada médico cobra un coseguro distinto según el arreglo particular que tenga con cada obra social — no es un valor fijo de la obra social. El campo "Plan" reemplaza a "Importe del Bono"/"Datos de Contacto" en el alcance del módulo, definido por el usuario tras la primera iteración.
- **Incidencias durante la aplicación (ambas corregidas en los scripts, ya reflejadas arriba):**
  1. `ALTER TABLE ObraSocial ADD Plan ...` falló con "sintaxis incorrecta" — `PLAN` es palabra reservada en SQL Server. Se corrigió citando la columna (`[Plan]`) tanto en el script como en la entidad JPA.
  2. `CREATE UNIQUE INDEX ... WHERE Visible = 1` en el mismo batch que el `ALTER TABLE ADD Visible` falló con "invalid column name" — SQL Server resuelve el predicado del índice filtrado en tiempo de compilación del batch completo, antes de que el `ALTER TABLE` anterior haya committeado. Se separó cada paso en su propio batch (`GO`) y se agregó `SET QUOTED_IDENTIFIER ON` (requisito de los índices filtrados) antes del `CREATE INDEX`. Ninguna de las dos fallas dejó cambios a medio aplicar — se verificó el estado real de las columnas antes de reintentar.
- **Verificación post-aplicación:** `sqlcmd` confirmó columnas finales en `ObraSocial` (`Plan` presente, `Importe_Bono`/`Datos_Contacto` ausentes) y en `Medico_ObraSocial` (`ID_Medico_ObraSocial`, `Importe_Coseguro`, `Visible`), más el índice único filtrado `UX_MedicoObraSocial_Activo` con `filter_definition = ([Visible]=(1))`. Backend recompilado y suite de tests completa (`mvn test`) sin fallos.
- **Estado:** ✅ Aplicada y verificada.

---

## 2026-08-19 (1) — Campos extendidos en ObraSocial (Módulo de Obras Sociales)

- **Script:** `docs/migrations/2026-08-19_obra_social_campos_extendidos.sql`
- **Base afectada:** `MediSpace` (real, `localhost\SQLEXPRESS01`)
- **Aplicado por:** Claude (Sonnet 5), a pedido explícito del usuario en esta sesión, vía `sqlcmd`
- **Qué hace:** agrega 5 columnas a `ObraSocial`: `Codigo_Sigla` (nullable), `Requiere_Bono` (NOT NULL DEFAULT 0), `Importe_Bono` (nullable), `Datos_Contacto` (nullable), `Observaciones` (nullable). Idempotente (`IF NOT EXISTS` por columna), no borra ni transforma datos existentes.
- **Motivo:** implementación del Módulo de Obras Sociales (spec.md §4.9 / Entrega §1.3.9) — pasa de catálogo mínimo (solo Nombre) a ABM completo con alta, baja lógica (RN-016: bloqueada si hay médicos activos asociados), modificación y consulta con filtros.
- **Verificación post-aplicación:** `sqlcmd` confirmó las 5 columnas presentes en `sys.columns`. Backend compilado (`mvn compile`) sin errores contra la entidad `ObraSocial` actualizada.
- **Estado:** ✅ Aplicada y verificada.

---

## 2026-08-12 — Aplicar migración pendiente de Turnos (columnas "planificadas")

- **Script:** `docs/migrations/2026-08-07_turnos_datos_reserva.sql`
- **Base afectada:** `MediSpace` (real, `localhost:1433`)
- **Aplicado por:** Claude (Opus 5), a pedido explícito del usuario en esta sesión, vía `sqlcmd`
- **Qué hace:** agrega 4 columnas nullable a `Turnos`: `Tipo_Consulta`, `Metodo_Pago_Planificado`, `Obra_Social_Planificada`, `Importe_Copago_Planificado`. Idempotente (`IF NOT EXISTS` por columna), no borra ni transforma datos existentes.
- **Motivo:** la migración había sido escrita el 2026-08-07 (ver comentario en el propio script) pero nunca se había ejecutado contra `MediSpace`. Sin ella, la app no arrancaba contra la base real — Hibernate fallaba en `ddl-auto: validate` con `Schema-validation: missing column [Importe_Copago_Planificado] in table [Turnos]` (mismo hallazgo ya documentado en `docs/VERIFICACION_MODULAR_POST_SPRINT2.md`, "Hallazgo de entorno #2").
- **Verificación post-aplicación:**
  - `sqlcmd` confirmó las 4 columnas presentes en `sys.columns`.
  - La app arrancó exitosamente contra `MediSpace` (antes fallaba en el paso de validación de esquema).
  - Se ejercitó en vivo el flujo que dependía de esta migración funcionando de punta a punta: reserva de turno → `PUT /api/turnos/{id}/estado` a `ATENDIDO` (antes fallaba con `HTTP 500` por un bug de tipos ya corregido aparte, ver `ArrendamientoModuloRepository.findContratoVigente`) → confirmado que se generó automáticamente el registro de `Facturacion` correspondiente con split 70/30.
- **Estado:** ✅ Aplicada y verificada.

---

## 2026-08-05 — Medico_Prestacion (relación N:M médico↔prestación)

- **Script:** `docs/migrations/2026-08-05_prestaciones_medicas_join.sql`
- **Base afectada:** `MediSpace` (real, `localhost:1433`)
- **Aplicado por:** sesión de trabajo previa (no registrada con este nivel de detalle en su momento — este changelog no existía todavía). Reconstruido retroactivamente hoy (2026-08-12) para no perder el registro.
- **Qué hace:** crea la tabla `Medico_Prestacion` (relación N:M médico↔prestación, con `Duracion_Estimada_Min`/`Importe_Particular`/`Tipo` por médico), hace backfill de los datos que existían en la columna vieja `Medicos.ID_Prestacion`, y luego elimina esa columna y su FK.
- **Motivo:** reemplazar la FK suelta original (1 prestación por médico) por una relación real N:M, según el desvío documentado como Anexo #6 en la propuesta técnica.
- **Verificación (hecha hoy, retroactiva):** `sqlcmd` confirmó que la tabla `Medico_Prestacion` existe (`create_date` = 2026-08-05 16:21:13, consistente con la fecha del script) y que `Medicos.ID_Prestacion` ya no existe — la migración corrió completa, incluyendo el paso 3 (drop de columna).
- **Estado:** ✅ Aplicada (confirmada retroactivamente).

---

## Cómo agregar una entrada nueva

Cada vez que se ejecute un cambio de esquema contra una base viva (migración en `docs/migrations/` o un `ALTER`/`CREATE`/`UPDATE` suelto), agregar una entrada arriba con: fecha, script o comando exacto, base afectada, quién lo aplicó, qué hace, por qué, cómo se verificó, y estado (✅ Aplicada / ⏳ Pendiente / ❌ Revertida).
