# Preguntas y pendientes — ronda de correcciones del checklist 27/08/2026

Anotadas durante la corrección del `CHECKLIST_VERIFICACION_COMPLETA_27_08.xlsx`, para ver al final (no se interrumpió el trabajo para preguntarlas). Todo lo que ya se corrigió está en `docs/ANEXO_CAMBIOS_POST_ENTREGA.md` §D.

---

## 1. Cosas que hay que decidir con el cliente / vos

### 1.1 Historias Clínicas — ¿GERENTE debe poder ver alguna HC?
Hoy: **GERENTE recibe 403 en todo el módulo de HC**, y eso está **alineado con la spec** (`spec.md` §2.1 y la tabla de permisos: "Historias Clínicas → GERENTE: Sin acceso"; el Home de Gerente en la Entrega no lista Historias Clínicas). El checklist lo marcó "[Falla]" pero es el comportamiento contractual.
**Pregunta:** ¿se deja así (recomendado, es lo que dice el documento) o el cliente quiere que Gerente vea las HC en modo lectura? Si es lo segundo, es un desvío nuevo que hay que documentar.

### 1.2 Historias Clínicas — ¿ADMINISTRATIVO debe ver el texto clínico completo?
Hoy: **sí lo ve** (motivo/diagnóstico/tratamiento/etc.), decisión ya tomada el 2026-08-23 (Anexo B.3) para alinear con "Ver historias clínicas: Sí" del documento original. El checklist pide "verificar contra la spec" — verificado: **la spec dice que sí**.
**Pregunta:** ¿se confirma? Si el cliente en la práctica prefiere que Administrativo NO vea el texto clínico (solo datos administrativos + adjuntos), hay que revertir el enmascarado y documentarlo.

### 1.3 Historias Clínicas — ¿la spec exige un listado de todas las HC?
Verificado: **no**. La Entrega y `spec.md` (RF-H1 a RF-H6) describen la HC como acceso *por paciente*, no como un listado navegable. Hoy: el MEDICO la busca por nombre/DNI desde su módulo; el ADMINISTRATIVO entra por el botón dentro de "Pacientes".
**Pregunta:** ¿alcanza así o quieren que agreguemos una pantalla de listado de HC (con filtros)? Es funcionalidad nueva, no está en el contrato.

### 1.4 Historias Clínicas — "la vista del MEDICO se ve fea y no tiene mucho sentido"
Se corrigió lo concreto (nombre/DNI del paciente ahora se muestran, adjuntos accesibles). Pero "se ve feo / no tiene sentido" es subjetivo.
**Pregunta:** ¿qué se espera exactamente de esa pantalla? ¿Un layout tipo ficha con los datos del paciente arriba y las evoluciones como timeline? ¿Poder filtrar evoluciones por fecha? Con eso definido se hace un rediseño puntual.

### 1.5 Consultorios — la palabra "OCUPADO"
Feedback del checklist: "no queda bien 'OCUPADO' porque en la práctica no está ocupada toda la franja horaria". Hoy el badge dice OCUPADO cuando el consultorio tiene ≥1 contrato de arrendamiento activo.
**Opciones:**
- (a) Cambiar el texto a algo como **"CON AGENDA"** o **"ASIGNADO"** (cambio de 1 línea).
- (b) Mostrar el detalle: "Ocupado Lun 8–12, Mié 14–18" en vez de un badge plano.
- (c) Calcular "ocupado ahora" solo si hay un contrato cuyo día/horario incluye el momento actual, y "asignado" el resto del tiempo.
**Pregunta:** ¿cuál preferís? (Recomiendo (a) para ya, (b) como mejora.)

### 1.6 Reportes — CSV vs Excel
El pedido decía ".csv" explícitamente y eso es lo que se entregó (se abre en Excel igual). La Entrega original (RF-R3) hablaba de "exportación a Excel". 
**Pregunta:** ¿alcanza con CSV o hace falta `.xlsx` nativo? El `.xlsx` requiere agregar una librería al backend (Apache POI) y un endpoint de exportación — es bastante más trabajo.

### 1.7 Métodos de pago
Se restringió a **EFECTIVO** y **TRANSFERENCIA** en todos lados (era el pedido). Se sacaron Tarjeta Débito, Tarjeta Crédito y "Obra Social" como método de pago.
**Pregunta:** ¿confirmás que el consultorio realmente no cobra nunca con tarjeta? Si en algún momento sí, hay que volver a habilitarla (y decidir si tarjeta queda PAGADO al instante o PENDIENTE como transferencia).

---

## 2. Aclaración funcional que pediste (Liquidaciones — obra social y copago)

Cómo funciona hoy el circuito, de punta a punta:

1. **Al reservar el turno** se carga (opcional): obra social del paciente y un **copago** como texto/número libre. *No* se prellena automáticamente desde el coseguro que el médico configuró para esa obra social — eso es una mejora pendiente ya documentada.
2. **Al marcar el turno ATENDIDO** se genera la Facturación automática:
   - `importeTotal` = importe de consulta del médico.
   - `importeCopago` = el copago cargado en la reserva (o 0).
   - `obraSocial` = la de la reserva, o la del paciente, o "Particular".
   - `porcentajeMedico` / `porcentajeConsultorio` = los del **contrato de arrendamiento vigente** para ese médico + consultorio + día/horario (si no hay contrato, 70/30 por defecto).
3. **Al registrar el cobro** se elige método (efectivo/transferencia), importe total, cubierto por OS y copago. Transferencia → factura queda PENDIENTE hasta confirmarla; efectivo → PAGADO.
4. **La liquidación** de un período agarra las facturaciones de ese médico en el rango y aplica el split 70/30 (o el % del contrato) sobre el `importeTotal`. Se bloquea si:
   - hay facturas del período en estado PENDIENTE (RN-005), o
   - hay turnos ATENDIDO del período **sin ningún Cobro asociado** (RN-018).

**Lo que NO hace hoy** (por si era la duda): el copago no se descuenta del importe que se le liquida al médico — el split se calcula sobre el `importeTotal` completo. El copago se registra como dato pero no entra en la fórmula de la liquidación. Si eso debería ser distinto (p. ej. el copago es 100% del médico, o se resta antes de splittear), decilo y lo ajustamos — es una regla de negocio nueva.

---

## 3. Ítems que necesitan re-prueba en vivo (no se pudieron verificar acá)

| Ítem | Por qué | Cómo probarlo |
|---|---|---|
| **Dashboard gerencial** | No se pudo reproducir la falla sin la base viva. Se endureció el código (tolera filas duplicadas, `@Transactional`). | Entrar como GERENTE. Si el dashboard sigue en blanco, mirar el toast de error — ahora incluye `[NombreDeExcepción: mensaje]` entre corchetes — o `target/.../*.log`. Pasame ese texto. |
| **Flujo A completo** (reservar → En Espera → Atendido → factura automática) | Requiere la base y datos reales. | Como ADMINISTRATIVO: reservar un turno, pasarlo a En Espera, marcarlo Atendido. Verificar que en Facturación aparece una fila nueva PENDIENTE para ese paciente/médico, sin insertar nada por SQL. |
| **Adjuntos** (subir PDF/JPG/PNG, rechazar .exe/.docx, descargar) | Ahora ADMINISTRATIVO tiene un botón de clip en cada evolución. | Como ADMINISTRATIVO y como MEDICO: abrir una HC con al menos una evolución, clic en el clip, subir un PDF (ok), un .txt (debe rechazar con "RF-H3: Solo se permiten archivos PDF, JPG o PNG"), y descargar el que subió. |
| **RN-012 baja de médico** | La query cambió (ahora solo cuenta RESERVADO/EN_ESPERA). | Como GERENTE: dar de baja un médico que tiene turnos DISPONIBLE a futuro pero **ningún** turno reservado → debe **dejar**. Después, reservar un turno futuro con ese médico e intentar la baja → debe **bloquear** con el mensaje RN-012. |
| **Reactivar médico (H2)** | Dependía del bug de RN-012, ahora desbloqueado. | Dar de baja un médico sin turnos reservados → reactivarlo → confirmar que vuelve a poder loguearse (password = matrícula). |
| **Transferencia** | Circuito nuevo. | Registrar un cobro con Transferencia → la fila queda con badge naranja "TRANSFERENCIA A VALIDAR" y botón "Confirmar transferencia" → confirmarla → pasa a PAGADO. |
| **IDOR médicos/turnos/contratos** (Hallazgos 1, 2, 3) | Ver instrucciones abajo (§4). | |

---

## 4. Cómo verificar los Hallazgos 1, 2 y 3 (el usuario dijo "no entendí")

Estos son huecos de seguridad: un MEDICO logueado podía ver datos de otros médicos. Ya se corrigieron; así se comprueba que la corrección funciona. Hace falta un cliente HTTP (la extensión REST Client de VS Code, Postman, o `curl`) porque son requests directas a la API, no botones.

**Preparación:** logueate como un MEDICO (POST `/api/auth/login` con su email y su matrícula como password) y guardá el `token` que devuelve.

### Hallazgo 1 — datos de otro médico
```
GET /api/medicos/{ID_DE_OTRO_MEDICO}
Authorization: Bearer <token del médico logueado>
```
- **Antes:** devolvía 200 con los datos del otro médico (nombre, matrícula, importe, coseguros).
- **Ahora:** debe devolver **403** ("No tenés permisos para acceder a datos de otro médico").
- Con tu **propio** id debe seguir devolviendo 200.
- Mismo test con `/api/medicos/{otro_id}/prestaciones` y `/api/medicos/{otro_id}/obras-sociales`.

### Hallazgo 2 — turno de otro médico
```
GET /api/turnos/{ID_DE_UN_TURNO_DE_OTRO_MEDICO}
Authorization: Bearer <token del médico logueado>
```
- **Antes:** devolvía 200 con los datos del turno y del paciente.
- **Ahora:** debe devolver **403**. Con un turno propio, 200.
(Para conseguir el id de un turno ajeno: pedí `GET /api/turnos?fecha=...` como GERENTE y copiá un `idTurno` de otro médico.)

### Hallazgo 3 — contratos de otros médicos
```
GET /api/arrendamientos/contratos
Authorization: Bearer <token del médico logueado>
```
- **Antes:** devolvía **todos** los contratos del sistema con los porcentajes pactados de cada médico.
- **Ahora:** sigue devolviendo todos (el médico necesita ver qué consultorios están ocupados), pero en las filas que **no son suyas** los campos `porcentajeConsultorio`, `porcentajeMedico` y `observaciones` vienen en `null`. En sus propias filas, con valores.

---

## 5. Nota sobre el Excel del checklist

La portada de `CHECKLIST_VERIFICACION_COMPLETA_27_08.xlsx` menciona hojas que **no están en el archivo**: "Hallazgos", "Endpoints", "Matriz de permisos", "Flujos E2E". El workbook exportado solo tiene 3 hojas (Portada, Checklist, Resumen módulos).

En particular, varios ítems del checklist referencian "el detalle del Hallazgo 4" (y 1, 2, 3) que estaría en la hoja "Hallazgos" faltante. Ese detalle sí existe en el repo: está en `docs/CHECKLIST_VERIFICACION_COMPLETA.md` §1 (tabla de Hallazgos 1–8). Si el Excel se va a seguir usando como fuente, conviene re-exportarlo completo o trabajar directamente sobre el `.md`.

---

*Generado 2026-08-27 junto con la ronda de correcciones. Ver `docs/ANEXO_CAMBIOS_POST_ENTREGA.md` §D para el detalle de lo ya corregido.*
