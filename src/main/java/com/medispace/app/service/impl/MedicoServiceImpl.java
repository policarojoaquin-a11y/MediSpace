package com.medispace.app.service.impl;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.dto.medico.MedicoUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.*;
import com.medispace.app.model.enums.RolEnum;
import com.medispace.app.repository.*;
import com.medispace.app.service.MedicoService;
import com.medispace.app.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicoServiceImpl implements MedicoService {

    private final MedicoRepository medicoRepository;
    private final UsuarioService usuarioService;
    private final EspecialidadRepository especialidadRepository;
    private final PrestacionMedicaRepository prestacionMedicaRepository;
    private final ObraSocialRepository obraSocialRepository;
    private final MedicoPrestacionRepository medicoPrestacionRepository;

    @Override
    @Transactional
    public MedicoResponseDTO crearMedico(MedicoCreateDTO dto) {
        // Validar matrícula única
        if (medicoRepository.findByMatricula(dto.getMatricula()).isPresent()) {
            throw new BusinessRuleException("La matrícula ya se encuentra registrada.");
        }

        // Crear usuario asociado automáticamente con rol MEDICO
        UsuarioCreateDTO usuarioDTO = UsuarioCreateDTO.builder()
                .email(dto.getEmail())
                .password(dto.getMatricula()) // Password por defecto = matrícula
                .rol(RolEnum.MEDICO.name())
                .build();
        Usuario usuario = usuarioService.crearUsuario(usuarioDTO);

        Especialidad especialidad = especialidadRepository.findById(dto.getIdEspecialidad())
                .orElseThrow(() -> new BusinessRuleException("Especialidad no encontrada."));

        // Resolver obras sociales
        Set<ObraSocial> obrasSociales = resolverObrasSociales(dto.getIdsObrasSociales());

        Medico medico = Medico.builder()
                .usuario(usuario)
                .nombre(dto.getNombre())
                .apellido(dto.getApellido())
                .matricula(dto.getMatricula())
                .especialidad(especialidad)
                .importeConsulta(dto.getImporteConsulta())
                .estado("ACTIVO")
                .visible(true)
                .fechaCreacion(LocalDateTime.now())
                .fechaInicioActividad(dto.getFechaInicioActividad())
                .obrasSociales(obrasSociales)
                .build();

        medico = medicoRepository.save(medico);

        if (dto.getPrestaciones() != null) {
            for (MedicoPrestacionDTO prestacionDTO : dto.getPrestaciones()) {
                agregarPrestacion(medico.getIdMedico(), prestacionDTO);
            }
        }

        return mapToDTO(medico);
    }

    @Override
    @Transactional
    public MedicoResponseDTO actualizarMedico(Integer id, MedicoUpdateDTO dto) {
        Medico medico = medicoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));

        Especialidad especialidad = especialidadRepository.findById(dto.getIdEspecialidad())
                .orElseThrow(() -> new BusinessRuleException("Especialidad no encontrada."));

        medico.setNombre(dto.getNombre());
        medico.setApellido(dto.getApellido());
        medico.setEspecialidad(especialidad);
        medico.setImporteConsulta(dto.getImporteConsulta());

        if (dto.getIdsObrasSociales() != null) {
            medico.setObrasSociales(resolverObrasSociales(dto.getIdsObrasSociales()));
        }

        medico = medicoRepository.save(medico);
        return mapToDTO(medico);
    }

    @Override
    public MedicoResponseDTO obtenerMedico(Integer id) {
        Medico medico = medicoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        return mapToDTO(medico);
    }

    @Override
    public MedicoResponseDTO obtenerMedicoPorEmail(String email) {
        Medico medico = medicoRepository.findByUsuario_Email(email)
                .orElseThrow(() -> new BusinessRuleException("No se encontró un médico asociado a este usuario."));
        return mapToDTO(medico);
    }

    @Override
    public List<MedicoResponseDTO> listarMedicos() {
        return medicoRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void eliminarMedico(Integer id) {
        Medico medico = medicoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));

        // RN-012: Baja bloqueada si hay turnos futuros sin reasignar
        long turnosFuturos = medicoRepository.countTurnosFuturosActivos(id);
        if (turnosFuturos > 0) {
            throw new BusinessRuleException(
                    "RN-012: No se puede dar de baja al médico porque tiene " + turnosFuturos + " turno(s) futuro(s) sin reasignar.");
        }

        medicoRepository.delete(medico);
    }

    private PrestacionMedica resolverPrestacion(MedicoPrestacionDTO dto) {
        if (dto.getIdPrestacion() != null) {
            return prestacionMedicaRepository.findById(dto.getIdPrestacion())
                    .orElseThrow(() -> new BusinessRuleException("Prestación médica no encontrada."));
        }
        if (dto.getNombrePrestacion() == null || dto.getNombrePrestacion().isBlank()) {
            throw new BusinessRuleException("Debe indicar el nombre de la prestación.");
        }
        String nombre = dto.getNombrePrestacion().trim();
        return prestacionMedicaRepository.findByNombreIgnoreCase(nombre)
                .orElseGet(() -> prestacionMedicaRepository.save(
                        PrestacionMedica.builder().nombre(nombre).build()));
    }

    private Set<ObraSocial> resolverObrasSociales(List<Integer> ids) {
        Set<ObraSocial> obrasSociales = new HashSet<>();
        if (ids != null) {
            for (Integer osId : ids) {
                ObraSocial os = obraSocialRepository.findById(osId)
                        .orElseThrow(() -> new BusinessRuleException("Obra social con ID " + osId + " no encontrada."));
                obrasSociales.add(os);
            }
        }
        return obrasSociales;
    }

    @Override
    @Transactional
    public MedicoPrestacionDTO agregarPrestacion(Integer idMedico, MedicoPrestacionDTO dto) {
        Medico medico = medicoRepository.findById(idMedico)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        PrestacionMedica prestacion = resolverPrestacion(dto);

        medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(idMedico, prestacion.getIdPrestacion())
                .ifPresent(mp -> {
                    throw new BusinessRuleException("Esta prestación ya está asociada al médico.");
                });

        MedicoPrestacion medicoPrestacion = MedicoPrestacion.builder()
                .medico(medico)
                .prestacion(prestacion)
                .duracionEstimadaMin(dto.getDuracionEstimadaMin())
                .importeParticular(dto.getImporteParticular())
                .tipo(dto.getTipo())
                .visible(true)
                .build();

        medicoPrestacion = medicoPrestacionRepository.save(medicoPrestacion);
        return mapPrestacionToDTO(medicoPrestacion);
    }

    @Override
    @Transactional
    public MedicoPrestacionDTO actualizarPrestacion(Integer idMedicoPrestacion, MedicoPrestacionDTO dto) {
        MedicoPrestacion medicoPrestacion = medicoPrestacionRepository.findById(idMedicoPrestacion)
                .orElseThrow(() -> new BusinessRuleException("Prestación del médico no encontrada."));

        medicoPrestacion.setDuracionEstimadaMin(dto.getDuracionEstimadaMin());
        medicoPrestacion.setImporteParticular(dto.getImporteParticular());
        medicoPrestacion.setTipo(dto.getTipo());

        medicoPrestacion = medicoPrestacionRepository.save(medicoPrestacion);
        return mapPrestacionToDTO(medicoPrestacion);
    }

    @Override
    @Transactional
    public void eliminarPrestacion(Integer idMedicoPrestacion) {
        MedicoPrestacion medicoPrestacion = medicoPrestacionRepository.findById(idMedicoPrestacion)
                .orElseThrow(() -> new BusinessRuleException("Prestación del médico no encontrada."));
        medicoPrestacionRepository.delete(medicoPrestacion);
    }

    @Override
    public List<MedicoPrestacionDTO> listarPrestacionesDeMedico(Integer idMedico) {
        return medicoPrestacionRepository.findByMedicoIdMedico(idMedico).stream()
                .map(this::mapPrestacionToDTO)
                .collect(Collectors.toList());
    }

    private MedicoPrestacionDTO mapPrestacionToDTO(MedicoPrestacion mp) {
        return MedicoPrestacionDTO.builder()
                .idMedicoPrestacion(mp.getIdMedicoPrestacion())
                .idPrestacion(mp.getPrestacion().getIdPrestacion())
                .nombrePrestacion(mp.getPrestacion().getNombre())
                .duracionEstimadaMin(mp.getDuracionEstimadaMin())
                .importeParticular(mp.getImporteParticular())
                .tipo(mp.getTipo())
                .build();
    }

    private MedicoResponseDTO mapToDTO(Medico m) {
        return MedicoResponseDTO.builder()
                .idMedico(m.getIdMedico())
                .idUsuario(m.getUsuario().getIdUsuario())
                .email(m.getUsuario().getEmail())
                .nombre(m.getNombre())
                .apellido(m.getApellido())
                .matricula(m.getMatricula())
                .idEspecialidad(m.getEspecialidad().getIdEspecialidad())
                .nombreEspecialidad(m.getEspecialidad().getNombre())
                .importeConsulta(m.getImporteConsulta())
                .estado(m.getEstado())
                .fechaInicioActividad(m.getFechaInicioActividad())
                .prestaciones(listarPrestacionesDeMedico(m.getIdMedico()))
                .obrasSociales(m.getObrasSociales().stream()
                        .map(ObraSocial::getNombre)
                        .collect(Collectors.toList()))
                .idsObrasSociales(m.getObrasSociales().stream()
                        .map(ObraSocial::getIdObraSocial)
                        .collect(Collectors.toList()))
                .build();
    }
}
