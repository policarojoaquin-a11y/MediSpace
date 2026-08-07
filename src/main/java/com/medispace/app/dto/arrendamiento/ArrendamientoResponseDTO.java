package com.medispace.app.dto.arrendamiento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArrendamientoResponseDTO {
    private Integer idArrendamiento;
    private Integer idMedico;
    private String nombreMedico;
    private Integer idConsultorio;
    private String numeroConsultorio;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Integer duracionTurnoMin;
    private Integer cupoMaximoDiario;
    private BigDecimal porcentajeConsultorio;
    private BigDecimal porcentajeMedico;
    private String estado;
    private String observaciones;
    private Integer turnosGenerados;
}
