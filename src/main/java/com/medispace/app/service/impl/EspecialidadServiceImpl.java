package com.medispace.app.service.impl;

import com.medispace.app.dto.EspecialidadCreateDTO;
import com.medispace.app.dto.EspecialidadResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Especialidad;
import com.medispace.app.repository.EspecialidadRepository;
import com.medispace.app.service.EspecialidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EspecialidadServiceImpl implements EspecialidadService {

    private final EspecialidadRepository especialidadRepository;

    @Override
    public List<EspecialidadResponseDTO> listarEspecialidades() {
        return especialidadRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EspecialidadResponseDTO crearEspecialidad(EspecialidadCreateDTO dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            throw new BusinessRuleException("El nombre de la especialidad es obligatorio.");
        }
        if (especialidadRepository.existsByNombre(dto.getNombre())) {
            throw new BusinessRuleException("Ya existe una especialidad con ese nombre.");
        }
        Especialidad especialidad = Especialidad.builder()
                .nombre(dto.getNombre())
                .visible(true)
                .build();
        especialidad = especialidadRepository.save(especialidad);
        return mapToDTO(especialidad);
    }

    private EspecialidadResponseDTO mapToDTO(Especialidad e) {
        return EspecialidadResponseDTO.builder()
                .idEspecialidad(e.getIdEspecialidad())
                .nombre(e.getNombre())
                .build();
    }
}
