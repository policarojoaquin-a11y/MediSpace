package com.medispace.app.dto.medico;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MedicoPrestacionDTO {
    private Integer idMedicoPrestacion;
    private Integer idPrestacion;
    private String nombrePrestacion;
    private Integer duracionEstimadaMin;
    private BigDecimal importeParticular;
    private String tipo;
}
