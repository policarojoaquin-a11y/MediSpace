package com.medispace.app.dto.facturacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenerarLiquidacionDTO {
    private Integer idMedico;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
}
