package com.medispace.app.service.impl;

import com.medispace.app.dto.historiaclinica.*;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.*;
import com.medispace.app.repository.*;
import com.medispace.app.service.HistoriaClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoriaClinicaServiceImpl implements HistoriaClinicaService {

    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final EvolucionClinicaRepository evolucionClinicaRepository;
    private final MedicoRepository medicoRepository;
    private final TurnoRepository turnoRepository;
    private final PrestacionMedicaRepository prestacionMedicaRepository;

    @Override
    public HistoriaClinicaResponseDTO obtenerHistoriaClinicaPorPaciente(Integer idPaciente, String userRole) {
        HistoriaClinica hc = historiaClinicaRepository.findByPacienteIdPaciente(idPaciente)
                .orElseThrow(() -> new BusinessRuleException("Historia clínica no encontrada para el paciente."));

        List<EvolucionClinica> evoluciones = evolucionClinicaRepository.findByHistoriaClinicaIdHistoriaClinicaOrderByFechaHoraDesc(hc.getIdHistoriaClinica());

        List<EvolucionResponseDTO> evolucionesDTO = evoluciones.stream()
                .map(e -> mapEvolucionToDTO(e, userRole))
                .collect(Collectors.toList());

        return HistoriaClinicaResponseDTO.builder()
                .idHistoriaClinica(hc.getIdHistoriaClinica())
                .idPaciente(hc.getPaciente().getIdPaciente())
                .nombrePaciente(hc.getPaciente().getNombre() + " " + hc.getPaciente().getApellido())
                .dniPaciente(hc.getPaciente().getDni())
                .fechaCreacion(hc.getFechaCreacion())
                .estado(hc.getEstado())
                .evoluciones(evolucionesDTO)
                .build();
    }

    @Override
    @Transactional
    public EvolucionResponseDTO agregarEvolucion(Integer idHistoriaClinica, Integer idMedicoAuth, EvolucionCreateDTO dto) {
        HistoriaClinica hc = historiaClinicaRepository.findById(idHistoriaClinica)
                .orElseThrow(() -> new BusinessRuleException("Historia clínica no encontrada."));

        Medico medico = medicoRepository.findByUsuario_IdUsuario(idMedicoAuth)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));

        Turno turno = null;
        if (dto.getIdTurno() != null) {
            turno = turnoRepository.findById(dto.getIdTurno()).orElse(null);
        }

        PrestacionMedica prestacion = null;
        if (dto.getIdPrestacion() != null) {
            prestacion = prestacionMedicaRepository.findById(dto.getIdPrestacion()).orElse(null);
        }

        EvolucionClinica evolucion = EvolucionClinica.builder()
                .historiaClinica(hc)
                .medico(medico)
                .turno(turno)
                .prestacion(prestacion)
                .fechaHora(LocalDateTime.now())
                .motivoConsulta(dto.getMotivoConsulta())
                .diagnostico(dto.getDiagnostico())
                .tratamiento(dto.getTratamiento())
                .indicaciones(dto.getIndicaciones())
                .estudiosSolicitados(dto.getEstudiosSolicitados())
                .observaciones(dto.getObservaciones())
                .visible(true)
                .build();

        evolucion = evolucionClinicaRepository.save(evolucion);
        return mapEvolucionToDTO(evolucion, "MEDICO");
    }

    @Override
    @Transactional
    public EvolucionResponseDTO editarEvolucion(Integer idEvolucion, Integer idMedicoAuth, EvolucionUpdateDTO dto) {
        EvolucionClinica evolucion = evolucionClinicaRepository.findById(idEvolucion)
                .orElseThrow(() -> new BusinessRuleException("Evolución clínica no encontrada."));

        // RN-010: Solo el médico responsable edita su evolución
        validarAutoriaMedico(evolucion, idMedicoAuth);

        evolucion.setMotivoConsulta(dto.getMotivoConsulta());
        evolucion.setDiagnostico(dto.getDiagnostico());
        evolucion.setTratamiento(dto.getTratamiento());
        evolucion.setIndicaciones(dto.getIndicaciones());
        evolucion.setEstudiosSolicitados(dto.getEstudiosSolicitados());
        evolucion.setObservaciones(dto.getObservaciones());

        evolucion = evolucionClinicaRepository.save(evolucion);
        return mapEvolucionToDTO(evolucion, "MEDICO");
    }

    @Override
    @Transactional
    public EvolucionResponseDTO anularEvolucion(Integer idEvolucion, Integer idUsuarioAuth, EvolucionAnularDTO dto) {
        EvolucionClinica evolucion = evolucionClinicaRepository.findById(idEvolucion)
                .orElseThrow(() -> new BusinessRuleException("Evolución clínica no encontrada."));

        // RN-010: Solo el médico responsable puede anular su propia evolución (mismo control que editarEvolucion)
        validarAutoriaMedico(evolucion, idUsuarioAuth);

        // RN-011: Evoluciones no se eliminan, solo se anulan con motivo obligatorio
        if (dto.getMotivoAnulacion() == null || dto.getMotivoAnulacion().trim().isEmpty()) {
            throw new BusinessRuleException("RN-011: Se requiere un motivo obligatorio para anular la evolución.");
        }

        String obsOriginal = evolucion.getObservaciones() != null ? evolucion.getObservaciones() : "";
        evolucion.setObservaciones(obsOriginal + "\n[ANULADA - Motivo: " + dto.getMotivoAnulacion() + "]");

        // Persistimos el motivo ANTES del soft-delete: @SQLDelete emite un UPDATE fijo
        // (solo Visible=0) que no incluiría el cambio de Observaciones si no se guarda antes.
        evolucionClinicaRepository.saveAndFlush(evolucion);
        // No hacemos delete físico, aplicamos borrado lógico / anulación (Visible=0 vía @SQLDelete)
        evolucionClinicaRepository.delete(evolucion);

        return mapEvolucionToDTO(evolucion, "MEDICO");
    }

    // RN-010: solo el médico responsable de la evolución puede modificarla o anularla.
    // idUsuarioAuth es el ID_Usuario del JWT (no el idMedico) — se resuelve al médico
    // correspondiente antes de comparar, para no repetir el bug histórico de comparar
    // espacios de IDs distintos (ver Anexo de la auditoría).
    private void validarAutoriaMedico(EvolucionClinica evolucion, Integer idUsuarioAuth) {
        Medico medicoAuth = medicoRepository.findByUsuario_IdUsuario(idUsuarioAuth)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        if (!evolucion.getMedico().getIdMedico().equals(medicoAuth.getIdMedico())) {
            throw new BusinessRuleException("RN-010: No tenés permisos para modificar esta evolución clínica.");
        }
    }

    private EvolucionResponseDTO mapEvolucionToDTO(EvolucionClinica e, String userRole) {
        boolean esPaciente = "PACIENTE".equalsIgnoreCase(userRole);
        // Sección 4.5: ADMINISTRATIVO tiene acceso "Parcial" — puede confirmar que la evolución
        // existe y sus metadatos (fecha, médico, prestación, adjuntos), pero no el contenido
        // clínico en sí. Es contenido exclusivo del médico (y, para diagnóstico/tratamiento/
        // indicaciones, también vedado a PACIENTE por RF-H5).
        boolean esAdministrativo = "ADMINISTRATIVO".equalsIgnoreCase(userRole);

        List<AdjuntoDTO> adjuntosDTO = e.getAdjuntos() != null ? e.getAdjuntos().stream()
                .map(a -> AdjuntoDTO.builder()
                        .idAdjunto(a.getIdAdjunto())
                        .nombreArchivo(a.getNombreArchivo())
                        .rutaArchivo(a.getRutaArchivo())
                        .tipoArchivo(a.getTipoArchivo())
                        .fechaCarga(a.getFechaCarga())
                        .build())
                .collect(Collectors.toList()) : List.of();

        return EvolucionResponseDTO.builder()
                .idEvolucion(e.getIdEvolucion())
                .idHistoriaClinica(e.getHistoriaClinica().getIdHistoriaClinica())
                .idMedico(e.getMedico().getIdMedico())
                .nombreMedico(e.getMedico().getNombre() + " " + e.getMedico().getApellido())
                .idTurno(e.getTurno() != null ? e.getTurno().getIdTurno() : null)
                .idPrestacion(e.getPrestacion() != null ? e.getPrestacion().getIdPrestacion() : null)
                .nombrePrestacion(e.getPrestacion() != null ? e.getPrestacion().getNombre() : null)
                .fechaHora(e.getFechaHora())
                // RF-H5 / sección 4.5: PACIENTE ve solo adjuntos propios, no el texto clínico
                // (diagnóstico/tratamiento/indicaciones). ADMINISTRATIVO ("Parcial") no ve ningún
                // campo de contenido clínico, incluyendo motivo/estudios/observaciones.
                .motivoConsulta(esAdministrativo ? null : e.getMotivoConsulta())
                .diagnostico((esPaciente || esAdministrativo) ? null : e.getDiagnostico())
                .tratamiento((esPaciente || esAdministrativo) ? null : e.getTratamiento())
                .indicaciones((esPaciente || esAdministrativo) ? null : e.getIndicaciones())
                .estudiosSolicitados(esAdministrativo ? null : e.getEstudiosSolicitados())
                .observaciones(esAdministrativo ? null : e.getObservaciones())
                .adjuntos(adjuntosDTO)
                .build();
    }
}
