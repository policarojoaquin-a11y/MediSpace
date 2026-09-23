package com.medispace.app.dto.turno;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelacionDiaResponseDTO {
    private Integer idMedico;
    private LocalDate fecha;
    private LocalDate fechaHasta;
    private String motivo;
    private int turnosCancelados;          // total (disponibles + reservados)
    private int disponiblesCancelados;
    private int reservadosCancelados;      // RESERVADO + EN_ESPERA
    private List<PacienteAContactarDTO> pacientesAContactar;
}
