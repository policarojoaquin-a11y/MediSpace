package com.medispace.app.service;

import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoObraSocialDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.dto.medico.MedicoSelfUpdateDTO;
import com.medispace.app.dto.medico.MedicoUpdateDTO;

import java.util.List;

public interface MedicoService {
    MedicoResponseDTO crearMedico(MedicoCreateDTO dto);
    MedicoResponseDTO actualizarMedico(Integer id, MedicoUpdateDTO dto);
    MedicoResponseDTO actualizarMisDatos(String email, MedicoSelfUpdateDTO dto);
    MedicoResponseDTO obtenerMedico(Integer id);
    MedicoResponseDTO obtenerMedicoPorEmail(String email);
    List<MedicoResponseDTO> listarMedicos(boolean incluirInactivos);
    void eliminarMedico(Integer id);
    void reactivarMedico(Integer id);
    MedicoPrestacionDTO agregarPrestacion(Integer idMedico, MedicoPrestacionDTO dto);
    MedicoPrestacionDTO actualizarPrestacion(Integer idMedicoPrestacion, MedicoPrestacionDTO dto);
    void eliminarPrestacion(Integer idMedicoPrestacion);
    List<MedicoPrestacionDTO> listarPrestacionesDeMedico(Integer idMedico);

    // Gestión de la propia cartilla por el médico autenticado (RN-017): resuelven el médico
    // desde el email del JWT y validan pertenencia sobre la fila antes de tocarla.
    MedicoPrestacionDTO agregarPrestacionPropia(String email, MedicoPrestacionDTO dto);
    MedicoPrestacionDTO actualizarPrestacionPropia(String email, Integer idMedicoPrestacion, MedicoPrestacionDTO dto);
    void eliminarPrestacionPropia(String email, Integer idMedicoPrestacion);

    MedicoObraSocialDTO agregarObraSocial(Integer idMedico, MedicoObraSocialDTO dto);
    MedicoObraSocialDTO actualizarObraSocial(Integer idMedicoObraSocial, MedicoObraSocialDTO dto);
    MedicoObraSocialDTO actualizarCoseguroPropio(String email, Integer idMedicoObraSocial, MedicoObraSocialDTO dto);
    MedicoObraSocialDTO agregarObraSocialPropia(String email, MedicoObraSocialDTO dto);
    void eliminarObraSocialPropia(String email, Integer idMedicoObraSocial);
    void eliminarObraSocial(Integer idMedicoObraSocial);
    List<MedicoObraSocialDTO> listarObrasSocialesDeMedico(Integer idMedico);
}
