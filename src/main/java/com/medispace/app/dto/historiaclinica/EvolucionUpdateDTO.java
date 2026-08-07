package com.medispace.app.dto.historiaclinica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvolucionUpdateDTO {
    private String motivoConsulta;
    private String diagnostico;
    private String tratamiento;
    private String indicaciones;
    private String estudiosSolicitados;
    private String observaciones;
}
