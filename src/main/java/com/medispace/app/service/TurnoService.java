package com.medispace.app.service;

import com.medispace.app.dto.turno.*;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Medico;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface TurnoService {
    /**
     * Genera turnos "Disponible" para un médico/consultorio en un día de la semana fijo,
     * a partir de un contrato de arrendamiento. Es idempotente: no duplica turnos ya
     * existentes para ese médico en el mismo horario.
     */
    List<TurnoResponseDTO> generarTurnosParaContrato(
            Medico medico, Consultorio consultorio, String diaSemana,
            LocalTime horaInicio, LocalTime horaFin,
            Integer duracionTurnoMin, Integer cupoMaximoDiario,
            LocalDate fechaDesde, LocalDate fechaHasta);

    TurnoResponseDTO reservarTurno(Integer idTurno, ReservarTurnoDTO dto);
    TurnoResponseDTO cambiarEstadoTurno(Integer idTurno, CambiarEstadoTurnoDTO dto);
    TurnoResponseDTO obtenerTurno(Integer id);
    List<TurnoResponseDTO> listarTurnos(Integer idMedico, Integer idPaciente, String estado, LocalDate fecha, List<Integer> idsMedico);
    void eliminarTurno(Integer id);
}
