-- =========================================================
-- MediSpace QA — Snapshot de control (Regla 5 del protocolo de seguridad QA)
-- =========================================================
-- Este script se ejecuta DOS VECES:
--   1) Fase 0, ANTES de crear ningun dato QA -> baseline (snapshot_control_pre_qa_<fecha>.txt)
--   2) Fase 9, DESPUES de correr qa_cleanup.sql -> debe dar EXACTAMENTE el mismo resultado
-- Cualquier diferencia en Chk_Main/Chk_Text/RowCount_ para una tabla es un INCIDENTE CRITICO:
-- significa que la limpieza QA toco (o el sistema modifico) datos reales.
--
-- Cada SELECT filtra explicitamente los rangos QA definidos en el protocolo:
--   Usuarios/Reporte_Generado: Email LIKE '%@qa.test'
--   Pacientes: Nombre LIKE 'QA_%' o DNI entre 99000001-99000999
--   Medicos y todo lo que cuelga de un medico QA: Matricula LIKE 'QA-%'
--   Consultorio y todo lo que cuelga de un consultorio QA: Numero_Consultorio IN ('QA-1','QA-2','QA-3')
--   ObraSocial: Nombre LIKE 'QA_%'
--   Especialidades: Nombre LIKE 'QA_%'
--   Prestaciones_Medicas: Nombre LIKE 'QA_%' (si se crean; el plan prioriza reusar existentes)
--
-- BINARY_CHECKSUM no admite columnas TEXT/NTEXT/IMAGE/XML como argumento explicito, por eso las
-- tablas con columnas TEXT separan Chk_Main (columnas simples) de Chk_Text (columnas TEXT, via
-- CHECKSUM(CAST(... AS NVARCHAR(MAX)))). Comparar ambas columnas en la re-ejecucion.
--
-- NOTA (hallazgo Fase 0): docs/schema.sql esta desactualizado para Dashboard_Gerencial — la
-- tabla viva tiene una estructura distinta (columnas ID_Dashboard, Fecha, Total_Turnos_Dia,
-- Turnos_Atendidos, Turnos_Cancelados, Turnos_No_Asistio, Turnos_Disponibles,
-- Facturacion_Total_Dia, Cobros_Pendientes, Nuevos_Pacientes, Fecha_Actualizacion, Visible).
-- Este script usa las columnas REALES, verificadas contra INFORMATION_SCHEMA.COLUMNS el 2026-09-23.

SET NOCOUNT ON;

SELECT 'Usuarios' AS TableName,
    COUNT(*) AS RowCount_,
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Usuario, Email, Password_hash, Rol, Visible, Fecha_Creacion)) AS Chk_Main,
    NULL AS Chk_Text
FROM Usuarios WHERE Email NOT LIKE '%@qa.test'

UNION ALL
SELECT 'ObraSocial', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_ObraSocial, Nombre, Codigo_Sigla, [Plan], Requiere_Bono, Observaciones, Visible)),
    NULL
FROM ObraSocial WHERE Nombre NOT LIKE 'QA[_]%'

UNION ALL
SELECT 'Especialidades', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Especialidad, Nombre, Visible)),
    NULL
FROM Especialidades WHERE Nombre NOT LIKE 'QA[_]%'

UNION ALL
SELECT 'Prestaciones_Medicas', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Prestacion, Nombre)),
    NULL
FROM Prestaciones_Medicas WHERE Nombre NOT LIKE 'QA[_]%' OR Nombre IS NULL

UNION ALL
SELECT 'Pacientes', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Paciente, Nombre, Apellido, DNI, Telefono, ID_ObraSocial, Numero_Credencial, Direccion, Plan_OS, Fecha_Nacimiento, Estado, Visible, Fecha_Creacion)),
    NULL
FROM Pacientes WHERE Nombre NOT LIKE 'QA[_]%' AND (TRY_CAST(DNI AS INT) IS NULL OR TRY_CAST(DNI AS INT) NOT BETWEEN 99000001 AND 99000999)

UNION ALL
SELECT 'Medicos', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Medico, ID_Usuario, Nombre, Apellido, Matricula, ID_Especialidad, Importe_Consulta, Estado, Visible, Fecha_Creacion, Fecha_Inicio_Actividad)),
    NULL
FROM Medicos WHERE Matricula NOT LIKE 'QA-%'

UNION ALL
SELECT 'Consultorio', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Consultorio, Numero_Consultorio, Estado, Ubicacion, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(Descripcion AS NVARCHAR(MAX)) + '|' + CAST(Equipamiento AS NVARCHAR(MAX))))
FROM Consultorio WHERE Numero_Consultorio NOT IN ('QA-1','QA-2','QA-3')

UNION ALL
SELECT 'Agenda_Medico', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Agenda, ID_Medico, ID_Consultorio, Dia_Semana, Hora_Inicio, Hora_Fin, Duracion_Turno_Min, Cupo_Maximo_Diario, Visible)),
    NULL
FROM Agenda_Medico

UNION ALL
SELECT 'Medico_ObraSocial', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Medico_ObraSocial, ID_Medico, ID_ObraSocial, Importe_Coseguro, Visible)),
    NULL
FROM Medico_ObraSocial mos
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = mos.ID_Medico AND m.Matricula LIKE 'QA-%')
  AND NOT EXISTS (SELECT 1 FROM ObraSocial os WHERE os.ID_ObraSocial = mos.ID_ObraSocial AND os.Nombre LIKE 'QA[_]%')

UNION ALL
SELECT 'Medico_Prestacion', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Medico_Prestacion, ID_Medico, ID_Prestacion, Duracion_Estimada_Min, Importe_Particular, Tipo, Visible)),
    NULL
FROM Medico_Prestacion mp
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = mp.ID_Medico AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Turnos', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Turno, ID_Medico, ID_Paciente, ID_Consultorio, Fecha_Hora, ID_Prestacion, Estado, Fecha_Reserva, Tipo_Consulta, Metodo_Pago_Planificado, Obra_Social_Planificada, Importe_Copago_Planificado, Visible, Fecha_Creacion)),
    NULL
FROM Turnos t
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = t.ID_Medico AND m.Matricula LIKE 'QA-%')
  AND NOT EXISTS (SELECT 1 FROM Consultorio c WHERE c.ID_Consultorio = t.ID_Consultorio AND c.Numero_Consultorio IN ('QA-1','QA-2','QA-3'))

UNION ALL
SELECT 'Cobros', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Cobro, ID_Turno, Metodo_Pago, Importe_Total, Importe_Cubierto_OS, Importe_Copago, Fecha_Cobro, Visible)),
    NULL
FROM Cobros co
WHERE NOT EXISTS (
    SELECT 1 FROM Turnos t JOIN Medicos m ON m.ID_Medico = t.ID_Medico
    WHERE t.ID_Turno = co.ID_Turno AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Historia_Clinica', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_HistoriaClinica, ID_Paciente, Fecha_Creacion, Estado, Visible)),
    NULL
FROM Historia_Clinica hc
WHERE NOT EXISTS (SELECT 1 FROM Pacientes p WHERE p.ID_Paciente = hc.ID_Paciente AND p.Nombre LIKE 'QA[_]%')

UNION ALL
SELECT 'Evolucion_Clinica', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Evolucion, ID_HistoriaClinica, ID_Medico, ID_Turno, ID_Prestacion, Fecha_Hora, Visible)),
    CHECKSUM_AGG(CHECKSUM(
        CAST(Motivo_Consulta AS NVARCHAR(MAX)) + '|' + CAST(Diagnostico AS NVARCHAR(MAX)) + '|' +
        CAST(Tratamiento AS NVARCHAR(MAX)) + '|' + CAST(Indicaciones AS NVARCHAR(MAX)) + '|' +
        CAST(Estudios_Solicitados AS NVARCHAR(MAX)) + '|' + CAST(Observaciones AS NVARCHAR(MAX))))
FROM Evolucion_Clinica ec
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = ec.ID_Medico AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Adjuntos_HistoriaClinica', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Adjunto, ID_Evolucion, Nombre_Archivo, Ruta_Archivo, Tipo_Archivo, Fecha_Carga, Visible)),
    NULL
FROM Adjuntos_HistoriaClinica a
WHERE NOT EXISTS (
    SELECT 1 FROM Evolucion_Clinica ec JOIN Medicos m ON m.ID_Medico = ec.ID_Medico
    WHERE ec.ID_Evolucion = a.ID_Evolucion AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Arrendamiento_Modulo', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Arrendamiento, ID_Medico, ID_Consultorio, Fecha_Inicio, Fecha_Fin, Dia_Semana, Hora_Inicio, Hora_Fin, Duracion_Turno_Min, Cupo_Maximo_Diario, Porcentaje_Consultorio, Porcentaje_Medico, Estado, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(ISNULL(Observaciones,'') AS NVARCHAR(MAX))))
FROM Arrendamiento_Modulo am
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = am.ID_Medico AND m.Matricula LIKE 'QA-%')
  AND NOT EXISTS (SELECT 1 FROM Consultorio c WHERE c.ID_Consultorio = am.ID_Consultorio AND c.Numero_Consultorio IN ('QA-1','QA-2','QA-3'))

UNION ALL
SELECT 'Facturacion', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Facturacion, ID_Turno, ID_Paciente, ID_Medico, ID_Arrendamiento, Fecha_Facturacion, Tipo_Consulta, Metodo_Pago, Obra_Social, Importe_Total, Importe_Copago, Porcentaje_Consultorio, Porcentaje_Medico, Estado_Pago, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(ISNULL(Observaciones,'') AS NVARCHAR(MAX))))
FROM Facturacion f
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = f.ID_Medico AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Liquidacion_Medica', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Liquidacion, ID_Medico, Fecha_Desde, Fecha_Hasta, Total_Facturado, Total_Consultorio, Total_Medico, Fecha_Generacion, Estado, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(ISNULL(Observaciones,'') AS NVARCHAR(MAX))))
FROM Liquidacion_Medica lm
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = lm.ID_Medico AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Pago_Facturacion', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Pago, ID_Facturacion, Metodo_Pago, Importe, Fecha_Pago, Estado, Numero_Comprobante, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(ISNULL(Observaciones,'') AS NVARCHAR(MAX))))
FROM Pago_Facturacion pf
WHERE NOT EXISTS (
    SELECT 1 FROM Facturacion f JOIN Medicos m ON m.ID_Medico = f.ID_Medico
    WHERE f.ID_Facturacion = pf.ID_Facturacion AND m.Matricula LIKE 'QA-%')

UNION ALL
SELECT 'Uso_Consultorio', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Uso, ID_Consultorio, ID_Medico, Fecha, Hora_Inicio, Hora_Fin, Cantidad_Pacientes, Facturacion_Generada, Visible)),
    NULL
FROM Uso_Consultorio uc
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = uc.ID_Medico AND m.Matricula LIKE 'QA-%')
  AND NOT EXISTS (SELECT 1 FROM Consultorio c WHERE c.ID_Consultorio = uc.ID_Consultorio AND c.Numero_Consultorio IN ('QA-1','QA-2','QA-3'))

UNION ALL
SELECT 'Cierre_Diario', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Cierre, ID_Medico, ID_Consultorio, ID_Arrendamiento, Fecha, Total_Facturado_Dia, Importe_Consultorio, Importe_Medico, Cantidad_Turnos, Estado, Fecha_Registro, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(ISNULL(Observaciones,'') AS NVARCHAR(MAX))))
FROM Cierre_Diario cd
WHERE NOT EXISTS (SELECT 1 FROM Medicos m WHERE m.ID_Medico = cd.ID_Medico AND m.Matricula LIKE 'QA-%')
  AND NOT EXISTS (SELECT 1 FROM Consultorio c WHERE c.ID_Consultorio = cd.ID_Consultorio AND c.Numero_Consultorio IN ('QA-1','QA-2','QA-3'))

UNION ALL
SELECT 'Reporte_Generado', COUNT(*),
    CHECKSUM_AGG(BINARY_CHECKSUM(ID_Reporte, ID_Usuario, Tipo_Reporte, Fecha_Generacion, Formato_Exportacion, Ruta_Archivo, Visible)),
    CHECKSUM_AGG(CHECKSUM(CAST(ISNULL(Parametros_Utilizados,'') AS NVARCHAR(MAX))))
FROM Reporte_Generado rg
WHERE NOT EXISTS (SELECT 1 FROM Usuarios u WHERE u.ID_Usuario = rg.ID_Usuario AND u.Email LIKE '%@qa.test')

-- Dashboard_Gerencial se compara APARTE, fila por fila (ver query siguiente) — no entra en el
-- CHECKSUM_AGG de arriba. Motivo (corregido 2026-09-23, ver Anexo Parte K): no tiene ningún
-- filtro QA propio (es una tabla de indicadores por Fecha, no por entidad), y
-- Fecha_Actualizacion cambia cada vez que se recalcula el dashboard de un día — incluir esa
-- columna en un checksum agregado de toda la tabla la hace fallar SIEMPRE que se recalcule
-- cualquier día, incluso si los valores de negocio no cambiaron.

ORDER BY 1;

-- =========================================================
-- Dashboard_Gerencial — comparación fila por fila (excluye Fecha_Actualizacion del checksum)
-- =========================================================
-- Metodología (regla 5 adaptada para esta tabla, sin filtro QA propio):
--   1) Correr esta query ANTES de crear datos QA -> guardar el resultado (una fila por Fecha).
--   2) Durante el QA, las fechas que se recalculan (ej. "hoy", al llamar
--      /dashboard o /dashboard/recalcular) van a diferir del baseline MIENTRAS existan datos QA
--      — es esperado, no es un incidente.
--   3) Después de qa_cleanup.sql, volver a recalcular esas mismas fechas
--      (POST /api/reportes/dashboard/recalcular?fecha=...) y re-correr esta query.
--   4) Para cada Fecha que se tocó durante el QA: el Chk_SinFechaActualizacion resultante tiene
--      que coincidir EXACTO con una consulta SQL independiente sobre esa fecha usando la tabla
--      Turnos/Facturacion ya limpia (sin datos QA) — no necesariamente con el valor del paso 1,
--      si en el medio pasó actividad real legítima para esa fecha. Para las fechas que el QA NO
--      tocó, tiene que coincidir EXACTO con el baseline del paso 1 — cualquier diferencia ahí sí
--      es un incidente.
SELECT
    Fecha,
    Total_Turnos_Dia, Turnos_Atendidos, Turnos_Cancelados, Turnos_No_Asistio, Turnos_Disponibles,
    Facturacion_Total_Dia, Cobros_Pendientes, Nuevos_Pacientes,
    BINARY_CHECKSUM(Total_Turnos_Dia, Turnos_Atendidos, Turnos_Cancelados, Turnos_No_Asistio,
                     Turnos_Disponibles, Facturacion_Total_Dia, Cobros_Pendientes, Nuevos_Pacientes, Visible) AS Chk_SinFechaActualizacion
FROM Dashboard_Gerencial
ORDER BY Fecha;
