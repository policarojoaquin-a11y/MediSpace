package com.medispace.app.service.impl;

import com.medispace.app.dto.ObraSocialCreateDTO;
import com.medispace.app.dto.ObraSocialResponseDTO;
import com.medispace.app.dto.ObraSocialUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.ObraSocial;
import com.medispace.app.repository.ObraSocialRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.service.ObraSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObraSocialServiceImpl implements ObraSocialService {

    private final ObraSocialRepository obraSocialRepository;
    private final PacienteRepository pacienteRepository;

    @Override
    public List<ObraSocialResponseDTO> buscarObrasSociales(String nombre, Boolean requiereBono, boolean incluirInactivas) {
        String nombreFiltro = (nombre == null || nombre.isBlank()) ? null : nombre.trim();

        List<ObraSocial> obrasSociales = incluirInactivas
                ? obraSocialRepository.buscarIncludingInactive(nombreFiltro, requiereBono)
                : obraSocialRepository.buscar(nombreFiltro, requiereBono);

        return obrasSociales.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ObraSocialResponseDTO crearObraSocial(ObraSocialCreateDTO dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            throw new BusinessRuleException("El nombre de la obra social es obligatorio.");
        }
        if (obraSocialRepository.existsByNombre(dto.getNombre())) {
            throw new BusinessRuleException("Ya existe una obra social con ese nombre.");
        }
        ObraSocial obraSocial = ObraSocial.builder()
                .nombre(dto.getNombre())
                .codigoSigla(dto.getCodigoSigla())
                .plan(dto.getPlan())
                .requiereBono(dto.getRequiereBono() != null && dto.getRequiereBono())
                .observaciones(dto.getObservaciones())
                .visible(true)
                .build();
        obraSocial = obraSocialRepository.save(obraSocial);
        return mapToDTO(obraSocial);
    }

    @Override
    @Transactional
    public ObraSocialResponseDTO actualizarObraSocial(Integer id, ObraSocialUpdateDTO dto) {
        ObraSocial obraSocial = obraSocialRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Obra social no encontrada."));

        if (dto.getNombre() != null && !dto.getNombre().isBlank()) {
            // RF-OS3: el nombre debe seguir siendo único tras la modificación (incluso contra
            // obras sociales inactivas, para no reabrir un nombre "liberado" por una baja lógica).
            if (obraSocialRepository.countByNombreIncludingInactiveExcludingId(dto.getNombre(), id) > 0) {
                throw new BusinessRuleException("Ya existe una obra social con ese nombre.");
            }
            obraSocial.setNombre(dto.getNombre());
        }
        obraSocial.setCodigoSigla(dto.getCodigoSigla());
        obraSocial.setPlan(dto.getPlan());
        obraSocial.setRequiereBono(dto.getRequiereBono() != null && dto.getRequiereBono());
        obraSocial.setObservaciones(dto.getObservaciones());

        obraSocial = obraSocialRepository.save(obraSocial);
        return mapToDTO(obraSocial);
    }

    @Override
    @Transactional
    public void eliminarObraSocial(Integer id) {
        ObraSocial obraSocial = obraSocialRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Obra social no encontrada."));

        // RN-016: no se da de baja una obra social con médicos activos asociados.
        if (obraSocialRepository.countMedicosActivosAsociados(id) > 0) {
            throw new BusinessRuleException("RN-016: No se puede dar de baja: hay médicos activos asociados a esta obra social.");
        }

        // RN-019: no se da de baja una obra social con pacientes activos asociados. Sin este
        // chequeo, un Paciente.ID_ObraSocial queda apuntando a una fila invisible para el
        // @SQLRestriction de ObraSocial, y Hibernate revienta con EntityNotFoundException al
        // resolver esa referencia — no solo para ese paciente, sino para CUALQUIER listado que
        // lo incluya (GET /api/pacientes entero, no solo el registro afectado).
        if (pacienteRepository.countByObraSocial_IdObraSocial(id) > 0) {
            throw new BusinessRuleException("RN-019: No se puede dar de baja: hay pacientes activos asociados a esta obra social.");
        }

        // El @SQLDelete y @SQLRestriction de la entidad hacen el soft delete automático.
        obraSocialRepository.delete(obraSocial);
    }

    @Override
    @Transactional
    public void reactivarObraSocial(Integer id) {
        obraSocialRepository.findByIdIncludingInactive(id)
                .orElseThrow(() -> new BusinessRuleException("Obra social no encontrada."));
        obraSocialRepository.reactivar(id);
    }

    private ObraSocialResponseDTO mapToDTO(ObraSocial o) {
        return ObraSocialResponseDTO.builder()
                .idObraSocial(o.getIdObraSocial())
                .nombre(o.getNombre())
                .codigoSigla(o.getCodigoSigla())
                .plan(o.getPlan())
                .requiereBono(o.getRequiereBono())
                .observaciones(o.getObservaciones())
                .visible(o.getVisible())
                .cantidadMedicosAsociados(obraSocialRepository.countMedicosAsociados(o.getIdObraSocial()))
                .build();
    }
}
