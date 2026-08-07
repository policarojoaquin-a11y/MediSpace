package com.medispace.app.service;

import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.dto.medico.MedicoUpdateDTO;

import java.util.List;

public interface MedicoService {
    MedicoResponseDTO crearMedico(MedicoCreateDTO dto);
    MedicoResponseDTO actualizarMedico(Integer id, MedicoUpdateDTO dto);
    MedicoResponseDTO obtenerMedico(Integer id);
    MedicoResponseDTO obtenerMedicoPorEmail(String email);
    List<MedicoResponseDTO> listarMedicos();
    void eliminarMedico(Integer id);
    MedicoPrestacionDTO agregarPrestacion(Integer idMedico, MedicoPrestacionDTO dto);
    MedicoPrestacionDTO actualizarPrestacion(Integer idMedicoPrestacion, MedicoPrestacionDTO dto);
    void eliminarPrestacion(Integer idMedicoPrestacion);
    List<MedicoPrestacionDTO> listarPrestacionesDeMedico(Integer idMedico);
}
