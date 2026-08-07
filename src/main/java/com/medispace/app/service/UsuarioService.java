package com.medispace.app.service;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.model.Usuario;

public interface UsuarioService {
    Usuario crearUsuario(UsuarioCreateDTO dto);
}
