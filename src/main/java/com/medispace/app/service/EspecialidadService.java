package com.medispace.app.service;

import com.medispace.app.dto.EspecialidadCreateDTO;
import com.medispace.app.dto.EspecialidadResponseDTO;

import java.util.List;

public interface EspecialidadService {
    List<EspecialidadResponseDTO> listarEspecialidades();
    EspecialidadResponseDTO crearEspecialidad(EspecialidadCreateDTO dto);
}
