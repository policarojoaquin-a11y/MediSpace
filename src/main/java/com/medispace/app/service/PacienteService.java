package com.medispace.app.service;

import com.medispace.app.dto.paciente.PacienteCreateDTO;
import com.medispace.app.dto.paciente.PacienteResponseDTO;
import com.medispace.app.dto.paciente.PacienteUpdateDTO;

import java.util.List;

public interface PacienteService {
    PacienteResponseDTO crearPaciente(PacienteCreateDTO dto);
    PacienteResponseDTO actualizarPaciente(Integer id, PacienteUpdateDTO dto);
    PacienteResponseDTO obtenerPaciente(Integer id);
    List<PacienteResponseDTO> listarPacientes();
    void eliminarPaciente(Integer id);
}
