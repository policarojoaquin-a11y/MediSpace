package com.medispace.app.dto.turno;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un paciente que tenía un turno RESERVADO/EN_ESPERA en un día que se canceló: hay que
 * llamarlo para reprogramar. Se devuelve en {@link CancelacionDiaResponseDTO}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PacienteAContactarDTO {
    private String nombrePaciente;
    private String telefonoPaciente;
    private LocalDateTime fechaHora;
    private String estadoPrevio; // RESERVADO | EN_ESPERA
}
