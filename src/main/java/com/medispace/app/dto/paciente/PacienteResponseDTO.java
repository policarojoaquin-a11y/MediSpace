package com.medispace.app.dto.paciente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PacienteResponseDTO {
    private Integer idPaciente;
    private String nombre;
    private String apellido;
    private String dni;
    private String telefono;
    private Integer idObraSocial;
    private String nombreObraSocial;
    private String numeroCredencial;
    private String direccion;
    private String planOs;
    private LocalDate fechaNacimiento;
    private String estado;
}
