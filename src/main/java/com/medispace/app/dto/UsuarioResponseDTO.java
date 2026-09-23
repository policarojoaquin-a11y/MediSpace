package com.medispace.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioResponseDTO {
    private Integer idUsuario;
    private String email;
    private String rol;
    private Boolean visible;
    private LocalDate fechaCreacion;
}
