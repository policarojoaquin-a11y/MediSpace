package com.medispace.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioUpdateDTO {
    private String email;
    // Solo aplicable si el actor autenticado tiene rol GERENTE (RN: "el rol Gerente es el
    // único que puede modificar roles"). Si viene null, el rol actual no se toca.
    private String rol;
}
