-- =========================================================
-- Migración: columnas "planificadas" en Turnos (Tipo_Consulta,
-- Metodo_Pago_Planificado, Obra_Social_Planificada,
-- Importe_Copago_Planificado).
--
-- Motivo: el formulario de reserva de turno (sección 4.4) ya cargaba estos
-- datos, pero ReservarTurnoDTO no los declaraba y Jackson los descartaba
-- silenciosamente (bug corregido — ver docs/AUDITORIA_MEDISPACE.md, ítem
-- bloqueante #7). Estas columnas permiten persistirlos como "intención" de
-- la reserva; el circuito de Facturación real sigue arrancando recién en
-- "Atendido" (sección 4.6) — se usan como valor por defecto al generar la
-- Facturación automática, no reemplazan ese flujo.
--
-- Idempotente: puede correrse varias veces sin efectos duplicados.
-- =========================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Turnos') AND name = 'Tipo_Consulta')
BEGIN
    ALTER TABLE Turnos ADD Tipo_Consulta VARCHAR(50) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Turnos') AND name = 'Metodo_Pago_Planificado')
BEGIN
    ALTER TABLE Turnos ADD Metodo_Pago_Planificado VARCHAR(30) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Turnos') AND name = 'Obra_Social_Planificada')
BEGIN
    ALTER TABLE Turnos ADD Obra_Social_Planificada VARCHAR(100) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Turnos') AND name = 'Importe_Copago_Planificado')
BEGIN
    ALTER TABLE Turnos ADD Importe_Copago_Planificado DECIMAL(10,2) NULL;
END
GO
