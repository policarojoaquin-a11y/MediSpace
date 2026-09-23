-- =========================================================
-- MediSpace QA — Limpieza de datos QA (Fase 9)
-- =========================================================
-- Borra FISICAMENTE solo los registros QA, respetando el orden de FK:
-- Adjuntos -> Evoluciones -> Pagos -> Cobros -> Facturacion -> Liquidaciones -> Cierres ->
-- Uso_Consultorio -> Turnos -> Arrendamientos -> Medico_ObraSocial/Prestacion ->
-- Historia_Clinica -> Pacientes -> Medicos -> Usuarios -> Consultorios -> ObraSocial ->
-- Especialidades -> Reporte_Generado de usuarios QA.
--
-- Cada DELETE lleva el filtro QA explicito (nunca un DELETE sin WHERE). Todo corre dentro de
-- una unica transaccion; antes del COMMIT se verifican los conteos esperados. Si algo no
-- cuadra, ROLLBACK y se aborta — no se hace commit parcial.
--
-- Requisito previo: correr este script SOLO despues de que el usuario confirme la limpieza
-- (regla 2 del protocolo). Antes de esto, ejecutar por separado el borrado de los archivos
-- adjuntos QA en disco (ver rutas devueltas por la consulta de Adjuntos_HistoriaClinica de
-- los medicos/pacientes QA, ya que Ruta_Archivo no se puede borrar con T-SQL puro).
--
-- Despues de este script: recalcular Dashboard_Gerencial si corresponde y volver a correr
-- qa/snapshot_control.sql para comparar contra qa/snapshot_control_pre_qa_<fecha>.txt.

SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Modo dry-run: corre TODO el script (borra dentro de la transacción, calcula y verifica los
-- conteos) pero termina en ROLLBACK en vez de COMMIT — no queda nada borrado de verdad, pero
-- confirma que los conteos esperados cuadran contra los borrados reales antes de animarse al
-- commit. Poner @DryRun = 0 para la corrida real.
DECLARE @DryRun BIT = 1;

BEGIN TRANSACTION QaCleanup;

BEGIN TRY

    -- IDs QA fijos calculados por rango (evita listas de IDs quebradizas)
    DECLARE @QaMedicoIds TABLE (ID_Medico INT PRIMARY KEY);
    INSERT INTO @QaMedicoIds SELECT ID_Medico FROM Medicos WHERE Matricula LIKE 'QA-%';

    DECLARE @QaConsultorioIds TABLE (ID_Consultorio INT PRIMARY KEY);
    INSERT INTO @QaConsultorioIds SELECT ID_Consultorio FROM Consultorio WHERE Numero_Consultorio IN ('QA-1','QA-2','QA-3');

    DECLARE @QaPacienteIds TABLE (ID_Paciente INT PRIMARY KEY);
    INSERT INTO @QaPacienteIds SELECT ID_Paciente FROM Pacientes WHERE Nombre LIKE 'QA[_]%' OR TRY_CAST(DNI AS INT) BETWEEN 99000001 AND 99000999;

    DECLARE @QaObraSocialIds TABLE (ID_ObraSocial INT PRIMARY KEY);
    INSERT INTO @QaObraSocialIds SELECT ID_ObraSocial FROM ObraSocial WHERE Nombre LIKE 'QA[_]%';

    DECLARE @QaEspecialidadIds TABLE (ID_Especialidad INT PRIMARY KEY);
    INSERT INTO @QaEspecialidadIds SELECT ID_Especialidad FROM Especialidades WHERE Nombre LIKE 'QA[_]%';

    DECLARE @QaUsuarioIds TABLE (ID_Usuario INT PRIMARY KEY);
    INSERT INTO @QaUsuarioIds SELECT ID_Usuario FROM Usuarios WHERE Email LIKE '%@qa.test';

    DECLARE @QaTurnoIds TABLE (ID_Turno INT PRIMARY KEY);
    INSERT INTO @QaTurnoIds
    SELECT ID_Turno FROM Turnos
    WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds)
       OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds)
       OR ID_Paciente IN (SELECT ID_Paciente FROM @QaPacienteIds);

    DECLARE @QaFacturacionIds TABLE (ID_Facturacion INT PRIMARY KEY);
    INSERT INTO @QaFacturacionIds
    SELECT ID_Facturacion FROM Facturacion
    WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds)
       OR ID_Turno IN (SELECT ID_Turno FROM @QaTurnoIds)
       OR ID_Paciente IN (SELECT ID_Paciente FROM @QaPacienteIds);

    DECLARE @QaHistoriaIds TABLE (ID_HistoriaClinica INT PRIMARY KEY);
    INSERT INTO @QaHistoriaIds
    SELECT ID_HistoriaClinica FROM Historia_Clinica WHERE ID_Paciente IN (SELECT ID_Paciente FROM @QaPacienteIds);

    DECLARE @QaEvolucionIds TABLE (ID_Evolucion INT PRIMARY KEY);
    INSERT INTO @QaEvolucionIds
    SELECT ID_Evolucion FROM Evolucion_Clinica
    WHERE ID_HistoriaClinica IN (SELECT ID_HistoriaClinica FROM @QaHistoriaIds)
       OR ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);

    DECLARE @ExpectedAdjuntos INT, @ExpectedEvoluciones INT, @ExpectedPagos INT, @ExpectedCobros INT,
            @ExpectedFacturacion INT, @ExpectedLiquidaciones INT, @ExpectedCierres INT,
            @ExpectedUso INT, @ExpectedTurnos INT, @ExpectedArrendamientos INT,
            @ExpectedMedObraSocial INT, @ExpectedMedPrestacion INT, @ExpectedHistoria INT,
            @ExpectedPacientes INT, @ExpectedMedicos INT, @ExpectedUsuarios INT,
            @ExpectedConsultorios INT, @ExpectedObraSocial INT, @ExpectedEspecialidades INT,
            @ExpectedReportes INT;

    SELECT @ExpectedAdjuntos = COUNT(*) FROM Adjuntos_HistoriaClinica WHERE ID_Evolucion IN (SELECT ID_Evolucion FROM @QaEvolucionIds);
    SELECT @ExpectedEvoluciones = COUNT(*) FROM @QaEvolucionIds;
    SELECT @ExpectedPagos = COUNT(*) FROM Pago_Facturacion WHERE ID_Facturacion IN (SELECT ID_Facturacion FROM @QaFacturacionIds);
    SELECT @ExpectedCobros = COUNT(*) FROM Cobros WHERE ID_Turno IN (SELECT ID_Turno FROM @QaTurnoIds);
    SELECT @ExpectedFacturacion = COUNT(*) FROM @QaFacturacionIds;
    SELECT @ExpectedLiquidaciones = COUNT(*) FROM Liquidacion_Medica WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);
    SELECT @ExpectedCierres = COUNT(*) FROM Cierre_Diario WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    SELECT @ExpectedUso = COUNT(*) FROM Uso_Consultorio WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    SELECT @ExpectedTurnos = COUNT(*) FROM @QaTurnoIds;
    SELECT @ExpectedArrendamientos = COUNT(*) FROM Arrendamiento_Modulo WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    SELECT @ExpectedMedObraSocial = COUNT(*) FROM Medico_ObraSocial WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_ObraSocial IN (SELECT ID_ObraSocial FROM @QaObraSocialIds);
    SELECT @ExpectedMedPrestacion = COUNT(*) FROM Medico_Prestacion WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);
    SELECT @ExpectedHistoria = COUNT(*) FROM @QaHistoriaIds;
    SELECT @ExpectedPacientes = COUNT(*) FROM @QaPacienteIds;
    SELECT @ExpectedMedicos = COUNT(*) FROM @QaMedicoIds;
    -- BUG corregido (2026-09-23): antes sumaba COUNT(@QaUsuarioIds) + COUNT(usuarios de medicos
    -- QA) como dos consultas separadas — pero todo medico QA YA tiene email @qa.test, o sea que
    -- ya está incluido en @QaUsuarioIds. Sumar los dos conteos contaba cada usuario de médico
    -- DOS VECES, @ExpectedUsuarios quedaba mayor al @@ROWCOUNT real del DELETE, y el THROW
    -- abortaba la limpieza entera. Se arma un conjunto único (UNION, no UNION ALL) de IDs y se
    -- cuenta una sola vez.
    DECLARE @QaUsuarioIdsCompleto TABLE (ID_Usuario INT PRIMARY KEY);
    INSERT INTO @QaUsuarioIdsCompleto
    SELECT ID_Usuario FROM @QaUsuarioIds
    UNION
    SELECT u.ID_Usuario FROM Usuarios u JOIN Medicos m ON m.ID_Usuario = u.ID_Usuario WHERE m.ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);
    SELECT @ExpectedUsuarios = COUNT(*) FROM @QaUsuarioIdsCompleto;
    SELECT @ExpectedConsultorios = COUNT(*) FROM @QaConsultorioIds;
    SELECT @ExpectedObraSocial = COUNT(*) FROM @QaObraSocialIds;
    SELECT @ExpectedEspecialidades = COUNT(*) FROM @QaEspecialidadIds;
    -- @ExpectedReportes: el OR de abajo NO duplica (un COUNT(*) con OR en el WHERE cuenta cada
    -- fila calificada una sola vez, a diferencia del bug de @ExpectedUsuarios que sumaba dos
    -- COUNT(*) por separado) — pero el segundo término es redundante: todo usuario de médico QA
    -- ya tiene email @qa.test y ya está en @QaUsuarioIds. Se deja solo la condición necesaria.
    SELECT @ExpectedReportes = COUNT(*) FROM Reporte_Generado WHERE ID_Usuario IN (SELECT ID_Usuario FROM @QaUsuarioIdsCompleto);

    PRINT 'Conteos esperados a borrar:';
    PRINT '  Adjuntos=' + CAST(@ExpectedAdjuntos AS VARCHAR) + ' Evoluciones=' + CAST(@ExpectedEvoluciones AS VARCHAR)
        + ' Pagos=' + CAST(@ExpectedPagos AS VARCHAR) + ' Cobros=' + CAST(@ExpectedCobros AS VARCHAR)
        + ' Facturacion=' + CAST(@ExpectedFacturacion AS VARCHAR) + ' Liquidaciones=' + CAST(@ExpectedLiquidaciones AS VARCHAR)
        + ' Cierres=' + CAST(@ExpectedCierres AS VARCHAR) + ' Uso=' + CAST(@ExpectedUso AS VARCHAR)
        + ' Turnos=' + CAST(@ExpectedTurnos AS VARCHAR) + ' Arrendamientos=' + CAST(@ExpectedArrendamientos AS VARCHAR)
        + ' MedObraSocial=' + CAST(@ExpectedMedObraSocial AS VARCHAR) + ' MedPrestacion=' + CAST(@ExpectedMedPrestacion AS VARCHAR)
        + ' Historia=' + CAST(@ExpectedHistoria AS VARCHAR) + ' Pacientes=' + CAST(@ExpectedPacientes AS VARCHAR)
        + ' Medicos=' + CAST(@ExpectedMedicos AS VARCHAR) + ' Usuarios=' + CAST(@ExpectedUsuarios AS VARCHAR)
        + ' Consultorios=' + CAST(@ExpectedConsultorios AS VARCHAR) + ' ObraSocial=' + CAST(@ExpectedObraSocial AS VARCHAR)
        + ' Especialidades=' + CAST(@ExpectedEspecialidades AS VARCHAR) + ' Reportes=' + CAST(@ExpectedReportes AS VARCHAR);

    -- 1) Adjuntos
    DELETE FROM Adjuntos_HistoriaClinica WHERE ID_Evolucion IN (SELECT ID_Evolucion FROM @QaEvolucionIds);
    IF @@ROWCOUNT <> @ExpectedAdjuntos THROW 51000, 'Adjuntos: conteo borrado no coincide', 1;

    -- 2) Evoluciones
    DELETE FROM Evolucion_Clinica WHERE ID_Evolucion IN (SELECT ID_Evolucion FROM @QaEvolucionIds);
    IF @@ROWCOUNT <> @ExpectedEvoluciones THROW 51000, 'Evoluciones: conteo borrado no coincide', 1;

    -- 3) Pagos
    DELETE FROM Pago_Facturacion WHERE ID_Facturacion IN (SELECT ID_Facturacion FROM @QaFacturacionIds);
    IF @@ROWCOUNT <> @ExpectedPagos THROW 51000, 'Pagos: conteo borrado no coincide', 1;

    -- 4) Cobros
    DELETE FROM Cobros WHERE ID_Turno IN (SELECT ID_Turno FROM @QaTurnoIds);
    IF @@ROWCOUNT <> @ExpectedCobros THROW 51000, 'Cobros: conteo borrado no coincide', 1;

    -- 5) Facturacion
    DELETE FROM Facturacion WHERE ID_Facturacion IN (SELECT ID_Facturacion FROM @QaFacturacionIds);
    IF @@ROWCOUNT <> @ExpectedFacturacion THROW 51000, 'Facturacion: conteo borrado no coincide', 1;

    -- 6) Liquidaciones
    DELETE FROM Liquidacion_Medica WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);
    IF @@ROWCOUNT <> @ExpectedLiquidaciones THROW 51000, 'Liquidaciones: conteo borrado no coincide', 1;

    -- 7) Cierres
    DELETE FROM Cierre_Diario WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    IF @@ROWCOUNT <> @ExpectedCierres THROW 51000, 'Cierres: conteo borrado no coincide', 1;

    -- 8) Uso_Consultorio
    DELETE FROM Uso_Consultorio WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    IF @@ROWCOUNT <> @ExpectedUso THROW 51000, 'Uso_Consultorio: conteo borrado no coincide', 1;

    -- 9) Turnos
    DELETE FROM Turnos WHERE ID_Turno IN (SELECT ID_Turno FROM @QaTurnoIds);
    IF @@ROWCOUNT <> @ExpectedTurnos THROW 51000, 'Turnos: conteo borrado no coincide', 1;

    -- 10) Arrendamientos
    DELETE FROM Arrendamiento_Modulo WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    IF @@ROWCOUNT <> @ExpectedArrendamientos THROW 51000, 'Arrendamientos: conteo borrado no coincide', 1;

    -- 11) Medico_ObraSocial / Medico_Prestacion
    DELETE FROM Medico_ObraSocial WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds) OR ID_ObraSocial IN (SELECT ID_ObraSocial FROM @QaObraSocialIds);
    IF @@ROWCOUNT <> @ExpectedMedObraSocial THROW 51000, 'Medico_ObraSocial: conteo borrado no coincide', 1;

    DELETE FROM Medico_Prestacion WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);
    IF @@ROWCOUNT <> @ExpectedMedPrestacion THROW 51000, 'Medico_Prestacion: conteo borrado no coincide', 1;

    -- 12) Historia Clinica
    DELETE FROM Historia_Clinica WHERE ID_HistoriaClinica IN (SELECT ID_HistoriaClinica FROM @QaHistoriaIds);
    IF @@ROWCOUNT <> @ExpectedHistoria THROW 51000, 'Historia_Clinica: conteo borrado no coincide', 1;

    -- 13) Pacientes
    DELETE FROM Pacientes WHERE ID_Paciente IN (SELECT ID_Paciente FROM @QaPacienteIds);
    IF @@ROWCOUNT <> @ExpectedPacientes THROW 51000, 'Pacientes: conteo borrado no coincide', 1;

    -- 14) Medicos (antes de borrar hay que soltar el usuario asociado, pero el usuario se borra despues)
    DELETE FROM Medicos WHERE ID_Medico IN (SELECT ID_Medico FROM @QaMedicoIds);
    IF @@ROWCOUNT <> @ExpectedMedicos THROW 51000, 'Medicos: conteo borrado no coincide', 1;

    -- 15) Reporte_Generado de usuarios QA — DEBE ir antes de borrar Usuarios (FK Reporte_Generado.ID_Usuario
    -- -> Usuarios.ID_Usuario). El enunciado lista "Reporte_Generado" al final del orden lógico de módulos,
    -- pero físicamente hay que soltar esta FK antes de poder borrar el Usuario que referencia.
    DELETE FROM Reporte_Generado WHERE ID_Usuario IN (SELECT ID_Usuario FROM @QaUsuarioIds);
    IF @@ROWCOUNT <> @ExpectedReportes THROW 51000, 'Reporte_Generado: conteo borrado no coincide', 1;

    -- 16) Usuarios (los @qa.test explicitos + los usuarios de los medicos QA recien borrados)
    DELETE FROM Usuarios WHERE Email LIKE '%@qa.test';
    IF @@ROWCOUNT <> @ExpectedUsuarios THROW 51000, 'Usuarios: conteo borrado no coincide', 1;

    -- 17) Consultorios
    DELETE FROM Consultorio WHERE ID_Consultorio IN (SELECT ID_Consultorio FROM @QaConsultorioIds);
    IF @@ROWCOUNT <> @ExpectedConsultorios THROW 51000, 'Consultorio: conteo borrado no coincide', 1;

    -- 18) ObraSocial
    DELETE FROM ObraSocial WHERE ID_ObraSocial IN (SELECT ID_ObraSocial FROM @QaObraSocialIds);
    IF @@ROWCOUNT <> @ExpectedObraSocial THROW 51000, 'ObraSocial: conteo borrado no coincide', 1;

    -- 19) Especialidades
    DELETE FROM Especialidades WHERE ID_Especialidad IN (SELECT ID_Especialidad FROM @QaEspecialidadIds);
    IF @@ROWCOUNT <> @ExpectedEspecialidades THROW 51000, 'Especialidades: conteo borrado no coincide', 1;

    -- 20) Prestaciones_Medicas QA (si se hubieran creado; el plan de Fase 1 reusó las existentes)
    DELETE FROM Prestaciones_Medicas WHERE Nombre LIKE 'QA[_]%';

    IF @DryRun = 1
    BEGIN
        ROLLBACK TRANSACTION QaCleanup;
        PRINT 'DRY-RUN OK — todos los conteos esperados coincidieron con los borrados reales. ROLLBACK (nada quedó borrado). Poner @DryRun = 0 para la corrida real.';
    END
    ELSE
    BEGIN
        COMMIT TRANSACTION QaCleanup;
        PRINT 'QA cleanup OK — commit realizado.';
    END

END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION QaCleanup;
    PRINT 'QA cleanup FALLÓ — rollback realizado. Error: ' + ERROR_MESSAGE();
    THROW;
END CATCH;
