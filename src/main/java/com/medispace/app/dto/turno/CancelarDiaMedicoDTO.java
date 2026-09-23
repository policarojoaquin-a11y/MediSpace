package com.medispace.app.dto.turno;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelarDiaMedicoDTO {
    // Para el rol MEDICO el controller lo sobreescribe con el id propio del JWT.
    private Integer idMedico;
    private LocalDate fecha;        // día a cancelar (obligatorio)
    private LocalDate fechaHasta;   // opcional: último día del rango (vacaciones/licencia). Si es null == fecha
    private String motivo;          // opcional: informativo (no se persiste, viaja en la respuesta)
}
