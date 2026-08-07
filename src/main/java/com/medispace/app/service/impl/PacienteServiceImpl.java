package com.medispace.app.service.impl;

import com.medispace.app.dto.paciente.PacienteCreateDTO;
import com.medispace.app.dto.paciente.PacienteResponseDTO;
import com.medispace.app.dto.paciente.PacienteUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.HistoriaClinica;
import com.medispace.app.model.ObraSocial;
import com.medispace.app.model.Paciente;
import com.medispace.app.repository.HistoriaClinicaRepository;
import com.medispace.app.repository.ObraSocialRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.service.PacienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PacienteServiceImpl implements PacienteService {

    private final PacienteRepository pacienteRepository;
    private final ObraSocialRepository obraSocialRepository;
    private final HistoriaClinicaRepository historiaClinicaRepository;

    @Override
    @Transactional
    public PacienteResponseDTO crearPaciente(PacienteCreateDTO dto) {
        // RN-009: DNI único, incluso entre inactivos
        long countDni = pacienteRepository.countByDniIncludingInactive(dto.getDni());
        if (countDni > 0) {
            throw new BusinessRuleException("RN-009: El DNI ya se encuentra registrado (incluso si está inactivo).");
        }

        ObraSocial os = null;
        if (dto.getIdObraSocial() != null) {
            os = obraSocialRepository.findById(dto.getIdObraSocial())
                    .orElseThrow(() -> new BusinessRuleException("Obra social no encontrada."));
        }

        Paciente paciente = Paciente.builder()
                .nombre(dto.getNombre())
                .apellido(dto.getApellido())
                .dni(dto.getDni())
                .telefono(dto.getTelefono())
                .obraSocial(os)
                .numeroCredencial(dto.getNumeroCredencial())
                .direccion(dto.getDireccion())
                .planOs(dto.getPlanOs())
                .fechaNacimiento(dto.getFechaNacimiento())
                .estado("ACTIVO")
                .visible(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        paciente = pacienteRepository.save(paciente);

        // RF-P2: Al guardar, se crea automáticamente la Historia Clínica vacía asociada (1 a 1)
        HistoriaClinica historiaClinica = HistoriaClinica.builder()
                .paciente(paciente)
                .fechaCreacion(LocalDateTime.now())
                .estado("ACTIVA")
                .visible(true)
                .build();
        historiaClinicaRepository.save(historiaClinica);

        return mapToDTO(paciente);
    }

    @Override
    @Transactional
    public PacienteResponseDTO actualizarPaciente(Integer id, PacienteUpdateDTO dto) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Paciente no encontrado."));

        // RN-008: DNI inmutable
        if (dto.getDni() != null && !dto.getDni().equals(paciente.getDni())) {
            throw new BusinessRuleException("RN-008: El DNI del paciente es inmutable una vez creado.");
        }

        ObraSocial os = null;
        if (dto.getIdObraSocial() != null) {
            os = obraSocialRepository.findById(dto.getIdObraSocial())
                    .orElseThrow(() -> new BusinessRuleException("Obra social no encontrada."));
        }

        paciente.setNombre(dto.getNombre());
        paciente.setApellido(dto.getApellido());
        paciente.setTelefono(dto.getTelefono());
        paciente.setObraSocial(os);
        paciente.setNumeroCredencial(dto.getNumeroCredencial());
        paciente.setDireccion(dto.getDireccion());
        paciente.setPlanOs(dto.getPlanOs());
        paciente.setFechaNacimiento(dto.getFechaNacimiento());

        paciente = pacienteRepository.save(paciente);
        return mapToDTO(paciente);
    }

    @Override
    public PacienteResponseDTO obtenerPaciente(Integer id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Paciente no encontrado."));
        return mapToDTO(paciente);
    }

    @Override
    public List<PacienteResponseDTO> listarPacientes() {
        return pacienteRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void eliminarPaciente(Integer id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Paciente no encontrado."));
        // El @SQLDelete y @SQLRestriction hacen el soft delete automático
        pacienteRepository.delete(paciente);
    }

    private PacienteResponseDTO mapToDTO(Paciente p) {
        return PacienteResponseDTO.builder()
                .idPaciente(p.getIdPaciente())
                .nombre(p.getNombre())
                .apellido(p.getApellido())
                .dni(p.getDni())
                .telefono(p.getTelefono())
                .idObraSocial(p.getObraSocial() != null ? p.getObraSocial().getIdObraSocial() : null)
                .nombreObraSocial(p.getObraSocial() != null ? p.getObraSocial().getNombre() : null)
                .numeroCredencial(p.getNumeroCredencial())
                .direccion(p.getDireccion())
                .planOs(p.getPlanOs())
                .fechaNacimiento(p.getFechaNacimiento())
                .estado(p.getEstado())
                .build();
    }
}
