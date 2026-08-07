package com.medispace.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultorioCreateDTO {
    private String numeroConsultorio;
    private String descripcion;
    private String equipamiento;
    private String ubicacion;
}
