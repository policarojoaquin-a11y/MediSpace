package com.medispace.app.service.impl;

import com.medispace.app.dto.ConsultorioCreateDTO;
import com.medispace.app.dto.ConsultorioResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Consultorio;
import com.medispace.app.repository.ConsultorioRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.service.ConsultorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsultorioServiceImpl implements ConsultorioService {

    // Estados que se pueden fijar manualmente (sección 4.8 / spec.md §4.7 RF-A6). "Ocupado" no
    // está acá a propósito: se deriva de si hay un contrato de arrendamiento activo en el
    // horario actual, no es un estado que la administración fije a mano.
    private static final Set<String> ESTADOS_VALIDOS = Set.of("DISPONIBLE", "BLOQUEADO", "EN_MANTENIMIENTO", "FUERA_DE_SERVICIO");

    private final ConsultorioRepository consultorioRepository;
    private final TurnoRepository turnoRepository;

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

    @Override
    @Transactional
    public ConsultorioResponseDTO actualizarEstado(Integer id, String estado) {
        Consultorio consultorio = consultorioRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Consultorio no encontrado."));

        if (estado == null || !ESTADOS_VALIDOS.contains(estado.toUpperCase())) {
            throw new BusinessRuleException("Estado inválido. Valores permitidos: " + ESTADOS_VALIDOS);
        }

        String estadoNuevo = estado.toUpperCase();
        consultorio.setEstado(estadoNuevo);
        consultorio = consultorioRepository.save(consultorio);

        ConsultorioResponseDTO dto = mapToDTO(consultorio);
        // Aviso no bloqueante (checklist 27/08): si el consultorio deja de estar disponible pero
        // todavía tiene turnos pendientes (paciente reservado / en espera) a futuro, se informa
        // pero NO se bloquea el cambio.
        if (!"DISPONIBLE".equals(estadoNuevo)) {
            long pendientes = turnoRepository.countPendientesFuturosPorConsultorio(consultorio.getIdConsultorio());
            if (pendientes > 0) {
                dto.setAdvertencia("Atención: este consultorio tiene " + pendientes
                        + " turno(s) pendiente(s) a futuro (paciente reservado o en espera). El cambio de estado se aplicó igual — revisá si hay que reprogramar esos turnos.");
            }
        }
        return dto;
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
