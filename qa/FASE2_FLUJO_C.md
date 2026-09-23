# Fase 2 — Flujo C: Incorporación de médico

Generado: 2026-09-23. Todas las pruebas se hicieron llamando directamente a la API REST
(`/api/medicos`, `/api/arrendamientos`) con las credenciales reales de Gerente
(`admin@medispace.com`), saltando el frontend (regla 9). Rango de fechas usado para los
contratos: 2026-09-28 (lunes) a 2026-10-11 (domingo) — 2 semanas exactas, 2 ocurrencias de cada
día de semana.

## Pasos y resultado

| # | Paso | Resultado |
|---|---|---|
| 1 | Alta de QA_Dr_Cardio por el Gerente (`POST /api/medicos`) | **PASS** — usuario `qa.dr.cardio@qa.test` (rol MEDICO) creado automáticamente, médico id 9, sin turnos todavía |
| 2 | Contrato QA-1, Lunes+Martes+Miércoles 9–13, 30min, cupo 8, 70/30 | **PASS** (con nota) — creado como 3 contratos separados (uno por día), ver "Nota de modelo" abajo |
| 3 | Turnos generados = (días coincidentes del rango × 8) | **PASS, verificado por SQL** — 2 lunes+2 martes+2 miércoles = 6 días × 8 = 48 turnos, confirmado exacto contra la tabla `Turnos` |
| 4 | QA_Dra_Pedia en QA-1, lunes 15–19 → se permite | **PASS** — creado sin error (distinto horario, mismo día/consultorio que Cardio) |
| 5a | QA_Dr_Clinico en QA-1, lunes 11–15 → RN-013 | **PASS** — rechazado con `400 {"message":"RN-013: El consultorio ya tiene un contrato activo que se superpone..."}` |
| 5b | Contrato de 2 horas → RN-014 | **PASS** — rechazado con `400 {"message":"RN-014: La asignación mínima de consultorio es de 4 horas..."}` |
| 5c | Contrato en QA-3 (mantenimiento) → se rechaza | **FAIL — BUG-001** (ver abajo). El contrato se creó igual (HTTP 201, id 22, Estado ACTIVO) y generó 16 turnos "Disponible" en un consultorio "En mantenimiento" |
| 6 | Médico con dos contratos activos en distintos consultorios y días → se permite | **PASS** (tras ajustar el día de la segunda prueba, ver BUG-002) — QA_Dr_Clinico con contrato QA-2/Martes (id 23, 16 turnos) y QA-1/Viernes (id 25, 16 turnos), ambos activos y con turnos generados correctamente |

## Nota de modelo (no es un bug, es una aclaración de spec vs. esquema)
El enunciado describe "un contrato: lunes a miércoles". El schema (`Arrendamiento_Modulo.Dia_Semana`)
y `ArrendamientoCreateDTO.diaSemana` solo admiten **un** día por fila — no hay forma de expresar un
rango de días en un único contrato. Para lograr la cobertura Lunes-Miércoles hacen falta 3
`POST /api/arrendamientos`, uno por día. El total de turnos generados (48) es matemáticamente
idéntico a lo que pediría el enunciado, así que no afecta el resultado funcional, pero si se
espera literalmente "un contrato" como una sola fila, marcar como **A DEFINIR**.

## Bugs encontrados

### BUG-001 (Alta severidad) — Se puede crear un contrato de arrendamiento sobre un consultorio no disponible
- **Archivo/línea**: `src/main/java/com/medispace/app/service/impl/ArrendamientoServiceImpl.java`,
  método `crearArrendamiento` (líneas 35–99). Valida RN-014 (línea 41-46) y RN-013 (línea 52-59)
  pero **nunca lee `consultorio.getEstado()`**.
- **Pasos para reproducir**: `PUT /api/consultorios/{id}/estado` con `EN_MANTENIMIENTO` sobre un
  consultorio → `POST /api/arrendamientos` con ese `idConsultorio` y cualquier día/horario válido
  (≥4hs, sin superposición).
- **Esperado**: rechazo (enunciado Flujo C, paso 5, y RF-A6 que define 5 estados de consultorio
  que deberían ser significativos).
- **Obtenido**: `201 Created`, contrato queda `Estado: ACTIVO`, y además genera 16 turnos
  "Disponible" reservables por un paciente en un consultorio que está en mantenimiento (ver
  BUG-001b).
- **Evidencia**: arrendamiento id 22 (QA_Dr_Clinico / QA-3 / Jueves 9–13), consultorio 10
  (`Numero_Consultorio='QA-3'`, `Estado='EN_MANTENIMIENTO'` en el momento de la creación).

### BUG-001b (deriva de BUG-001) — Turnos "Disponible" reservables generados en un consultorio en mantenimiento
Consecuencia directa de BUG-001: como el contrato se crea igual, `generarTurnosParaContrato` corre
sin condicionar por el estado del consultorio y deja 16 turnos en estado `DISPONIBLE` en QA-3 —
un paciente podría reservar turno en un consultorio fuera de servicio.

### BUG-002 (Media severidad) — Un contrato puede crearse "exitosamente" (201, ACTIVO) generando 0 turnos, sin aviso
- **Archivo/línea**: `src/main/java/com/medispace/app/service/impl/TurnoServiceImpl.java`, método
  `generarTurnosParaContrato`, línea 63: `if (!turnoRepository.existsByMedicoIdMedicoAndFechaHora(medico.getIdMedico(), fechaHoraSlot))`.
  La deduplicación de turnos es por **(médico, fecha_hora) solamente** — no incluye el
  consultorio.
- **Cómo se disparó**: consecuencia encadenada de BUG-001. Al existir ya el contrato "fantasma"
  en QA-3 (Jueves 9–13, permitido por el BUG-001), un segundo contrato válido del mismo médico
  en QA-1 con el mismo día+horario (Jueves 9–13) no chocó contra RN-013 (que solo mira
  superposición **dentro del mismo consultorio**), así que el contrato se creó (`201`,
  `Estado: ACTIVO`, id 24) — pero como los `Fecha_Hora` exactos ya existían para ese médico
  (por el contrato en QA-3), la dedup por `(medico, fecha_hora)` descartó los 16 slots nuevos
  uno por uno. Resultado: `turnosGenerados: 0` en la respuesta, sin error ni advertencia.
- **Esperado**: o bien se rechaza la creación (ya hay actividad del médico en ese día/horario en
  otro consultorio — doble reserva física del profesional), o al menos se devuelve una
  advertencia visible si el contrato termina generando 0 turnos.
- **Obtenido**: `201 Created`, contrato válido en apariencia, cero turnos reales, sin ningún
  campo ni mensaje que lo señale (el front tendría que inspeccionar `turnosGenerados === 0`
  explícitamente, y hoy no lo hace en ningún lado del código revisado).
- **Evidencia**: arrendamiento id 24 (QA_Dr_Clinico / QA-1 / Jueves 9–13) — 0 filas en `Turnos`
  para `ID_Medico=11 AND ID_Consultorio=8`. Se resolvió el caso de prueba positivo #6 con un
  contrato limpio en Viernes (id 25) para no depender de este bug.
- **Relacionado — A DEFINIR**: no existe ninguna RN que impida que un mismo médico tenga dos
  contratos activos que se superpongan en día+horario en consultorios *distintos* (RN-013 solo
  mira superposición por consultorio, no por médico). No está en la propuesta técnica si esto
  debería estar permitido (¿un médico atendiendo simultáneamente en dos consultorios?) o
  prohibido. Recomendación: agregar una validación de superposición a nivel médico,
  independientemente de si se corrige BUG-001/BUG-002.

## Datos QA creados en este flujo
Ver [`qa/qa_seed_arrendamientos_ids.json`](qa_seed_arrendamientos_ids.json) — arrendamientos
18–25, todos con matrícula QA-100X y filtrables por consultorio QA-1/2/3. Se limpian en Fase 9
vía `qa_cleanup.sql`.
