-- =========================================================
-- Consultorios Mitre — Esquema de Base de Datos (SQL Server)
-- Basado en la Propuesta Técnica V Final
-- =========================================================

-- =========================
-- USUARIOS
-- =========================
CREATE TABLE Usuarios (
    ID_Usuario      INT IDENTITY(1,1) PRIMARY KEY,
    Email           VARCHAR(255) NOT NULL UNIQUE,
    Password_hash   VARCHAR(255) NOT NULL,
    Rol             VARCHAR(30)  NOT NULL, -- GERENTE / ADMINISTRATIVO / MEDICO (los pacientes no tienen usuario)
    Visible         BIT NOT NULL DEFAULT 1,
    Fecha_Creacion  DATE NOT NULL DEFAULT GETDATE()
);

-- =========================
-- OBRA SOCIAL
-- =========================
CREATE TABLE ObraSocial (
    ID_ObraSocial   INT IDENTITY(1,1) PRIMARY KEY,
    Nombre          VARCHAR(100) NOT NULL,
    Codigo_Sigla    VARCHAR(20) NULL,
    [Plan]          VARCHAR(50) NULL, -- 'Plan' es palabra reservada en SQL Server
    Requiere_Bono   BIT NOT NULL DEFAULT 0,
    Observaciones   VARCHAR(500) NULL,
    Visible         BIT NOT NULL DEFAULT 1
);

-- =========================
-- ESPECIALIDADES
-- =========================
CREATE TABLE Especialidades (
    ID_Especialidad INT IDENTITY(1,1) PRIMARY KEY,
    Nombre          VARCHAR(50) NOT NULL UNIQUE,
    Visible         BIT NOT NULL DEFAULT 1
);

-- =========================
-- PRESTACIONES MEDICAS
-- =========================
CREATE TABLE Prestaciones_Medicas (
    ID_Prestacion   INT IDENTITY(1,1) PRIMARY KEY,
    Nombre          VARCHAR(50)
);

-- =========================
-- PACIENTES
-- =========================
-- Los pacientes NO tienen usuario de acceso al sistema (no inician sesión).
-- Son gestionados exclusivamente por Gerente/Administrativo.
CREATE TABLE Pacientes (
    ID_Paciente         INT IDENTITY(1,1) PRIMARY KEY,
    Nombre              VARCHAR(50) NOT NULL,
    Apellido            VARCHAR(50) NOT NULL,
    DNI                 VARCHAR(20) NOT NULL UNIQUE, -- RN-008: inmutable / RN-009: único incluso inactivo
    Telefono            VARCHAR(20),
    ID_ObraSocial       INT NULL,
    Numero_Credencial   VARCHAR(50),
    Direccion           VARCHAR(150),
    Plan_OS             VARCHAR(50),
    Fecha_Nacimiento    DATE NOT NULL,
    Estado              VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    Visible             BIT NOT NULL DEFAULT 1,
    Fecha_Creacion      DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_Pacientes_ObraSocial FOREIGN KEY (ID_ObraSocial) REFERENCES ObraSocial(ID_ObraSocial)
);

-- =========================
-- MEDICOS
-- =========================
CREATE TABLE Medicos (
    ID_Medico               INT IDENTITY(1,1) PRIMARY KEY,
    ID_Usuario              INT NOT NULL,
    Nombre                  VARCHAR(50) NOT NULL,
    Apellido                VARCHAR(50) NOT NULL,
    Matricula               VARCHAR(50) NOT NULL UNIQUE,
    ID_Especialidad         INT NOT NULL,
    Importe_Consulta        DECIMAL(10,2),
    Estado                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    Visible                 BIT NOT NULL DEFAULT 1,
    Fecha_Creacion          DATETIME NOT NULL DEFAULT GETDATE(),
    Fecha_Inicio_Actividad  DATE NOT NULL,
    CONSTRAINT FK_Medicos_Usuario FOREIGN KEY (ID_Usuario) REFERENCES Usuarios(ID_Usuario),
    CONSTRAINT FK_Medicos_Especialidad FOREIGN KEY (ID_Especialidad) REFERENCES Especialidades(ID_Especialidad)
);

-- =========================
-- CONSULTORIO
-- =========================
CREATE TABLE Consultorio (
    ID_Consultorio      INT IDENTITY(1,1) PRIMARY KEY,
    Numero_Consultorio  VARCHAR(20) NOT NULL UNIQUE,
    Descripcion         TEXT,
    Estado              VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE', -- Disponible/Ocupado/Bloqueado/En mantenimiento/Fuera de servicio
    Equipamiento        TEXT,
    Ubicacion           VARCHAR(100),
    Visible             BIT NOT NULL DEFAULT 1
);

-- =========================
-- AGENDA MEDICO
-- DEPRECADA: reemplazada por Arrendamiento_Modulo (Dia_Semana, Duracion_Turno_Min,
-- Cupo_Maximo_Diario ahora viven ahí). El contrato de arrendamiento es la única
-- fuente de horario del médico y dispara la generación automática de turnos.
-- Se deja la tabla en la base por compatibilidad histórica, pero la app no la usa más.
-- =========================
CREATE TABLE Agenda_Medico (
    ID_Agenda           INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico           INT NOT NULL,
    ID_Consultorio      INT NULL,
    Dia_Semana          VARCHAR(20) NOT NULL,
    Hora_Inicio         TIME NOT NULL,
    Hora_Fin            TIME NOT NULL,
    Duracion_Turno_Min  INT NOT NULL,
    Cupo_Maximo_Diario  INT NOT NULL,
    Visible             BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Agenda_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_Agenda_Consultorio FOREIGN KEY (ID_Consultorio) REFERENCES Consultorio(ID_Consultorio)
);

-- =========================
-- MEDICO_OBRASOCIAL (N:M, con datos particulares por médico)
-- Importe_Coseguro es específico del arreglo de ESE médico con esa
-- obra social (lo decide el médico), no un valor fijo de la obra
-- social. Mismo patrón que Medico_Prestacion: baja lógica vía
-- Visible, índice único filtrado evita duplicar una misma relación
-- activa.
-- =========================
CREATE TABLE Medico_ObraSocial (
    ID_Medico_ObraSocial    INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico               INT NOT NULL,
    ID_ObraSocial           INT NOT NULL,
    Importe_Coseguro        DECIMAL(10,2) NULL,
    Visible                 BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_MOS_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_MOS_ObraSocial FOREIGN KEY (ID_ObraSocial) REFERENCES ObraSocial(ID_ObraSocial)
);
CREATE UNIQUE INDEX UX_MedicoObraSocial_Activo ON Medico_ObraSocial (ID_Medico, ID_ObraSocial) WHERE Visible = 1;

-- =========================
-- MEDICO_PRESTACION (N:M, con datos particulares por médico)
-- Reemplaza la vieja FK suelta Medicos.ID_Prestacion (1 sola prestación por médico).
-- Duracion_Estimada_Min / Importe_Particular / Tipo son específicos de la
-- oferta de ESE médico para esa prestación de catálogo, no del catálogo en sí.
-- Baja lógica vía Visible = 0 (no DELETE físico). Índice único filtrado
-- (ver migración) evita duplicar una misma prestación activa por médico.
-- =========================
CREATE TABLE Medico_Prestacion (
    ID_Medico_Prestacion   INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico              INT NOT NULL,
    ID_Prestacion          INT NOT NULL,
    Duracion_Estimada_Min  INT NULL,
    Importe_Particular     DECIMAL(10,2) NULL,
    Tipo                   VARCHAR(50) NULL,
    Visible                BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_MedicoPrestacion_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_MedicoPrestacion_Prestacion FOREIGN KEY (ID_Prestacion) REFERENCES Prestaciones_Medicas(ID_Prestacion)
);
CREATE UNIQUE INDEX UX_MedicoPrestacion_Activo ON Medico_Prestacion (ID_Medico, ID_Prestacion) WHERE Visible = 1;

-- =========================
-- TURNOS
-- =========================
CREATE TABLE Turnos (
    ID_Turno        INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico       INT NOT NULL,
    ID_Paciente     INT NULL,
    ID_Consultorio  INT NULL,
    Fecha_Hora      DATETIME NOT NULL,
    ID_Prestacion   INT NULL,
    Estado          VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE', -- Disponible/Reservado/En Espera/Atendido/Cancelado/No Asistió
    Fecha_Reserva   DATETIME NULL,
    -- Datos cargados al reservar (sección 4.4). El circuito de Facturación real sigue
    -- arrancando en "Atendido" (sección 4.6) — estos campos son la intención capturada en la
    -- reserva, usada como valor por defecto al generar la Facturación automática.
    Tipo_Consulta                VARCHAR(50) NULL,
    Metodo_Pago_Planificado      VARCHAR(30) NULL,
    Obra_Social_Planificada      VARCHAR(100) NULL,
    Importe_Copago_Planificado   DECIMAL(10,2) NULL,
    Visible         BIT NOT NULL DEFAULT 1,
    Fecha_Creacion  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_Turnos_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_Turnos_Paciente FOREIGN KEY (ID_Paciente) REFERENCES Pacientes(ID_Paciente),
    CONSTRAINT FK_Turnos_Consultorio FOREIGN KEY (ID_Consultorio) REFERENCES Consultorio(ID_Consultorio),
    CONSTRAINT FK_Turnos_Prestacion FOREIGN KEY (ID_Prestacion) REFERENCES Prestaciones_Medicas(ID_Prestacion)
);

-- =========================
-- COBROS
-- =========================
CREATE TABLE Cobros (
    ID_Cobro                INT IDENTITY(1,1) PRIMARY KEY,
    ID_Turno                INT NOT NULL,
    Metodo_Pago              VARCHAR(30) NOT NULL,
    Importe_Total            DECIMAL(10,2) NOT NULL,
    Importe_Cubierto_OS      DECIMAL(10,2),
    Importe_Copago           DECIMAL(10,2),
    Fecha_Cobro              DATETIME NOT NULL DEFAULT GETDATE(),
    Visible                  BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Cobros_Turno FOREIGN KEY (ID_Turno) REFERENCES Turnos(ID_Turno)
);

-- =========================
-- HISTORIA CLINICA (1:1 con Paciente)
-- =========================
CREATE TABLE Historia_Clinica (
    ID_HistoriaClinica  INT IDENTITY(1,1) PRIMARY KEY,
    ID_Paciente         INT NOT NULL UNIQUE,
    Fecha_Creacion      DATETIME NOT NULL DEFAULT GETDATE(),
    Estado              VARCHAR(20) NOT NULL DEFAULT 'ACTIVA', -- Activa/Inactiva
    Visible             BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_HC_Paciente FOREIGN KEY (ID_Paciente) REFERENCES Pacientes(ID_Paciente)
);

-- =========================
-- EVOLUCION CLINICA
-- =========================
CREATE TABLE Evolucion_Clinica (
    ID_Evolucion            INT IDENTITY(1,1) PRIMARY KEY,
    ID_HistoriaClinica      INT NOT NULL,
    ID_Medico               INT NOT NULL,
    ID_Turno                INT NULL,
    ID_Prestacion           INT NULL,
    Fecha_Hora              DATETIME NOT NULL DEFAULT GETDATE(),
    Motivo_Consulta         TEXT,
    Diagnostico             TEXT,
    Tratamiento             TEXT,
    Indicaciones            TEXT,
    Estudios_Solicitados    TEXT,
    Observaciones           TEXT,
    Visible                 BIT NOT NULL DEFAULT 1, -- se usa como "no anulada"; anulación real vía Estado si se agrega
    CONSTRAINT FK_Evolucion_HC FOREIGN KEY (ID_HistoriaClinica) REFERENCES Historia_Clinica(ID_HistoriaClinica),
    CONSTRAINT FK_Evolucion_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_Evolucion_Turno FOREIGN KEY (ID_Turno) REFERENCES Turnos(ID_Turno),
    CONSTRAINT FK_Evolucion_Prestacion FOREIGN KEY (ID_Prestacion) REFERENCES Prestaciones_Medicas(ID_Prestacion)
);

-- =========================
-- ADJUNTOS HISTORIA CLINICA
-- =========================
CREATE TABLE Adjuntos_HistoriaClinica (
    ID_Adjunto      INT IDENTITY(1,1) PRIMARY KEY,
    ID_Evolucion    INT NOT NULL,
    Nombre_Archivo  VARCHAR(100) NOT NULL,
    Ruta_Archivo    VARCHAR(255) NOT NULL,
    Tipo_Archivo    VARCHAR(30),
    Fecha_Carga     DATETIME NOT NULL DEFAULT GETDATE(),
    Visible         BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Adjuntos_Evolucion FOREIGN KEY (ID_Evolucion) REFERENCES Evolucion_Clinica(ID_Evolucion)
);

-- =========================
-- ARRENDAMIENTO MODULO
-- =========================
CREATE TABLE Arrendamiento_Modulo (
    ID_Arrendamiento        INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico               INT NOT NULL,
    ID_Consultorio          INT NOT NULL,
    Fecha_Inicio            DATE NOT NULL,
    Fecha_Fin               DATE NULL,
    Dia_Semana              VARCHAR(20) NOT NULL, -- LUNES..DOMINGO
    Hora_Inicio             TIME NOT NULL,
    Hora_Fin                TIME NOT NULL,
    Duracion_Turno_Min      INT NOT NULL DEFAULT 30,
    Cupo_Maximo_Diario      INT NOT NULL DEFAULT 8,
    Porcentaje_Consultorio  DECIMAL(5,2) NOT NULL,
    Porcentaje_Medico       DECIMAL(5,2) NOT NULL,
    Estado                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    Observaciones           TEXT,
    Visible                 BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Arrendamiento_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_Arrendamiento_Consultorio FOREIGN KEY (ID_Consultorio) REFERENCES Consultorio(ID_Consultorio)
);

-- =========================
-- FACTURACION
-- =========================
CREATE TABLE Facturacion (
    ID_Facturacion          INT IDENTITY(1,1) PRIMARY KEY,
    ID_Turno                INT NOT NULL,
    ID_Paciente             INT NOT NULL,
    ID_Medico               INT NOT NULL,
    ID_Arrendamiento        INT NULL,
    Fecha_Facturacion       DATETIME NOT NULL DEFAULT GETDATE(),
    Tipo_Consulta           VARCHAR(50) NOT NULL,
    Metodo_Pago             VARCHAR(30) NOT NULL,
    Obra_Social             VARCHAR(100),
    Importe_Total           DECIMAL(10,2) NOT NULL,
    Importe_Copago          DECIMAL(10,2),
    Porcentaje_Consultorio  DECIMAL(5,2) NOT NULL,
    Porcentaje_Medico       DECIMAL(5,2) NOT NULL,
    Estado_Pago             VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', -- Pendiente/Pagado/Parcial/Anulado/Reintegrado
    Observaciones           TEXT,
    Visible                 BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Facturacion_Turno FOREIGN KEY (ID_Turno) REFERENCES Turnos(ID_Turno),
    CONSTRAINT FK_Facturacion_Paciente FOREIGN KEY (ID_Paciente) REFERENCES Pacientes(ID_Paciente),
    CONSTRAINT FK_Facturacion_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_Facturacion_Arrendamiento FOREIGN KEY (ID_Arrendamiento) REFERENCES Arrendamiento_Modulo(ID_Arrendamiento)
);

-- =========================
-- LIQUIDACION MEDICA
-- =========================
CREATE TABLE Liquidacion_Medica (
    ID_Liquidacion      INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico           INT NOT NULL,
    Fecha_Desde         DATE NOT NULL,
    Fecha_Hasta         DATE NOT NULL,
    Total_Facturado     DECIMAL(10,2) NOT NULL,
    Total_Consultorio   DECIMAL(10,2) NOT NULL,
    Total_Medico        DECIMAL(10,2) NOT NULL,
    Fecha_Generacion    DATETIME NOT NULL DEFAULT GETDATE(),
    Estado              VARCHAR(20) NOT NULL DEFAULT 'EMITIDA', -- Emitida/Anulada
    Observaciones       TEXT,
    Visible             BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Liquidacion_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico)
);

-- =========================
-- PAGO FACTURACION
-- =========================
CREATE TABLE Pago_Facturacion (
    ID_Pago              INT IDENTITY(1,1) PRIMARY KEY,
    ID_Facturacion       INT NOT NULL,
    Metodo_Pago          VARCHAR(30) NOT NULL,
    Importe              DECIMAL(10,2) NOT NULL,
    Fecha_Pago           DATETIME NOT NULL DEFAULT GETDATE(),
    Estado               VARCHAR(20) NOT NULL,
    Numero_Comprobante   VARCHAR(100),
    Observaciones        TEXT,
    Visible              BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Pago_Facturacion FOREIGN KEY (ID_Facturacion) REFERENCES Facturacion(ID_Facturacion)
);

-- =========================
-- USO CONSULTORIO
-- =========================
CREATE TABLE Uso_Consultorio (
    ID_Uso                  INT IDENTITY(1,1) PRIMARY KEY,
    ID_Consultorio          INT NOT NULL,
    ID_Medico               INT NOT NULL,
    Fecha                   DATE NOT NULL,
    Hora_Inicio             TIME NOT NULL,
    Hora_Fin                TIME NOT NULL,
    Cantidad_Pacientes      INT,
    Facturacion_Generada    DECIMAL(10,2),
    Visible                 BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Uso_Consultorio FOREIGN KEY (ID_Consultorio) REFERENCES Consultorio(ID_Consultorio),
    CONSTRAINT FK_Uso_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico)
);

-- =========================
-- CIERRE DIARIO
-- =========================
CREATE TABLE Cierre_Diario (
    ID_Cierre               INT IDENTITY(1,1) PRIMARY KEY,
    ID_Medico                INT NOT NULL,
    ID_Consultorio            INT NOT NULL,
    ID_Arrendamiento         INT NULL,
    Fecha                     DATE NOT NULL,
    Total_Facturado_Dia       DECIMAL(10,2) NOT NULL,
    Importe_Consultorio       DECIMAL(10,2) NOT NULL,
    Importe_Medico            DECIMAL(10,2) NOT NULL,
    Cantidad_Turnos           INT NOT NULL,
    Estado                    VARCHAR(20) NOT NULL,
    Observaciones             TEXT,
    Fecha_Registro            DATETIME NOT NULL DEFAULT GETDATE(),
    Visible                   BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Cierre_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_Cierre_Consultorio FOREIGN KEY (ID_Consultorio) REFERENCES Consultorio(ID_Consultorio),
    CONSTRAINT FK_Cierre_Arrendamiento FOREIGN KEY (ID_Arrendamiento) REFERENCES Arrendamiento_Modulo(ID_Arrendamiento)
);

-- =========================
-- REPORTE GENERADO
-- =========================
CREATE TABLE Reporte_Generado (
    ID_Reporte              INT IDENTITY(1,1) PRIMARY KEY,
    ID_Usuario              INT NOT NULL,
    Tipo_Reporte            VARCHAR(50) NOT NULL,
    Fecha_Generacion        DATETIME NOT NULL DEFAULT GETDATE(),
    Parametros_Utilizados   TEXT,
    Formato_Exportacion     VARCHAR(20),
    Ruta_Archivo            VARCHAR(255),
    Visible                 BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_Reporte_Usuario FOREIGN KEY (ID_Usuario) REFERENCES Usuarios(ID_Usuario)
);

-- =========================
-- DASHBOARD GERENCIAL
-- =========================
CREATE TABLE Dashboard_Gerencial (
    ID_Indicador        INT IDENTITY(1,1) PRIMARY KEY,
    Nombre_Indicador    VARCHAR(100) NOT NULL,
    Categoria           VARCHAR(50) NOT NULL,
    Valor               DECIMAL(10,2) NOT NULL,
    Fecha_Calculo       DATETIME NOT NULL DEFAULT GETDATE(),
    Periodo             VARCHAR(20) NOT NULL,
    Visible             BIT NOT NULL DEFAULT 1
);

-- =========================================================
-- Índices recomendados (además de PK/UNIQUE ya definidos)
-- =========================================================
CREATE INDEX IX_Turnos_Medico_Fecha ON Turnos(ID_Medico, Fecha_Hora);
CREATE INDEX IX_Turnos_Paciente ON Turnos(ID_Paciente);
CREATE INDEX IX_Turnos_Estado ON Turnos(Estado);
CREATE INDEX IX_Facturacion_Medico ON Facturacion(ID_Medico);
CREATE INDEX IX_Facturacion_Paciente ON Facturacion(ID_Paciente);
CREATE INDEX IX_Evolucion_HC ON Evolucion_Clinica(ID_HistoriaClinica);
CREATE INDEX IX_Arrendamiento_Consultorio_Fecha ON Arrendamiento_Modulo(ID_Consultorio, Fecha_Inicio, Fecha_Fin);
