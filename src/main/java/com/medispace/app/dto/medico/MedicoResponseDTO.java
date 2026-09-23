package com.medispace.app.dto.medico;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MedicoResponseDTO {
    private Integer idMedico;
    private Integer idUsuario;
    private String email;
    private String nombre;
    private String apellido;
    private String matricula;
    private Integer idEspecialidad;
    private String nombreEspecialidad;
    private BigDecimal importeConsulta;
    private String estado;
    private Boolean visible;
    private LocalDate fechaInicioActividad;
    private List<MedicoPrestacionDTO> prestaciones;
    private List<MedicoObraSocialDTO> obrasSociales;
}
