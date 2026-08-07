-- =========================================================
-- Migración: Medico_Prestacion (relación real médico↔prestación)
-- Reemplaza la FK suelta Medicos.ID_Prestacion (1 prestación por médico)
-- por una relación N:M con datos particulares por médico
-- (Duracion_Estimada_Min, Importe_Particular, Tipo).
-- Idempotente: puede correrse varias veces sin efectos duplicados.
-- No asume base vacía: hace backfill de los datos existentes
-- ANTES de eliminar la columna vieja.
-- =========================================================

-- Paso 1: crear la tabla Medico_Prestacion si no existe
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'Medico_Prestacion')
BEGIN
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
END
GO

-- Índice único filtrado: evita duplicar la misma prestación activa para un médico,
-- pero permite reactivarla si una fila anterior fue dada de baja (Visible = 0).
-- Los índices filtrados requieren QUOTED_IDENTIFIER ON en la sesión que los crea.
SET QUOTED_IDENTIFIER ON;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_MedicoPrestacion_Activo')
BEGIN
    CREATE UNIQUE INDEX UX_MedicoPrestacion_Activo
        ON Medico_Prestacion (ID_Medico, ID_Prestacion)
        WHERE Visible = 1;
END
GO

-- Paso 2: backfill de los datos existentes en Medicos.ID_Prestacion,
-- ANTES de tocar la columna vieja.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Medicos') AND name = 'ID_Prestacion')
BEGIN
    INSERT INTO Medico_Prestacion (ID_Medico, ID_Prestacion, Visible)
    SELECT m.ID_Medico, m.ID_Prestacion, 1
    FROM Medicos m
    WHERE m.ID_Prestacion IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM Medico_Prestacion mp
          WHERE mp.ID_Medico = m.ID_Medico
            AND mp.ID_Prestacion = m.ID_Prestacion
            AND mp.Visible = 1
      );
END
GO

-- Paso 3: recién ahora, eliminar la FK y la columna vieja de Medicos.
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Medicos_Prestacion')
BEGIN
    ALTER TABLE Medicos DROP CONSTRAINT FK_Medicos_Prestacion;
END
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Medicos') AND name = 'ID_Prestacion')
BEGIN
    ALTER TABLE Medicos DROP COLUMN ID_Prestacion;
END
GO
