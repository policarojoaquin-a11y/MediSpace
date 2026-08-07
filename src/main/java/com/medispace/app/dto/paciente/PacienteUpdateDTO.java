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
public class PacienteUpdateDTO {
    private String nombre;
    private String apellido;
    private String dni; // Si se provee, se validará contra RN-008
    private String telefono;
    private Integer idObraSocial;
    private String numeroCredencial;
    private String direccion;
    private String planOs;
    private LocalDate fechaNacimiento;
}
