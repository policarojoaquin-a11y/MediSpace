package com.medispace.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConsultorioResponseDTO {
    private Integer idConsultorio;
    private String numeroConsultorio;
    private String descripcion;
    private String estado;
    private String equipamiento;
    private String ubicacion;
    // Aviso no bloqueante devuelto tras un cambio de estado: p. ej. "quedan turnos pendientes
    // en este consultorio". El cambio se aplica igual (checklist 27/08).
    private String advertencia;
}
