package com.medispace.app.service.impl;

import com.medispace.app.dto.ConsultorioCreateDTO;
import com.medispace.app.dto.ConsultorioResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Consultorio;
import com.medispace.app.repository.ConsultorioRepository;
import com.medispace.app.service.ConsultorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsultorioServiceImpl implements ConsultorioService {

    private final ConsultorioRepository consultorioRepository;

    @Override
    public List<ConsultorioResponseDTO> listarConsultorios() {
        return consultorioRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConsultorioResponseDTO crearConsultorio(ConsultorioCreateDTO dto) {
        if (dto.getNumeroConsultorio() == null || dto.getNumeroConsultorio().trim().isEmpty()) {
            throw new BusinessRuleException("El número de consultorio es obligatorio.");
        }
        if (consultorioRepository.findByNumeroConsultorio(dto.getNumeroConsultorio()).isPresent()) {
            throw new BusinessRuleException("Ya existe un consultorio con ese número.");
        }

        Consultorio consultorio = Consultorio.builder()
                .numeroConsultorio(dto.getNumeroConsultorio())
                .descripcion(dto.getDescripcion())
                .equipamiento(dto.getEquipamiento())
                .ubicacion(dto.getUbicacion())
                .estado("DISPONIBLE")
                .visible(true)
                .build();

        consultorio = consultorioRepository.save(consultorio);
        return mapToDTO(consultorio);
    }

    private ConsultorioResponseDTO mapToDTO(Consultorio c) {
        return ConsultorioResponseDTO.builder()
                .idConsultorio(c.getIdConsultorio())
                .numeroConsultorio(c.getNumeroConsultorio())
                .descripcion(c.getDescripcion())
                .estado(c.getEstado())
                .equipamiento(c.getEquipamiento())
                .ubicacion(c.getUbicacion())
                .build();
    }
}
