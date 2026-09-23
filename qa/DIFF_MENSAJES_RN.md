# Diff textual de mensajes de error — RN-001 a RN-018 vs. propuesta original

Fuente: `docs/Entrega primer cuatrimestre Policaro (1).md`, sección **"1.7 Reglas del negocio"**
(el documento fue extraído de un PDF con las celdas de la tabla desordenadas — se reconstruyó
el orden real ID↔Módulo↔Regla↔Mensaje por contenido semántico, cruzando cada regla con su RN ya
conocida en `docs/spec.md`).

**Hallazgo de documentación**: el documento original define reglas **hasta RN-018**, no hasta
RN-015 como afirma `docs/spec.md` línea 148 ("RN-016 a RN-021... sin número oficial en la
Entrega original"). RN-016 (Obras Sociales, baja con médicos activos), RN-017 (médico solo
edita su propio coseguro) y RN-018 (no liquidar atendidos sin cobro) **sí** están numeradas y
definidas en el documento original — no son adiciones post-entrega sin numerar. Solo RN-019,
RN-020 y RN-021 son genuinamente nuevas (agregadas tras el incidente del 27/08 y la función de
cancelación de días, respectivamente). `docs/spec.md` línea 148 debería corregirse para reflejar
esto.

| RN | Mensaje en el documento original | Mensaje en el código | ¿Coincide? |
|---|---|---|---|
| RN-001 | "El turno seleccionado ya no está disponible. Por favor seleccioná otro horario." | "RN-001: Solo se puede reservar un turno en estado DISPONIBLE." | **No** (mismo significado, texto distinto — el código no usa el mensaje orientado al usuario del documento) |
| RN-002 | "El paciente ya tiene un turno reservado en este horario. Verificá en Mis Turnos." | "RN-002: El paciente ya posee un turno reservado en este mismo horario." | Parcial (mismo contenido, sin la sugerencia "Verificá en Mis Turnos") |
| RN-003 | "Conflicto de horario: el médico ya tiene un turno programado en este horario." | "RN-003: El médico ya tiene un turno reservado en este mismo horario." | Parcial (mismo contenido, distinto texto) |
| RN-004 | "El turno ya fue atendido y no puede modificarse." | "RN-004: Un turno en estado ATENDIDO es inmutable." | Parcial |
| RN-005 | "Existen [N] atenciones sin cobro registrado para este médico. Completar antes de liquidar." | "RN-005: No se puede liquidar si existen turnos atendidos sin cobro registrado en el período (N factura(s) pendiente(s))." | Parcial (**sí** incluye N, como pide el documento) |
| RN-006 | — (validación silenciosa; no editable desde el formulario de cobro) | Sin mensaje — no hay ningún endpoint que permita editar el split, coherente con "silenciosa" | **Sí** |
| RN-007 | "La liquidación ya fue emitida. Para corregirla, primero anulala desde el historial." | "RN-007: La liquidación ya se encuentra anulada." (este es el mensaje de **doble anulación**, no de "intentar modificar una emitida" — no existe ruta para eso, ver abajo) | **No** — el documento espera un mensaje al intentar *modificar* una emitida; el código no tiene ningún endpoint de edición, así que ese mensaje específico nunca se muestra (la UI simplemente no ofrece la opción) |
| RN-008 | "El DNI no puede editarse. Si hay un error de carga, contactar a administración." | "RN-008: El documento (DNI / Pasaporte) del paciente es inmutable una vez creado." | Parcial |
| RN-009 | "Ya existe un paciente registrado con ese DNI. Verificar en la búsqueda incluyendo inactivos." | "RN-009: El documento (DNI / Pasaporte) ya se encuentra registrado (incluso si está inactivo)." | Parcial |
| RN-010 | "No tenés permisos para modificar esta evolución clínica." | "RN-010: No tenés permisos para modificar esta evolución clínica." (edición/anulación cruzada) — también existen 2 variantes más específicas para otros casos (acceso a HC ajena, crear evolución sin vínculo) | **Sí**, texto casi idéntico en el caso principal |
| RN-011 | "Las evoluciones no pueden eliminarse. Podés marcarla como anulada indicando el motivo." | "RN-011: Se requiere un motivo obligatorio para anular la evolución." | Parcial (el código valida específicamente la ausencia de motivo; el documento describe el caso general de "querer eliminar") |
| RN-012 | "El médico tiene [N] turnos activos. Reasignalos o cancelalos antes de proceder con la baja." | "RN-012: No se puede dar de baja al médico porque tiene N turno(s) futuro(s) sin reasignar." | Parcial (**sí** incluye N) |
| RN-013 | "El consultorio [N] ya está asignado a otro médico en ese horario." | "RN-013: El consultorio ya tiene un contrato activo que se superpone con el día/horario/período solicitado." | Parcial (el código no incluye el número/nombre del consultorio en el mensaje, el documento sí) |
| RN-014 | "El tiempo de uso mínimo es de 4 horas por jornada." | "RN-014: La asignación mínima de consultorio es de 4 horas (240 minutos). Se asignaron solo N min." | Parcial (el código es más detallado) |
| RN-015 | — (validación en formulario de alta de usuario; campos mutuamente excluyentes) | "RN-015: El rol asignado no es válido..." / "...único rol activo simultáneo" (sí tiene mensajes explícitos, a diferencia de lo que describe el documento) | **No** — el documento esperaba una validación silenciosa de formulario; el backend sí expone mensajes explícitos (más robusto, ya que también hay que validarlo en la API, no solo en el formulario) |
| RN-016 | "No se puede eliminar, existe médicos activos asociados a esta obra social" | "RN-016: No se puede dar de baja: hay médicos activos asociados a esta obra social." | **Sí**, mismo contenido |
| RN-017 | "No tienes permisos para modificar el coseguro de otro médico" | "RN-017: No podés modificar el coseguro de otro médico." / "...las prestaciones de otro médico." / "...la relación con obras sociales de otro médico." (varias variantes, una por tipo de recurso) | **Sí** en el caso de coseguro (texto casi idéntico); el código además cubre prestaciones y obras sociales, que el documento no distingue como mensajes separados |
| RN-018 | "Existen turnos atendidos sin cobros registrados. Completar antes de liquidar." | "RN-018: Existen N atenciones sin cobro registrado para este médico. Completar antes de liquidar." | **Sí**, casi idéntico, y **sí** incluye N |

## Conclusión
Ninguna regla usa el texto **literal** del documento — el código usa un estilo consistente propio
("RN-0XX: ...") en vez de los mensajes redactados para usuario final del documento original. El
**contenido/significado** coincide en 16 de 18 casos (RN-006 y RN-017 con más precisión incluso).
Los 2 casos sin equivalencia directa:
- **RN-007**: el documento espera un mensaje al intentar *modificar* una liquidación emitida; el
  código no tiene ningún endpoint de edición, por lo que ese mensaje nunca se dispara (la opción
  simplemente no existe en la UI/API).
- **RN-015**: el documento esperaba una validación silenciosa de formulario; el código expone
  mensajes de error explícitos (evaluación: esto es una mejora, no un defecto — el documento no
  contempla que la regla también deba cumplirse llamando a la API directamente, sin pasar por el
  formulario).

Ningún caso es una regla **ausente** — las 18 reglas del documento original están implementadas,
con mensajes en español, orientados a que el usuario entienda qué pasó, aunque con una redacción
distinta a la literal del documento.
