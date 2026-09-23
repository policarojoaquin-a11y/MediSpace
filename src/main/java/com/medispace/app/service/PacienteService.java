package com.medispace.app.service;

import com.medispace.app.dto.paciente.PacienteCreateDTO;
import com.medispace.app.dto.paciente.PacienteResponseDTO;
import com.medispace.app.dto.paciente.PacienteUpdateDTO;

import java.util.List;

public interface PacienteService {
    PacienteResponseDTO crearPaciente(PacienteCreateDTO dto);
    PacienteResponseDTO actualizarPaciente(Integer id, PacienteUpdateDTO dto);

    // idMedicoFiltro: si viene no-null (caller con rol MEDICO), restringe a pacientes vinculados
    // a ese médico. GERENTE/ADMINISTRATIVO pasan null y ven todo.
    PacienteResponseDTO obtenerPaciente(Integer id, Integer idMedicoFiltro);
    List<PacienteResponseDTO> listarPacientes(Integer idMedicoFiltro);

    void eliminarPaciente(Integer id);
}
