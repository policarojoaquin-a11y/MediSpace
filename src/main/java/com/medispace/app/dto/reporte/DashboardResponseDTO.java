package com.medispace.app.dto.reporte;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardResponseDTO {
    private LocalDate fecha;
    private Integer totalTurnosDia;
    private Integer turnosAtendidos;
    private Integer turnosCancelados;
    private Integer turnosNoAsistio;
    private Integer turnosDisponibles;
    private BigDecimal facturacionTotalDia;
    private BigDecimal cobrosPendientes;
    private Integer nuevosPacientes;
    private LocalDateTime fechaActualizacion;
}
