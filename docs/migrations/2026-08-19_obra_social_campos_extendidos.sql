-- =========================================================
-- Migración: campos extendidos en ObraSocial (sección 4.9 de spec.md
-- / 1.3.9 de la Entrega — Módulo de Obras Sociales).
--
-- Motivo: el módulo pasa de ser un catálogo mínimo (solo Nombre) a un
-- ABM completo con alta, baja lógica, modificación y consulta con
-- filtros. Agrega los campos pedidos por el módulo: código/sigla,
-- si requiere bono de consulta previo y su importe, datos de contacto
-- y observaciones administrativas. Visible ya existía (soft delete).
--
-- Idempotente: puede correrse varias veces sin efectos duplicados.
-- =========================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Codigo_Sigla')
BEGIN
    ALTER TABLE ObraSocial ADD Codigo_Sigla VARCHAR(20) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Requiere_Bono')
BEGIN
    ALTER TABLE ObraSocial ADD Requiere_Bono BIT NOT NULL DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Importe_Bono')
BEGIN
    ALTER TABLE ObraSocial ADD Importe_Bono DECIMAL(10,2) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Datos_Contacto')
BEGIN
    ALTER TABLE ObraSocial ADD Datos_Contacto VARCHAR(150) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ObraSocial') AND name = 'Observaciones')
BEGIN
    ALTER TABLE ObraSocial ADD Observaciones VARCHAR(500) NULL;
END
GO
