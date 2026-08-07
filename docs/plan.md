# Plan — Implementación Técnica Consultorios Mitre

## 1. Stack (según propuesta)

- **Backend:** Java (recomendado: Spring Boot para acelerar el desarrollo agéntico — capas claras, validaciones declarativas, seguridad por rol vía Spring Security).
- **Base de datos:** SQL Server.
- **Deploy:** Apache Tomcat.
- **Frontend:** web responsivo (no se especifica framework en la propuesta — a definir; sugerido: server-rendered o SPA liviana según preferencia del equipo).
- **Archivos adjuntos:** almacenamiento en filesystem/infra definida, ruta referenciada en BD (`Adjuntos_HistoriaClinica.Ruta_Archivo`).

## 2. Arquitectura por capas

```
Controller (REST, valida rol vía Spring Security)
  → Service (reglas de negocio RN-001 a RN-015, SIEMPRE acá, nunca solo en frontend)
    → Repository (JPA/Hibernate sobre SQL Server)
      → DB (esquema ya definido, ver sección 4)
```

Principio clave para SDD en Antigravity: **cada regla de negocio (RN-XXX) es un test de aceptación** antes de generar el código del service correspondiente. No se implementa una regla sin su test.

## 3. Módulos → Etapas (orden de construcción)

| Etapa | Módulos | Objetivo |
|---|---|---|
| 1 | Usuarios, Pacientes, Médicos/Agenda | Entidades base + auth + roles |
| 2 | Turnos, Historias Clínicas, Facturación | Operatoria crítica diaria |
| 3 | Arrendamiento, Reportes/Dashboard | Automatización e inteligencia gerencial |

Construir en este orden porque cada etapa depende de las tablas de la anterior (ver mapa de dependencias de la propuesta: Usuarios alimenta a todos; Pacientes/Médicos alimentan a Turnos; Turnos alimenta a Historia Clínica y Facturación; Facturación alimenta a Arrendamiento y Reportes).

## 4. Modelo de datos (ya definido en la propuesta — no rediseñar, solo migrar)

Tablas principales agrupadas por dominio:

- **Identidad:** `Usuarios`
- **Personas:** `Pacientes`, `Medicos`, `Especialidades`, `ObraSocial`, `Medico_ObraSocial` (N:M)
- **Agenda/Turnos:** `Agenda_Medico`, `Turnos`, `Prestaciones_Medicas`, `Consultorio`
- **Clínico:** `Historia_Clinica` (1:1 con Paciente), `Evolucion_Clinica`, `Adjuntos_HistoriaClinica`
- **Financiero:** `Cobros`, `Facturacion`, `Liquidacion_Medica`, `Pago_Facturacion`
- **Arrendamiento:** `Arrendamiento_Modulo`, `Uso_Consultorio`, `Cierre_Diario`
- **Gerencial:** `Reporte_Generado`, `Dashboard_Gerencial`

Todas las tablas transaccionales usan **soft delete** (`Visible BIT`), no DELETE físico — esto es consistente con RN-004, RN-007, RN-011.

## 5. Cómo dividir esto en tareas para Antigravity (bajo consumo de tokens)

En vez de "construime el sistema", pasar **una tarea por vez**, en este orden, cada una autocontenida:

1. `Modelar entidades JPA + migraciones SQL para Usuarios, Pacientes, Medicos, Especialidades, ObraSocial (Etapa 1)`
2. `Implementar auth + roles (Spring Security) con las reglas RN-015`
3. `CRUD Pacientes con RN-008 y RN-009 como tests de aceptación`
4. `CRUD Médicos + Agenda_Medico, con alta automática de usuario asociado`
5. `Generación automática de Turnos desde Agenda_Medico`
6. `Reserva/cancelación/estados de Turnos con RN-001 a RN-004`
7. `Historia Clínica + Evolucion_Clinica con RN-010, RN-011`
8. `Facturación automática al pasar turno a Atendido, con RN-005, RN-006, RN-007`
9. `Arrendamiento: Consultorio + Arrendamiento_Modulo con RN-013, RN-014`
10. `Reportes + Dashboard_Gerencial (lectura agregada, sin lógica de negocio nueva)`

Cada tarea: darle al agente **solo el fragmento de spec.md correspondiente a ese módulo** + el fragmento del esquema SQL de esas tablas — no el documento completo. Esto es lo que más ahorra contexto/tokens.

## 6. Project Constitution sugerida (archivo fijo para Antigravity)

Reglas que no cambian entre tareas — ponerlas una vez en la "constitution" del proyecto en vez de repetirlas en cada prompt:

- Toda regla de negocio se valida en el Service (backend), nunca solo en el frontend.
- Soft delete siempre (`Visible`), nunca DELETE físico en tablas transaccionales.
- Toda mutación financiera y clínica queda auditada (usuario, fecha/hora, valores antes/después).
- Acceso a endpoints controlado por rol (Gerente/Administrativo/Médico/Paciente), no solo ocultar botones en UI.
- Nomenclatura de tablas/campos en español, tal como está en el esquema original (no traducir a inglés).

## 7. Testing (según Aseguramiento de Calidad de la propuesta)

- Unitarias: por Service, una por cada RN-XXX.
- Integración: Turnos↔Facturación↔Historia Clínica↔Liquidaciones↔Reportes (según flujo A).
- Funcionales: un test por flujo integral (A, B, C) de la propuesta.
- Seguridad: matriz de permisos por rol y por módulo (tablas de "Acceso y Permisos" de la propuesta).
- Rendimiento: concurrencia en reserva de turnos (evitar doble-reserva del mismo turno).

## 8. Fuera de alcance (no construir)

Integraciones con APIs de obras sociales/AFIP/laboratorios, apps móviles nativas, infraestructura de hosting empresarial — confirmado en spec.md sección 7.
