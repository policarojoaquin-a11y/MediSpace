package com.medispace.app.service;

import com.medispace.app.dto.ConsultorioCreateDTO;
import com.medispace.app.dto.ConsultorioResponseDTO;

import java.util.List;

public interface ConsultorioService {
    List<ConsultorioResponseDTO> listarConsultorios();
    ConsultorioResponseDTO crearConsultorio(ConsultorioCreateDTO dto);
    ConsultorioResponseDTO actualizarEstado(Integer id, String estado);
}
