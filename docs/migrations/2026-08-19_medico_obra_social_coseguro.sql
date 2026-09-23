-- =========================================================
-- Migración: Medico_ObraSocial pasa de tabla puente simple (PK
-- compuesta ID_Medico+ID_ObraSocial, sin datos propios) a relación
-- con datos propios — mismo patrón ya usado en Medico_Prestacion.
--
-- Motivo: cada médico cobra un coseguro distinto según el arreglo
-- que tenga con cada obra social; lo decide el médico, no es un
-- valor fijo de la obra social ni del médico en general. Se agrega
-- Importe_Coseguro por par médico–obra social, con baja lógica
-- (Visible) igual que el resto de las relaciones N:M con datos
-- propios del sistema.
--
-- Cada paso va en su propio batch (separado por GO): CREATE INDEX
-- con predicado filtrado sobre una columna agregada en el mismo lote
-- falla en SQL Server con "invalid column name" por resolución de
-- nombres — necesita que el ALTER TABLE anterior ya haya committeado.
--
-- Idempotente: puede correrse varias veces sin efectos duplicados.
-- =========================================================

IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'PK_Medico_ObraSocial' AND parent_object_id = OBJECT_ID('Medico_ObraSocial'))
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Medico_ObraSocial') AND name = 'ID_Medico_ObraSocial')
BEGIN
    ALTER TABLE Medico_ObraSocial DROP CONSTRAINT PK_Medico_ObraSocial;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Medico_ObraSocial') AND name = 'ID_Medico_ObraSocial')
BEGIN
    ALTER TABLE Medico_ObraSocial ADD ID_Medico_ObraSocial INT IDENTITY(1,1) NOT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'PK_Medico_ObraSocial' AND parent_object_id = OBJECT_ID('Medico_ObraSocial'))
BEGIN
    ALTER TABLE Medico_ObraSocial ADD CONSTRAINT PK_Medico_ObraSocial PRIMARY KEY (ID_Medico_ObraSocial);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Medico_ObraSocial') AND name = 'Importe_Coseguro')
BEGIN
    ALTER TABLE Medico_ObraSocial ADD Importe_Coseguro DECIMAL(10,2) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Medico_ObraSocial') AND name = 'Visible')
BEGIN
    ALTER TABLE Medico_ObraSocial ADD Visible BIT NOT NULL DEFAULT 1;
END
GO

-- Los índices filtrados (WHERE ...) requieren QUOTED_IDENTIFIER ON en la sesión que los crea;
-- sqlcmd no lo trae en ON por defecto.
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_MedicoObraSocial_Activo' AND object_id = OBJECT_ID('Medico_ObraSocial'))
BEGIN
    CREATE UNIQUE INDEX UX_MedicoObraSocial_Activo ON Medico_ObraSocial (ID_Medico, ID_ObraSocial) WHERE Visible = 1;
END
GO
