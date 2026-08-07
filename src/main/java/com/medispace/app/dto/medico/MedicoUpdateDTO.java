package com.medispace.app.dto.medico;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MedicoUpdateDTO {
    private String nombre;
    private String apellido;
    private Integer idEspecialidad;
    private BigDecimal importeConsulta;
    private List<Integer> idsObrasSociales;
}
