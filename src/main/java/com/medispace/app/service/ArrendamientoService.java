package com.medispace.app.service;

import com.medispace.app.dto.arrendamiento.*;

import java.util.List;

public interface ArrendamientoService {
    ArrendamientoResponseDTO crearArrendamiento(ArrendamientoCreateDTO dto);
    ArrendamientoResponseDTO obtenerArrendamiento(Integer id);
    List<ArrendamientoResponseDTO> listarPorMedico(Integer idMedico);
    List<ArrendamientoResponseDTO> listarContratos(Integer medicoId);
    void darDeBajaArrendamiento(Integer id);
}
