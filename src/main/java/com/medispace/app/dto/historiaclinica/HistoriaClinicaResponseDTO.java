package com.medispace.app.dto.historiaclinica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HistoriaClinicaResponseDTO {
    private Integer idHistoriaClinica;
    private Integer idPaciente;
    private String nombrePaciente;
    private String dniPaciente;
    private LocalDateTime fechaCreacion;
    private String estado;
    private List<EvolucionResponseDTO> evoluciones;
}
