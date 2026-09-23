# Project Constitution — Consultorios Mitre

Reglas fijas del proyecto. Se cargan una sola vez en la configuración del agente (Project Rules) y aplican a **todas** las tareas, sin necesidad de repetirlas en cada prompt.

## 1. Arquitectura

- Backend en capas estrictas: `Controller` → `Service` → `Repository` → `Entity`.
- **Toda regla de negocio (RN-001 a RN-015) vive en el Service.** Nunca se implementa una validación solo en el frontend ni solo en el controller.
- Los controllers no contienen lógica de negocio, solo mapeo request/response y control de acceso por rol.
- Un módulo del sistema = un paquete Java (`usuarios`, `pacientes`, `medicos`, `turnos`, `historiaclinica`, `facturacion`, `arrendamiento`, `reportes`).

## 2. Base de datos

- No modificar el esquema de `/db/schema.sql` sin aprobación explícita — está definido por la propuesta técnica aceptada por el cliente.
- **Todo cambio de esquema ejecutado directamente contra una base viva** (migración de `docs/migrations/` aplicada, o cualquier `ALTER`/`CREATE`/`UPDATE` suelto corrido a mano) se registra en `docs/DB_CHANGELOG.md` en el momento en que se ejecuta — no después. La entrada incluye: fecha, script o comando exacto, base afectada (real vs. de prueba/clon), quién lo aplicó, motivo, y cómo se verificó. Nunca aplicar un cambio de esquema contra la base real sin confirmación explícita del usuario primero.
- **Soft delete siempre.** Ninguna tabla transaccional (Pacientes, Médicos, Turnos, Historia_Clinica, Evolucion_Clinica, Facturacion, Liquidacion_Medica, Arrendamiento_Modulo, Consultorio) permite `DELETE` físico. Se usa el campo `Visible`/`Estado` correspondiente.
- Toda entidad nueva respeta el nombre de tabla y campos tal como están en `schema.sql` (español, sin traducir a inglés).
- Los IDs autoincrementales (`Serial`) se mapean como `IDENTITY(1,1)` en SQL Server.

## 3. Seguridad y roles

- 3 roles: `GERENTE`, `ADMINISTRATIVO`, `MEDICO`. Un usuario tiene un único rol activo (RN-015). El paciente **no** tiene usuario ni login — es un registro de datos gestionado por Gerente/Administrativo.
- Cada endpoint valida el rol permitido según la matriz de "Acceso y Permisos" de `spec.md` — nunca ocultar un botón en el frontend como única barrera.
- Passwords siempre hasheadas (BCrypt o equivalente). Nunca loguear ni exponer el hash.
- Un médico solo accede a sus propios turnos/pacientes/historias clínicas ("solo propios" en la matriz de permisos).

## 4. Auditoría

- Toda mutación sobre **Historia Clínica** y **movimientos financieros** (Facturacion, Cobros, Liquidacion_Medica) registra: usuario que la realizó, fecha/hora, y — en el caso financiero — valor anterior/nuevo y motivo del ajuste.
- Las evoluciones clínicas y las liquidaciones emitidas no se editan: se anulan (con motivo obligatorio) y se genera una nueva.

## 5. Testing

- Ninguna regla RN-XXX se da por implementada sin su test de aceptación correspondiente.
- Al terminar un módulo, correr los tests de integración del flujo al que pertenece (Flujo A, B o C de `spec.md`) antes de pasar al siguiente módulo.
- Los mensajes de error mostrados al usuario deben coincidir con los definidos en la tabla de reglas de negocio de `spec.md` (no reformular libremente).

## 6. Alcance

- No implementar integraciones externas (verificación online con sistemas reales de obras sociales, AFIP, laboratorios) — están explícitamente fuera de alcance. Esto no incluye el catálogo interno de Obras Sociales (§4.9 de `spec.md`), que es dato maestro propio del sistema, sin integración externa.
- No generar apps móviles nativas — solo web responsivo.
- Ante ambigüedad entre lo que pide un prompt puntual y lo que dice `spec.md`/`plan.md`, **`spec.md` manda**. Si hay contradicción, avisar antes de generar código.

## 7. Estilo de trabajo con el agente

- Una tarea = un módulo o sub-funcionalidad chica. No pedir "implementá todo el sistema".
- Planning mode para arrancar un módulo nuevo; Fast mode para fixes puntuales o ajustes menores sobre algo ya construido.
- No avanzar a la siguiente etapa (Etapa 2, Etapa 3) sin haber validado manualmente el checkpoint de la etapa anterior.
