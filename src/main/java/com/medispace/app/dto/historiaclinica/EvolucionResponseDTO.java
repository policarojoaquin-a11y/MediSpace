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
public class EvolucionResponseDTO {
    private Integer idEvolucion;
    private Integer idHistoriaClinica;
    private Integer idMedico;
    private String nombreMedico;
    private Integer idTurno;
    private Integer idPrestacion;
    private String nombrePrestacion;
    private LocalDateTime fechaHora;
    private String motivoConsulta;
    private String diagnostico; // Omitido para pacientes (RF-H5)
    private String tratamiento; // Omitido para pacientes (RF-H5)
    private String indicaciones; // Omitido para pacientes (RF-H5)
    private String estudiosSolicitados;
    private String observaciones;
    private List<AdjuntoDTO> adjuntos;
}
