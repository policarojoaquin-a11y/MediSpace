package com.medispace.app.dto.turno;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TurnoResponseDTO {
    private Integer idTurno;
    private Integer idMedico;
    private String nombreMedico;
    private String nombreEspecialidad;
    private Integer idPaciente;
    private String nombrePaciente;
    private String telefonoPaciente;
    private Integer idConsultorio;
    private String numeroConsultorio;
    private LocalDateTime fechaHora;
    private Integer idPrestacion;
    private String nombrePrestacion;
    private String estado;
    private LocalDateTime fechaReserva;
    private String tipoConsulta;
    private String metodoPagoPlanificado;
    private String obraSocialPlanificada;
    private BigDecimal importeCopagoPlanificado;
}
