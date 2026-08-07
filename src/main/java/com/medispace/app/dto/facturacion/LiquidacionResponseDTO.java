package com.medispace.app.dto.facturacion;

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
public class LiquidacionResponseDTO {
    private Integer idLiquidacion;
    private Integer idMedico;
    private String nombreMedico;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private BigDecimal totalFacturado;
    private BigDecimal totalConsultorio;
    private BigDecimal totalMedico;
    private LocalDateTime fechaGeneracion;
    private String estado;
    private String observaciones;
}
