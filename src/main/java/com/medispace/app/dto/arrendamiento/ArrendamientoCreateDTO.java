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
public class ArrendamientoCreateDTO {
    private Integer idMedico;
    private Integer idConsultorio;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String diaSemana;        // LUNES..DOMINGO
    private LocalTime horaInicio;    // RN-014: HoraFin - HoraInicio >= 4hs
    private LocalTime horaFin;
    private Integer duracionTurnoMin;
    private Integer cupoMaximoDiario;
    private BigDecimal porcentajeConsultorio;
    private BigDecimal porcentajeMedico;
    private String observaciones;
}
