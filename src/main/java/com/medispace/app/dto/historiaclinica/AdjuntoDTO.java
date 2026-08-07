package com.medispace.app.dto.historiaclinica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdjuntoDTO {
    private Integer idAdjunto;
    private String nombreArchivo;
    private String rutaArchivo;
    private String tipoArchivo;
    private LocalDateTime fechaCarga;
}
