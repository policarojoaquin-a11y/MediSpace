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
public class MedicoCreateDTO {
    private String nombre;
    private String apellido;
    private String matricula;
    private Integer idEspecialidad;
    private BigDecimal importeConsulta;
    private LocalDate fechaInicioActividad;
    private String email;
    private List<MedicoObraSocialDTO> obrasSociales;
    private List<MedicoPrestacionDTO> prestaciones;
}
