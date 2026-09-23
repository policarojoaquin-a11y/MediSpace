package com.medispace.app.dto.historiaclinica;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@AllArgsConstructor
public class AdjuntoArchivoDTO {
    private final Resource recurso;
    private final String nombreArchivo;
    private final String tipoArchivo;
}
