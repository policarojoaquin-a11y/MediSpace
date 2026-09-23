package com.medispace.app.service;

import com.medispace.app.dto.ObraSocialCreateDTO;
import com.medispace.app.dto.ObraSocialResponseDTO;
import com.medispace.app.dto.ObraSocialUpdateDTO;

import java.util.List;

public interface ObraSocialService {
    List<ObraSocialResponseDTO> buscarObrasSociales(String nombre, Boolean requiereBono, boolean incluirInactivas);

    ObraSocialResponseDTO crearObraSocial(ObraSocialCreateDTO dto);

    ObraSocialResponseDTO actualizarObraSocial(Integer id, ObraSocialUpdateDTO dto);

    void eliminarObraSocial(Integer id);

    void reactivarObraSocial(Integer id);
}
