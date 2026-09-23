# Fase 0 — Reconocimiento y respaldo

Generado: 2026-09-23

## 1. Stack técnico

- **Backend**: Spring Boot 3.2.3, Java 17, Maven (`pom.xml`). Módulos: `spring-boot-starter-web`,
  `-data-jpa`, `-security`, JWT (`io.jsonwebtoken` 0.11.5), driver `mssql-jdbc` (runtime).
- **Persistencia**: Hibernate/JPA sobre SQL Server (`SQLServerDialect`), `ddl-auto: validate`
  (el esquema NO lo gestiona Hibernate — vive en `docs/schema.sql` + migraciones manuales).
- **Frontend**: SPA en JS vanilla servida como estático por Spring (`src/main/resources/static/js/`):
  `app.js`, `api.js`, `login.js`, `turnos.js`, `pacientes.js`, `medicos.js`, `facturacion.js`,
  `historias.js`, `reportes.js`, `arrendamientos.js`. Sin framework (no React/Vue/Angular).
- **Auth**: JWT stateless (`JwtAuthFilter`, `JwtUtils`, `SecurityConfig`), roles `GERENTE` /
  `ADMINISTRATIVO` / `MEDICO` vía `@PreAuthorize(hasRole/hasAnyRole)` en cada controller.
- **Build/deploy**: `mvn spring-boot:run` (dev) — tarda ~17s, sirve en `http://localhost:8080`
  (SPA en `/`, API en `/api`). No se encontró configuración explícita de despliegue en Tomcat
  standalone (WAR) en el repo — el `spring-boot-maven-plugin` empaqueta un JAR ejecutable con
  Tomcat embebido. Si el enunciado requiere Tomcat externo, es un punto a confirmar con el
  usuario (no hay `web.xml` ni plugin `maven-war-plugin`).
- **Tests existentes**: 19 archivos JUnit 5 (`src/test/java`), 125/125 verdes según la última
  verificación registrada (2026-09-09) — cubren en buena medida las reglas de negocio RN-001 a
  RN-021 a nivel de servicio y algunos controllers (`TurnoControllerTest`,
  `HistoriaClinicaControllerTest`, `ArrendamientoControllerTest`, `LiquidacionControllerTest`).

## 2. Conexión a la base real

- SQL Server, instancia **`JP-LAPTOP\SQLEXPRESS01`** (SQL Server 2019 Express, build 15.0.2000.5),
  base **`MediSpace`**, recovery model **SIMPLE**.
- Connection string vive en `src/main/resources/application.yml` (working copy, credenciales
  `sa` / contraseña de desarrollo local — no es un secreto de producción).
- `sqlcmd` no funciona en esta máquina (falta ODBC Driver 17). Todas las consultas de este
  reconocimiento se hicieron vía ADO.NET (`System.Data.SqlClient`) desde PowerShell.

## 3. Backup y verificación (Regla 1 — OBLIGATORIO antes de cualquier escritura)

```
BACKUP DATABASE [MediSpace]
TO DISK = N'C:\Program Files\Microsoft SQL Server\MSSQL15.SQLEXPRESS01\MSSQL\Backup\medispace_pre_qa_20260923.bak'
WITH INIT, CHECKSUM, NAME = N'MediSpace-Full-PreQA-20260923';

RESTORE VERIFYONLY
FROM DISK = N'...\medispace_pre_qa_20260923.bak'
WITH CHECKSUM;
```

- **Resultado**: `BACKUP` OK, `RESTORE VERIFYONLY` OK (backup íntegro, con checksums).
- Confirmado contra `msdb.dbo.backupset`: tamaño **6.52 MB**, `has_backup_checksums = True`,
  finalizado 2026-09-23 10:02:02.
- Ruta final: `C:\Program Files\Microsoft SQL Server\MSSQL15.SQLEXPRESS01\MSSQL\Backup\medispace_pre_qa_20260923.bak`
  (carpeta de backup por defecto de la instancia — mi usuario de Windows no tiene permiso de
  lectura directa ahí, pero SQL Server sí, y `RESTORE VERIFYONLY` lo confirma).

## 4. Snapshot de control (Regla 5)

Guardado en [`qa/snapshot_control.sql`](snapshot_control.sql) (reutilizable) y
[`qa/snapshot_control_pre_qa_20260923.txt`](snapshot_control_pre_qa_20260923.txt) (baseline).
Verificado previamente que **no existía ningún registro QA** en ningún rango reservado (0 filas
en las 7 verificaciones: `Usuarios` `@qa.test`, `Pacientes` DNI 99000001-99000999, `Pacientes`
`QA_%`, `Medicos` `QA-%`, `Consultorio` QA-1/2/3, `ObraSocial` `QA_%`, `Especialidades` `QA_%`).

**Hallazgo**: `docs/schema.sql` está desactualizado para `Dashboard_Gerencial` — la tabla viva
tiene una estructura distinta y más rica (indicadores del día: `Turnos_Atendidos`,
`Turnos_Cancelados`, `Turnos_No_Asistio`, `Turnos_Disponibles`, `Facturacion_Total_Dia`,
`Cobros_Pendientes`, `Nuevos_Pacientes`, `Fecha_Actualizacion`) que la definición documentada
(`Nombre_Indicador`/`Categoria`/`Valor`/`Periodo`). Coincide con el fix de caché del dashboard
mencionado en el historial de sesiones anteriores — el snapshot y el resto de la suite usan la
estructura real, verificada contra `INFORMATION_SCHEMA.COLUMNS`.

## 5. Inventario de solo lectura (sin datos personales)

| Tabla | Filas |
|---|---:|
| Usuarios | 13 |
| Pacientes | 14 |
| Medicos | 7 |
| Consultorio | 4 |
| Especialidades | 4 |
| ObraSocial | 5 |
| Prestaciones_Medicas | 5 |
| Medico_ObraSocial | 13 |
| Medico_Prestacion | 5 |
| Agenda_Medico (deprecada) | 3 |
| Arrendamiento_Modulo | 11 |
| Turnos | 1836 |
| Cobros | 28 |
| Historia_Clinica | 14 |
| Evolucion_Clinica | 2 |
| Adjuntos_HistoriaClinica | 2 |
| Facturacion | 35 |
| Liquidacion_Medica | 6 |
| Pago_Facturacion | 0 |
| Uso_Consultorio | 0 |
| Cierre_Diario | 0 |
| Reporte_Generado | 0 |
| Dashboard_Gerencial | 10 |

**Turnos por estado**: DISPONIBLE 1773, RESERVADO 15, EN_ESPERA 11, ATENDIDO 26, NO_ASISTIO 1,
CANCELADO 10.

**Rango de fechas con datos**: 2026-08-03 a 2027-02-27.

**Observación**: `Pago_Facturacion`, `Uso_Consultorio`, `Cierre_Diario` y `Reporte_Generado`
están vacías en la base real — esos módulos nunca se ejercitaron con datos reales, así que la
suite QA será la primera cobertura de punta a punta para ellos.

## 6. Matriz de cobertura (módulo → endpoint → rol → estado)

Extraída directamente de los 13 `@RestController` (no hay vistas server-side — todo es API REST
consumida por la SPA). Estado = **implementada** salvo que se indique lo contrario.

### Usuarios (`/api/usuarios`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST | GERENTE, ADMINISTRATIVO | Bloquea alta de rol MEDICO acá (RF-U1); ADMINISTRATIVO solo puede crear ADMINISTRATIVO |
| PUT /{id} | GERENTE, ADMINISTRATIVO | RN-015 (rol inválido) |
| GET /{id}, GET | GERENTE, ADMINISTRATIVO | |
| DELETE /{id} | GERENTE, ADMINISTRATIVO | Baja lógica (RN-020) |
| PUT /{id}/reactivar | GERENTE, ADMINISTRATIVO | |

### Pacientes (`/api/pacientes`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST, PUT /{id}, DELETE /{id} | ADMINISTRATIVO | Gerente solo lectura (confirmado en código) |
| GET /{id}, GET | GERENTE, ADMINISTRATIVO, MEDICO | Filtrado por `idMedicoPropio` si es MEDICO |

### Médicos (`/api/medicos`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST | GERENTE | Alta real (crea Usuario asociado) |
| PUT /{id} | GERENTE, ADMINISTRATIVO | |
| GET /{id} | GERENTE, ADMINISTRATIVO, MEDICO | Guard de ownership si MEDICO |
| GET (listar) | GERENTE, ADMINISTRATIVO | |
| DELETE /{id}, PUT /{id}/reactivar | GERENTE | RN-012 |
| GET/PUT /me, /me/prestaciones, /me/obras-sociales (+ CRUD) | MEDICO | El médico gestiona su propia cartilla |
| /{id}/prestaciones, /{id}/obras-sociales (CRUD) | GERENTE (prestaciones) / GERENTE+ADMINISTRATIVO (obras sociales) | Contratos ajenos con % ocultos para MEDICO (`listarContratos`) |

### Turnos (`/api/turnos`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST /{id}/reservar | GERENTE, ADMINISTRATIVO | |
| PUT /{id}/estado | GERENTE, ADMINISTRATIVO, MEDICO | Guard ownership si MEDICO |
| GET /{id}, GET (listar) | GERENTE, ADMINISTRATIVO, MEDICO | idMedico se fuerza desde JWT si es MEDICO |
| DELETE /{id} | GERENTE, ADMINISTRATIVO | |
| POST /cancelar-dia | GERENTE, ADMINISTRATIVO, MEDICO | RF-T8, cancelación masiva por día/rango |

### Historias Clínicas (`/api/historias-clinicas`)
| Endpoint | Rol | Notas |
|---|---|---|
| GET /paciente/{id} | ADMINISTRATIVO, MEDICO | **GERENTE explícitamente excluido** (sección 4.5) |
| POST /{idHC}/evoluciones | MEDICO | Solo el médico tratante (RN-010) |
| PUT /evoluciones/{id} | GERENTE, MEDICO | (nota: GERENTE puede editar aunque no puede "ver" vía el otro endpoint — a verificar como hallazgo de permisos, ver §7) |
| DELETE /evoluciones/{id}/anular | GERENTE, MEDICO | RN-011 |
| POST /evoluciones/{id}/adjuntos, GET /adjuntos/{id}/descargar | ADMINISTRATIVO, MEDICO | |

### Facturación (`/api/facturacion`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST /{id}/cobro | GERENTE, ADMINISTRATIVO | |
| PUT /{id}/confirmar-transferencia | GERENTE, ADMINISTRATIVO | |
| GET /{id}, GET (listar) | GERENTE, ADMINISTRATIVO | Filtro por fecha |

### Liquidaciones (`/api/liquidaciones`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST /generar | GERENTE, ADMINISTRATIVO | |
| PUT /{id}/anular | GERENTE | |
| GET /medico/{id}, GET /{id} | GERENTE, ADMINISTRATIVO, MEDICO | Guard ownership |
| GET /me | MEDICO | |
| GET (buscar) | GERENTE, ADMINISTRATIVO | |

### Arrendamientos (`/api/arrendamientos`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST, PUT /{id}/baja | GERENTE | |
| GET /{id} | GERENTE, ADMINISTRATIVO | |
| GET /medico/{id}, GET /contratos | GERENTE, ADMINISTRATIVO, MEDICO | % y observaciones enmascarados para contratos ajenos si MEDICO |
| POST /uso, POST /cierre-diario | GERENTE, ADMINISTRATIVO | |

### Consultorios (`/api/consultorios`)
| Endpoint | Rol | Notas |
|---|---|---|
| GET | GERENTE, ADMINISTRATIVO, MEDICO | |
| POST | GERENTE | |
| PUT /{id}/estado | GERENTE, ADMINISTRATIVO | |

### Obras Sociales (`/api/obras-sociales`)
| Endpoint | Rol | Notas |
|---|---|---|
| GET | GERENTE, ADMINISTRATIVO, MEDICO | |
| POST, PUT /{id}, DELETE /{id}, PUT /{id}/reactivar | GERENTE, ADMINISTRATIVO | |

### Especialidades (`/api/especialidades`)
| Endpoint | Rol | Notas |
|---|---|---|
| GET | GERENTE, ADMINISTRATIVO | (MEDICO no listado — a verificar si es intencional) |
| POST | GERENTE | |

### Reportes y Dashboard (`/api/reportes`)
| Endpoint | Rol | Notas |
|---|---|---|
| GET /dashboard, POST /dashboard/recalcular | **GERENTE únicamente** | Coincide con el enunciado (Fase 6) |
| GET /facturacion/medico, /facturacion/obra-social, /consultorios | GERENTE únicamente | El enunciado (Fase 5) esperaba reportes operativos/médicos accesibles también a otros roles — **a verificar**: hoy TODO `/api/reportes/*` es exclusivo de GERENTE, no hay reportes operativos separados para Admin/Médico en este controller |

### Auth (`/api/auth`)
| Endpoint | Rol | Notas |
|---|---|---|
| POST /login | Público | JWT. **No se encontró endpoint de recuperación de contraseña** (forgot-password / reset) — marcar **NO IMPLEMENTADA** salvo que aparezca en otro controller |

## 7. Puntos a verificar en Fase 7 (matriz de permisos) — no son bugs todavía, son hallazgos de reconocimiento
- `PUT /api/historias-clinicas/evoluciones/{id}` permite `GERENTE` aunque `GET /paciente/{id}`
  (lectura) excluye a `GERENTE`. Confirmar si es un bug de superficie de permisos o intencional.
- `GET /api/especialidades` no incluye `MEDICO` — confirmar contra la tabla 1.7 de la propuesta.
- No se encontró controller/endpoint de recuperación de contraseña — Fase 3 del enunciado
  (recuperación con link de 20 min) se marcará **NO IMPLEMENTADA** salvo hallazgo posterior.
- Los reportes "operativos" y "médicos" de la Fase 5 del enunciado (turnos por fecha, ausentes,
  tiempo de espera, pacientes atendidos por médico, especialidades más demandadas) no tienen
  endpoints propios distintos del Dashboard Gerencial — solo existen 4 endpoints bajo
  `/api/reportes`, todos exclusivos de GERENTE. Se marcarán **NO IMPLEMENTADA** o **PARCIAL**
  según corresponda tras revisar `ReporteServiceImpl` en detalle.

## 8. Stack de testing propuesto

- **JUnit 5 + Spring Boot Test** (ya en `pom.xml`) para reglas de negocio y servicios — se
  extiende la suite existente (125 tests) en vez de reemplazarla. Los tests QA nuevos que no
  necesiten persistir van con `@Transactional` (rollback automático de Spring Test).
- **Playwright** (Java o Node — a definir con el usuario; dado que el resto del stack es
  Java/Maven, se sugiere `com.microsoft.playwright:playwright` vía Maven para no introducir un
  segundo gestor de paquetes) para los E2E de UI multi-rol y multi-contexto (tiempo real en
  agenda del médico).
- **GreenMail** (embebido, Java, se integra fácil con Spring Boot Test) como SMTP falso — sujeto
  a que exista funcionalidad de recuperación de contraseña por email; si se confirma "NO
  IMPLEMENTADA" en Fase 3, este punto se marca igual y no se agrega SMTP falso.
- **Apache POI** para verificar los `.xlsx` exportados (Fase 5) — es Java nativo, coherente con
  el resto del stack.

## 9. Pendiente de confirmación del usuario antes de Fase 1

1. ¿Despliegue en Tomcat externo (WAR) o `spring-boot:run` (Tomcat embebido) es aceptable para
   todo el QA? No se encontró configuración de WAR en el repo.
2. Confirmar los 4 hallazgos de la sección 7 antes de clasificarlos como bug/NO IMPLEMENTADA en
   el informe final.
3. Confirmación explícita para avanzar a **Fase 1** (creación de datos QA — la primera
   escritura real en la base), según lo pactado.
