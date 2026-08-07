package com.medispace.app.dto.reporte;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReporteFacturacionDTO {
    private Integer idMedico;
    private String nombreMedico;
    private String obraSocial;
    private String metodoPago;
    private String periodo; // ej: "2025-07"
    private BigDecimal totalFacturado;
    private BigDecimal totalCobrado;
    private BigDecimal totalPendiente;
    private BigDecimal totalCopago;
    private Long cantidadTurnos;
}
