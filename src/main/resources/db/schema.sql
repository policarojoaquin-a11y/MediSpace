-- =========================================================
-- Consultorios Mitre — Esquema de Base de Datos (SQL Server)
-- Etapa 1: Usuarios, Pacientes, Médicos, Especialidades, ObraSocial
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
-- MEDICO_OBRASOCIAL (N:M)
-- =========================
CREATE TABLE Medico_ObraSocial (
    ID_Medico       INT NOT NULL,
    ID_ObraSocial   INT NOT NULL,
    CONSTRAINT PK_Medico_ObraSocial PRIMARY KEY (ID_Medico, ID_ObraSocial),
    CONSTRAINT FK_MOS_Medico FOREIGN KEY (ID_Medico) REFERENCES Medicos(ID_Medico),
    CONSTRAINT FK_MOS_ObraSocial FOREIGN KEY (ID_ObraSocial) REFERENCES ObraSocial(ID_ObraSocial)
);

-- =========================
-- MEDICO_PRESTACION (N:M, con datos particulares por médico)
-- Reemplaza la vieja FK suelta Medicos.ID_Prestacion. Ver
-- docs/migrations/2026-08-05_prestaciones_medicas_join.sql
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
