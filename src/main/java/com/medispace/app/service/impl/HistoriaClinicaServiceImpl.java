package com.medispace.app.service.impl;

import com.medispace.app.dto.historiaclinica.*;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.*;
import com.medispace.app.model.enums.RolEnum;
import com.medispace.app.repository.*;
import com.medispace.app.service.HistoriaClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoriaClinicaServiceImpl implements HistoriaClinicaService {

    private static final Set<String> EXTENSIONES_ADJUNTO_PERMITIDAS = Set.of("pdf", "jpg", "jpeg", "png");

    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final EvolucionClinicaRepository evolucionClinicaRepository;
    private final MedicoRepository medicoRepository;
    private final TurnoRepository turnoRepository;
    private final PrestacionMedicaRepository prestacionMedicaRepository;
    private final AdjuntoHistoriaClinicaRepository adjuntoHistoriaClinicaRepository;
    private final PacienteRepository pacienteRepository;

    @Value("${medispace.uploads.dir}")
    private String uploadsDir;

    @Override
    public HistoriaClinicaResponseDTO obtenerHistoriaClinicaPorPaciente(Integer idPaciente, Usuario actor) {
        // Constitución §3 / spec §4.2: un MEDICO solo accede a la HC de un paciente propio
        // (vinculado por al menos un turno). ADMINISTRATIVO ve todas. Antes este endpoint no
        // validaba pertenencia — cualquier médico leía la HC completa de cualquier paciente por id.
        validarAccesoPacienteSiMedico(actor, idPaciente);

        HistoriaClinica hc = historiaClinicaRepository.findByPacienteIdPaciente(idPaciente)
                .orElseThrow(() -> new BusinessRuleException("Historia clínica no encontrada para el paciente."));

        List<EvolucionClinica> evoluciones = evolucionClinicaRepository.findByHistoriaClinicaIdHistoriaClinicaOrderByFechaHoraDesc(hc.getIdHistoriaClinica());

        List<EvolucionResponseDTO> evolucionesDTO = evoluciones.stream()
                .map(this::mapEvolucionToDTO)
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

        // RN-010 (Hallazgo 4): solo el médico responsable registra evoluciones sobre la HC de
        // un paciente propio. "Propio" = tiene al menos un turno con ese paciente (todo turno
        // atendido ya implica uno). Antes, agregarEvolucion no validaba autoría — a diferencia
        // de editar/anular/adjuntar — y cualquier médico podía escribir en cualquier HC.
        Integer idPaciente = hc.getPaciente().getIdPaciente();
        if (!pacienteRepository.existsVinculadoAMedico(medico.getIdMedico(), idPaciente)) {
            throw new BusinessRuleException("RN-010: No podés registrar una evolución para un paciente que no tenés asignado en ningún turno.");
        }

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
        return mapEvolucionToDTO(evolucion);
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
        return mapEvolucionToDTO(evolucion);
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

        return mapEvolucionToDTO(evolucion);
    }

    @Override
    @Transactional
    public AdjuntoDTO agregarAdjunto(Integer idEvolucion, Usuario actor, MultipartFile file) {
        EvolucionClinica evolucion = evolucionClinicaRepository.findById(idEvolucion)
                .orElseThrow(() -> new BusinessRuleException("Evolución clínica no encontrada."));

        // RN-010: si quien sube el archivo es el médico, solo puede hacerlo sobre su propia
        // evolución. Administrativo adjunta en nombre de cualquier médico sin esa restricción
        // (sección 4.5 de la propuesta: "Adjuntar estudios: Sí" para Administrativo).
        if (RolEnum.MEDICO.name().equalsIgnoreCase(actor.getRol())) {
            validarAutoriaMedico(evolucion, actor.getIdUsuario());
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("El archivo adjunto no puede estar vacío.");
        }

        String nombreOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo";
        String extension = extensionDe(nombreOriginal);
        if (!EXTENSIONES_ADJUNTO_PERMITIDAS.contains(extension)) {
            throw new BusinessRuleException("RF-H3: Solo se permiten archivos PDF, JPG o PNG.");
        }

        String nombreAlmacenado = UUID.randomUUID() + "." + extension;
        try {
            Path directorio = Paths.get(uploadsDir);
            Files.createDirectories(directorio);
            Files.write(directorio.resolve(nombreAlmacenado), file.getBytes());
        } catch (IOException e) {
            throw new BusinessRuleException("No se pudo guardar el archivo adjunto. Intente nuevamente.");
        }

        AdjuntoHistoriaClinica adjunto = AdjuntoHistoriaClinica.builder()
                .evolucion(evolucion)
                .nombreArchivo(nombreOriginal)
                .rutaArchivo(nombreAlmacenado)
                .tipoArchivo(extension)
                .fechaCarga(LocalDateTime.now())
                .visible(true)
                .build();

        adjunto = adjuntoHistoriaClinicaRepository.save(adjunto);
        return mapAdjuntoToDTO(adjunto);
    }

    @Override
    public AdjuntoArchivoDTO descargarAdjunto(Integer idAdjunto, Usuario actor) {
        AdjuntoHistoriaClinica adjunto = adjuntoHistoriaClinicaRepository.findById(idAdjunto)
                .orElseThrow(() -> new BusinessRuleException("Adjunto no encontrado."));

        // Mismo control de pertenencia que la lectura de la HC: un MEDICO solo descarga
        // adjuntos de evoluciones de pacientes propios (antes se bajaba cualquiera por id).
        Integer idPaciente = adjunto.getEvolucion().getHistoriaClinica().getPaciente().getIdPaciente();
        validarAccesoPacienteSiMedico(actor, idPaciente);

        Path ruta = Paths.get(uploadsDir).resolve(adjunto.getRutaArchivo());
        Resource recurso;
        try {
            recurso = new UrlResource(ruta.toUri());
        } catch (MalformedURLException e) {
            throw new BusinessRuleException("No se pudo acceder al archivo adjunto.");
        }
        if (!recurso.exists() || !recurso.isReadable()) {
            throw new BusinessRuleException("El archivo adjunto no está disponible.");
        }
        return new AdjuntoArchivoDTO(recurso, adjunto.getNombreArchivo(), adjunto.getTipoArchivo());
    }

    private String extensionDe(String nombreArchivo) {
        int idx = nombreArchivo.lastIndexOf('.');
        return idx >= 0 && idx < nombreArchivo.length() - 1
                ? nombreArchivo.substring(idx + 1).toLowerCase()
                : "";
    }

    private AdjuntoDTO mapAdjuntoToDTO(AdjuntoHistoriaClinica a) {
        return AdjuntoDTO.builder()
                .idAdjunto(a.getIdAdjunto())
                .nombreArchivo(a.getNombreArchivo())
                .rutaArchivo(a.getRutaArchivo())
                .tipoArchivo(a.getTipoArchivo())
                .fechaCarga(a.getFechaCarga())
                .build();
    }

    // RN-010: solo el médico responsable de la evolución puede modificarla o anularla.
    // idUsuarioAuth es el ID_Usuario del JWT (no el idMedico) — se resuelve al médico
    // correspondiente antes de comparar, para no repetir el bug histórico de comparar
    // espacios de IDs distintos (ver Anexo de la auditoría).
    // Un MEDICO solo accede a la HC / adjuntos de un paciente vinculado a algún turno propio
    // (mismo criterio RN-010 que agregarEvolucion). GERENTE no llega (403 en el controller);
    // ADMINISTRATIVO ve todo. actor null (contextos sin auth) no se restringe.
    private void validarAccesoPacienteSiMedico(Usuario actor, Integer idPaciente) {
        if (actor == null || !RolEnum.MEDICO.name().equalsIgnoreCase(actor.getRol())) {
            return;
        }
        Medico medico = medicoRepository.findByUsuario_IdUsuario(actor.getIdUsuario())
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        if (!pacienteRepository.existsVinculadoAMedico(medico.getIdMedico(), idPaciente)) {
            throw new BusinessRuleException("RN-010: No tenés acceso a la historia clínica de un paciente que no atendés.");
        }
    }

    private void validarAutoriaMedico(EvolucionClinica evolucion, Integer idUsuarioAuth) {
        Medico medicoAuth = medicoRepository.findByUsuario_IdUsuario(idUsuarioAuth)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        if (!evolucion.getMedico().getIdMedico().equals(medicoAuth.getIdMedico())) {
            throw new BusinessRuleException("RN-010: No tenés permisos para modificar esta evolución clínica.");
        }
    }

    private EvolucionResponseDTO mapEvolucionToDTO(EvolucionClinica e) {
        List<AdjuntoDTO> adjuntosDTO = e.getAdjuntos() != null ? e.getAdjuntos().stream()
                .map(this::mapAdjuntoToDTO)
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
                .motivoConsulta(e.getMotivoConsulta())
                .diagnostico(e.getDiagnostico())
                .tratamiento(e.getTratamiento())
                .indicaciones(e.getIndicaciones())
                .estudiosSolicitados(e.getEstudiosSolicitados())
                .observaciones(e.getObservaciones())
                .adjuntos(adjuntosDTO)
                .build();
    }
}
