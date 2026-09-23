package com.medispace.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ObraSocialResponseDTO {
    private Integer idObraSocial;
    private String nombre;
    private String codigoSigla;
    private String plan;
    private Boolean requiereBono;
    private String observaciones;
    private Boolean visible;
    private Long cantidadMedicosAsociados;
}
