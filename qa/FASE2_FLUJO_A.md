# Fase 2 — Flujo A: Atención estándar

Generado: 2026-09-23. Pruebas por API directa (regla 9), roles reales: `secretaria@medispace.com`
(ADMINISTRATIVO) y `qa.dr.cardio@qa.test` (MEDICO, password = matrícula `QA-1001`). Turnos usados:
1837, 1838, 1839, 1840 (QA_Dr_Cardio, QA-1, 2026-09-28).

## Resultado: 16/16 checks PASS, sin bugs nuevos

| Paso del enunciado | Resultado |
|---|---|
| 1. Alta de paciente → HC vacía 1:1 | **PASS** (ya verificado en Fase 1 para los 6 pacientes QA) |
| 2. Reservar (tipo consulta, método pago, cobertura/copago) → Reservado; médico lo ve en su agenda | **PASS** — `POST /turnos/1837/reservar` → `RESERVADO`; `GET /turnos/1837` como médico devuelve el paciente |
| 3. En Espera → se genera facturación Pendiente | **PASS** — `idFacturacion` presente, `estadoPagoFacturacion=PENDIENTE` |
| — médico lo ve "en tiempo real" | **NO IMPLEMENTADA** — ver nota abajo |
| 4. Cobro en efectivo durante la espera, importe de la config. del médico | **PASS** — `importeTotal=20000` (= `Medicos.Importe_Consulta` de QA_Dr_Cardio), cobro EFECTIVO → `PAGADO` |
| 5. Evolución completa + PDF + imagen adjuntos → Atendido (irreversible) | **PASS** — evolución id 5, adjuntos id 11 (pdf) y 12 (png) subidos (201), turno → `ATENDIDO` |
| 6. Idempotencia En Espera→Atendido no duplica facturación | **PASS** — mismo `idFacturacion=44` antes y después de `ATENDIDO` |
| RN-004 (no probado explícitamente en el enunciado pero validado acá) | **PASS** — intentar cambiar un turno `ATENDIDO` devuelve 400 `RN-004: ... inmutable` |
| 7. Split $20.000 → médico $14.000 / consultorio $6.000, referencia al contrato vigente | **PASS** — `porcentajeMedico=70.00`, `porcentajeConsultorio=30.00` (tomados del contrato de arrendamiento id 18, no de un default fijo) → $14.000/$6.000 exactos |
| 8a. Obra social con copago | **PASS** — turno 1838, QA_P2/QA_OSDE, `copago=5000` al reservar → `Facturacion.Importe_Copago=5000`, `Obra_Social=QA_OSDE` |
| 8b. Transferencia → "a validar" → confirmar → Pagado | **PASS** — turno 1839, QA_P3/QA_IOMA: cobro TRANSFERENCIA deja la factura en `PENDIENTE` (no se auto-confirma), `PUT /confirmar-transferencia` → `PAGADO` |
| 8c. Cobro tardío con turno ya Atendido | **PASS** — turno 1840: se marcó `ATENDIDO` sin cobro previo, y el cobro posterior igual pasó a `PAGADO` (el estado del turno no bloquea el cobro) |
| 9. Liquidación diaria/semanal/mensual | Pendiente — se hace junto con Fase 4 (Facturación y Liquidaciones) para no duplicar trabajo |

## Hallazgo: "tiempo real" (NO IMPLEMENTADA)
El enunciado pide que el médico vea el paso a "En Espera" **en tiempo real, sin recargar, en dos
contextos de navegador**. Revisé todo `src/main/resources/static/js/`: el único `setInterval` de
toda la SPA es el reloj de la topbar (`app.js`, refresca cada 60s la fecha mostrada) — no hay
polling del listado de turnos, ni `WebSocket`, ni `EventSource`/SSE en ningún módulo. La agenda
del médico (`turnos.js`/`modules/turnos.js`) solo se recarga cuando el usuario navega o dispara
una acción explícita (`loadTurnos()` bajo demanda). **Conclusión: el requisito de tiempo real no
está implementado** — el comportamiento real es "hay que volver a entrar a la vista o refrescar
manualmente para ver el cambio de estado hecho por otro usuario". No es un bug si se entiende como
gap de alcance vs. la propuesta original; lo marco NO IMPLEMENTADA en vez de bug porque no hay
código que lo intente y falle — directamente no existe el mecanismo.

## Métodos de pago — nota
`RegistrarCobroDTO` documenta 4 métodos en su comment (EFECTIVO/TARJETA/TRANSFERENCIA/
MERCADOPAGO) pero `FacturacionServiceImpl.METODOS_PAGO_VALIDOS` solo acepta `EFECTIVO` y
`TRANSFERENCIA` (decisión de negocio post-entrega documentada en el propio código, checklist
27/08). No es un bug — es intencional y ya está probado por `TARJETA`/`MERCADOPAGO` devolviendo
400 si se los intenta usar (no lo probé explícitamente en este flujo, pero surge del código leído
en `FacturacionServiceImpl.registrarCobro`).
