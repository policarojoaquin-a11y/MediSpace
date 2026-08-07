package com.medispace.app.dto.turno;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CambiarEstadoTurnoDTO {
    private String nuevoEstado; // DISPONIBLE, RESERVADO, EN_ESPERA, ATENDIDO, CANCELADO, NO_ASISTIO
}
