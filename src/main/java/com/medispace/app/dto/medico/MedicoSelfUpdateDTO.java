package com.medispace.app.dto.medico;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Subconjunto restringido de MedicoUpdateDTO para autoedición ("Mis Datos"): no incluye
// idEspecialidad ni importeConsulta, que quedan admin-only (afectan RN-006 y credencialización
// por especialidad, no son datos personales de bajo riesgo). Las obras sociales con las que
// trabaja (alta/baja de la relación) también quedan admin-only — lo único que el médico decide
// por sí mismo es el coseguro de cada una, vía los endpoints /medicos/me/obras-sociales/*.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MedicoSelfUpdateDTO {
    private String nombre;
    private String apellido;
}
