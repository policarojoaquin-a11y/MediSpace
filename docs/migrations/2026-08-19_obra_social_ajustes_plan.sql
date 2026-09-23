-- =========================================================
-- Migración: ajustes en ObraSocial — se quita Importe_Bono y
-- Datos_Contacto (agregados hoy mismo en 2026-08-19_obra_social_
-- campos_extendidos.sql, corregidos por el usuario antes de que el
-- módulo llegara a producción) y se agrega Plan.
--
-- Motivo: el coseguro que paga el paciente no es un dato fijo de la
-- obra social — lo decide cada médico según el arreglo que tenga con
-- esa obra social (ver 2026-08-19_medico_obra_social_coseguro.sql,
-- que agrega Importe_Coseguro a Medico_ObraSocial). Importe_Bono deja
-- de tener sentido acá. Datos_Contacto se descarta por decisión de
-- producto (no se usa). Se agrega Plan (ej: "210", "310") como dato
-- simple de la obra social.
--
-- Nota: Plan es palabra reservada en SQL Server (PLAN), por eso va
-- entre corchetes.
--
-- Idempotente: puede correrse varias veces sin efectos duplicados.
-- =========================================================

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Importe_Bono')
BEGIN
    ALTER TABLE ObraSocial DROP COLUMN Importe_Bono;
END
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Datos_Contacto')
BEGIN
    ALTER TABLE ObraSocial DROP COLUMN Datos_Contacto;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Plan')
BEGIN
    ALTER TABLE ObraSocial ADD [Plan] VARCHAR(50) NULL;
END
GO
