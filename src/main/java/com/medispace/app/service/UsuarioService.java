package com.medispace.app.service;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.UsuarioResponseDTO;
import com.medispace.app.dto.UsuarioUpdateDTO;
import com.medispace.app.model.Usuario;

import java.util.List;

public interface UsuarioService {
    Usuario crearUsuario(UsuarioCreateDTO dto);

    UsuarioResponseDTO actualizarUsuario(Integer id, UsuarioUpdateDTO dto, String rolActor);

    void eliminarUsuario(Integer id);

    void reactivarUsuario(Integer id);

    UsuarioResponseDTO obtenerUsuario(Integer id);

    List<UsuarioResponseDTO> buscarUsuarios(String email, String rol, boolean incluirInactivos);
}
