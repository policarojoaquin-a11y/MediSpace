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
public class MedicoObraSocialDTO {
    private Integer idMedicoObraSocial;
    private Integer idObraSocial;
    private String nombreObraSocial;
    // Coseguro que cobra este médico para esta obra social puntual — lo decide el médico.
    private BigDecimal importeCoseguro;
}
