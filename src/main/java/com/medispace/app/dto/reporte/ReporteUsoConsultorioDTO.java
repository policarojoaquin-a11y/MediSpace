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
public class ReporteUsoConsultorioDTO {
    private Integer idConsultorio;
    private String numeroConsultorio;
    private Integer idMedico;
    private String nombreMedico;
    private String periodo;
    private Integer totalSesiones;
    private Integer totalPacientesAtendidos;
    private BigDecimal facturacionGenerada;
    private BigDecimal importeConsultorio;
    private BigDecimal importeMedico;
}
