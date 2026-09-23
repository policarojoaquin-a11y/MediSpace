package com.medispace.app.service.impl;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoObraSocialDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.dto.medico.MedicoSelfUpdateDTO;
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
import java.util.List;
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
    private final MedicoObraSocialRepository medicoObraSocialRepository;

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
                .build();

        medico = medicoRepository.save(medico);

        if (dto.getPrestaciones() != null) {
            for (MedicoPrestacionDTO prestacionDTO : dto.getPrestaciones()) {
                agregarPrestacion(medico.getIdMedico(), prestacionDTO);
            }
        }

        if (dto.getObrasSociales() != null) {
            for (MedicoObraSocialDTO osDTO : dto.getObrasSociales()) {
                agregarObraSocial(medico.getIdMedico(), osDTO);
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

        medico = medicoRepository.save(medico);
        return mapToDTO(medico);
    }

    @Override
    @Transactional
    public MedicoResponseDTO actualizarMisDatos(String email, MedicoSelfUpdateDTO dto) {
        Medico medico = medicoRepository.findByUsuario_Email(email)
                .orElseThrow(() -> new BusinessRuleException("No se encontró un médico asociado a este usuario."));

        // Autoedición restringida a nombre/apellido: Especialidad e Importe de Consulta no son
        // editables por el propio médico (afectan RN-006 y la habilitación por especialidad) —
        // solo vía actualizarMedico (Gerente/Administrativo). La cartilla del médico
        // (prestaciones y obras sociales con las que trabaja) sí la gestiona el propio médico,
        // pero por endpoints dedicados (/me/prestaciones, /me/obras-sociales), no por este DTO.
        medico.setNombre(dto.getNombre());
        medico.setApellido(dto.getApellido());

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
    public List<MedicoResponseDTO> listarMedicos(boolean incluirInactivos) {
        List<Medico> medicos = incluirInactivos
                ? medicoRepository.findAllIncludingInactive()
                : medicoRepository.findAll();
        return medicos.stream()
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

    @Override
    @Transactional
    public void reactivarMedico(Integer id) {
        medicoRepository.findByIdIncludingInactive(id)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        medicoRepository.reactivar(id);
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

    // ---- Cartilla propia del médico (RN-017) ----
    // El documento de relevamiento describe la hoja del médico (prestaciones + importes + obras
    // sociales con las que trabaja) como "actualizable cada vez que el médico lo desee". Estos
    // métodos habilitan eso: resuelven el médico desde el email del JWT y reutilizan la misma
    // lógica que usa Gerente/Administrativo, validando pertenencia antes de editar/borrar.

    private Medico resolverMedicoPropio(String email) {
        return medicoRepository.findByUsuario_Email(email)
                .orElseThrow(() -> new BusinessRuleException("No se encontró un médico asociado a este usuario."));
    }

    @Override
    @Transactional
    public MedicoPrestacionDTO agregarPrestacionPropia(String email, MedicoPrestacionDTO dto) {
        return agregarPrestacion(resolverMedicoPropio(email).getIdMedico(), dto);
    }

    @Override
    @Transactional
    public MedicoPrestacionDTO actualizarPrestacionPropia(String email, Integer idMedicoPrestacion, MedicoPrestacionDTO dto) {
        Medico medico = resolverMedicoPropio(email);
        MedicoPrestacion mp = medicoPrestacionRepository.findById(idMedicoPrestacion)
                .orElseThrow(() -> new BusinessRuleException("Prestación del médico no encontrada."));
        if (!mp.getMedico().getIdMedico().equals(medico.getIdMedico())) {
            throw new BusinessRuleException("RN-017: No podés modificar las prestaciones de otro médico.");
        }
        return actualizarPrestacion(idMedicoPrestacion, dto);
    }

    @Override
    @Transactional
    public void eliminarPrestacionPropia(String email, Integer idMedicoPrestacion) {
        Medico medico = resolverMedicoPropio(email);
        MedicoPrestacion mp = medicoPrestacionRepository.findById(idMedicoPrestacion)
                .orElseThrow(() -> new BusinessRuleException("Prestación del médico no encontrada."));
        if (!mp.getMedico().getIdMedico().equals(medico.getIdMedico())) {
            throw new BusinessRuleException("RN-017: No podés modificar las prestaciones de otro médico.");
        }
        eliminarPrestacion(idMedicoPrestacion);
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

    // RF-M3: la relación N:M médico–obra social (qué obras sociales trabaja + el coseguro que
    // cobra en cada una) la gestiona tanto Gerente/Administrativo (en nombre de cualquier
    // médico, ej. onboarding) como el propio médico sobre su cartilla, vía los métodos
    // *Propia (RN-017: solo su propia relación, nunca la de otro médico).
    @Override
    @Transactional
    public MedicoObraSocialDTO agregarObraSocial(Integer idMedico, MedicoObraSocialDTO dto) {
        Medico medico = medicoRepository.findById(idMedico)
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        if (dto.getIdObraSocial() == null) {
            throw new BusinessRuleException("Debe indicar la obra social.");
        }
        ObraSocial obraSocial = obraSocialRepository.findById(dto.getIdObraSocial())
                .orElseThrow(() -> new BusinessRuleException("Obra social no encontrada."));

        medicoObraSocialRepository.findByMedicoIdMedicoAndObraSocialIdObraSocial(idMedico, obraSocial.getIdObraSocial())
                .ifPresent(mos -> {
                    throw new BusinessRuleException("Esta obra social ya está asociada al médico.");
                });

        MedicoObraSocial medicoObraSocial = MedicoObraSocial.builder()
                .medico(medico)
                .obraSocial(obraSocial)
                .importeCoseguro(dto.getImporteCoseguro())
                .visible(true)
                .build();

        medicoObraSocial = medicoObraSocialRepository.save(medicoObraSocial);
        return mapObraSocialToDTO(medicoObraSocial);
    }

    @Override
    @Transactional
    public MedicoObraSocialDTO actualizarObraSocial(Integer idMedicoObraSocial, MedicoObraSocialDTO dto) {
        MedicoObraSocial medicoObraSocial = medicoObraSocialRepository.findById(idMedicoObraSocial)
                .orElseThrow(() -> new BusinessRuleException("Relación médico-obra social no encontrada."));
        medicoObraSocial.setImporteCoseguro(dto.getImporteCoseguro());
        medicoObraSocial = medicoObraSocialRepository.save(medicoObraSocial);
        return mapObraSocialToDTO(medicoObraSocial);
    }

    @Override
    @Transactional
    public MedicoObraSocialDTO actualizarCoseguroPropio(String email, Integer idMedicoObraSocial, MedicoObraSocialDTO dto) {
        Medico medico = medicoRepository.findByUsuario_Email(email)
                .orElseThrow(() -> new BusinessRuleException("No se encontró un médico asociado a este usuario."));
        MedicoObraSocial medicoObraSocial = medicoObraSocialRepository.findById(idMedicoObraSocial)
                .orElseThrow(() -> new BusinessRuleException("Relación médico-obra social no encontrada."));

        if (!medicoObraSocial.getMedico().getIdMedico().equals(medico.getIdMedico())) {
            throw new BusinessRuleException("RN-017: No podés modificar el coseguro de otro médico.");
        }

        medicoObraSocial.setImporteCoseguro(dto.getImporteCoseguro());
        medicoObraSocial = medicoObraSocialRepository.save(medicoObraSocial);
        return mapObraSocialToDTO(medicoObraSocial);
    }

    @Override
    @Transactional
    public MedicoObraSocialDTO agregarObraSocialPropia(String email, MedicoObraSocialDTO dto) {
        return agregarObraSocial(resolverMedicoPropio(email).getIdMedico(), dto);
    }

    @Override
    @Transactional
    public void eliminarObraSocialPropia(String email, Integer idMedicoObraSocial) {
        Medico medico = resolverMedicoPropio(email);
        MedicoObraSocial mos = medicoObraSocialRepository.findById(idMedicoObraSocial)
                .orElseThrow(() -> new BusinessRuleException("Relación médico-obra social no encontrada."));
        if (!mos.getMedico().getIdMedico().equals(medico.getIdMedico())) {
            throw new BusinessRuleException("RN-017: No podés modificar la relación con obras sociales de otro médico.");
        }
        eliminarObraSocial(idMedicoObraSocial);
    }

    @Override
    @Transactional
    public void eliminarObraSocial(Integer idMedicoObraSocial) {
        MedicoObraSocial medicoObraSocial = medicoObraSocialRepository.findById(idMedicoObraSocial)
                .orElseThrow(() -> new BusinessRuleException("Relación médico-obra social no encontrada."));
        medicoObraSocialRepository.delete(medicoObraSocial);
    }

    @Override
    public List<MedicoObraSocialDTO> listarObrasSocialesDeMedico(Integer idMedico) {
        return medicoObraSocialRepository.findByMedicoIdMedico(idMedico).stream()
                .map(this::mapObraSocialToDTO)
                .collect(Collectors.toList());
    }

    private MedicoObraSocialDTO mapObraSocialToDTO(MedicoObraSocial mos) {
        return MedicoObraSocialDTO.builder()
                .idMedicoObraSocial(mos.getIdMedicoObraSocial())
                .idObraSocial(mos.getObraSocial().getIdObraSocial())
                .nombreObraSocial(mos.getObraSocial().getNombre())
                .importeCoseguro(mos.getImporteCoseguro())
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
                .visible(m.getVisible())
                .fechaInicioActividad(m.getFechaInicioActividad())
                .prestaciones(listarPrestacionesDeMedico(m.getIdMedico()))
                .obrasSociales(listarObrasSocialesDeMedico(m.getIdMedico()))
                .build();
    }
}
