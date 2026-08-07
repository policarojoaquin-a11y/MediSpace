package com.medispace.app.service.impl;

import com.medispace.app.dto.turno.*;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.*;
import com.medispace.app.repository.*;
import com.medispace.app.service.FacturacionService;
import com.medispace.app.service.TurnoService;
import com.medispace.app.util.DiaSemanaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TurnoServiceImpl implements TurnoService {

    private final TurnoRepository turnoRepository;
    private final PacienteRepository pacienteRepository;
    private final PrestacionMedicaRepository prestacionMedicaRepository;
    private final FacturacionService facturacionService;

    private static final List<String> ESTADOS_OCUPADOS = List.of("RESERVADO", "EN_ESPERA");

    @Override
    @Transactional
    public List<TurnoResponseDTO> generarTurnosParaContrato(
            Medico medico, Consultorio consultorio, String diaSemana,
            LocalTime horaInicio, LocalTime horaFin,
            Integer duracionTurnoMin, Integer cupoMaximoDiario,
            LocalDate fechaDesde, LocalDate fechaHasta) {

        List<Turno> turnosGenerados = new ArrayList<>();
        LocalDate fechaActual = fechaDesde;

        while (!fechaActual.isAfter(fechaHasta)) {
            String diaSemanaEs = DiaSemanaUtil.traducir(fechaActual.getDayOfWeek());

            if (diaSemana.equalsIgnoreCase(diaSemanaEs)) {
                LocalTime horaSlot = horaInicio;
                int slotsGenerados = 0;

                while (horaSlot.plusMinutes(duracionTurnoMin).isBefore(horaFin.plusSeconds(1))
                       && slotsGenerados < cupoMaximoDiario) {

                    LocalDateTime fechaHoraSlot = LocalDateTime.of(fechaActual, horaSlot);

                    // Evitar duplicar turnos en el mismo horario para el mismo médico
                    if (!turnoRepository.existsByMedicoIdMedicoAndFechaHora(medico.getIdMedico(), fechaHoraSlot)) {
                        Turno turno = Turno.builder()
                                .medico(medico)
                                .consultorio(consultorio)
                                .fechaHora(fechaHoraSlot)
                                .estado("DISPONIBLE")
                                .visible(true)
                                .fechaCreacion(LocalDateTime.now())
                                .build();
                        turnosGenerados.add(turno);
                        slotsGenerados++;
                    }

                    horaSlot = horaSlot.plusMinutes(duracionTurnoMin);
                }
            }
            fechaActual = fechaActual.plusDays(1);
        }

        List<Turno> guardados = turnoRepository.saveAll(turnosGenerados);
        return guardados.stream().map(this::mapToDTO).toList();
    }

    @Override
    @Transactional
    public TurnoResponseDTO reservarTurno(Integer idTurno, ReservarTurnoDTO dto) {
        // Lock pesimista: evita que dos reservas concurrentes sobre el mismo turno lean ambas
        // "DISPONIBLE" antes de que cualquiera confirme (condición de carrera real bajo carga).
        Turno turno = turnoRepository.findByIdForUpdate(idTurno)
                .orElseThrow(() -> new BusinessRuleException("Turno no encontrado."));

        // RN-001: Solo se reserva un turno en estado "DISPONIBLE"
        if (!"DISPONIBLE".equalsIgnoreCase(turno.getEstado())) {
            throw new BusinessRuleException("RN-001: Solo se puede reservar un turno en estado DISPONIBLE.");
        }

        Paciente paciente = pacienteRepository.findById(dto.getIdPaciente())
                .orElseThrow(() -> new BusinessRuleException("Paciente no encontrado."));

        // RN-002: Un paciente no puede tener 2 turnos reservados en el mismo horario
        if (turnoRepository.existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(
                paciente.getIdPaciente(), turno.getFechaHora(), ESTADOS_OCUPADOS)) {
            throw new BusinessRuleException("RN-002: El paciente ya posee un turno reservado en este mismo horario.");
        }

        // RN-003: Un médico no puede tener 2 turnos en el mismo horario
        if (turnoRepository.existsByMedicoIdMedicoAndFechaHoraAndEstadoIn(
                turno.getMedico().getIdMedico(), turno.getFechaHora(), ESTADOS_OCUPADOS)) {
            throw new BusinessRuleException("RN-003: El médico ya tiene un turno reservado en este mismo horario.");
        }

        PrestacionMedica prestacion = null;
        if (dto.getIdPrestacion() != null) {
            prestacion = prestacionMedicaRepository.findById(dto.getIdPrestacion())
                    .orElseThrow(() -> new BusinessRuleException("Prestación médica no encontrada."));
        }

        turno.setPaciente(paciente);
        turno.setPrestacion(prestacion);
        turno.setEstado("RESERVADO");
        turno.setFechaReserva(LocalDateTime.now());
        turno.setTipoConsulta(dto.getTipoConsulta());
        turno.setMetodoPagoPlanificado(dto.getMetodoPago());
        turno.setObraSocialPlanificada(dto.getObraSocial());
        turno.setImporteCopagoPlanificado(dto.getCopago());

        turno = turnoRepository.save(turno);
        return mapToDTO(turno);
    }

    @Override
    @Transactional
    public TurnoResponseDTO cambiarEstadoTurno(Integer idTurno, CambiarEstadoTurnoDTO dto) {
        Turno turno = turnoRepository.findById(idTurno)
                .orElseThrow(() -> new BusinessRuleException("Turno no encontrado."));

        String estadoActual = turno.getEstado().toUpperCase();
        String nuevoEstado = dto.getNuevoEstado().toUpperCase();

        // RN-004: Un turno "Atendido" es inmutable, no vuelve a "Disponible" ni se cancela retroactivamente
        if ("ATENDIDO".equalsIgnoreCase(estadoActual)) {
            throw new BusinessRuleException("RN-004: Un turno en estado ATENDIDO es inmutable.");
        }

        // RF-T3: Cancelación — vuelve a "Disponible" solo si es antes del horario de atención; si no, queda "Cancelado".
        if ("CANCELADO".equalsIgnoreCase(nuevoEstado)) {
            if (LocalDateTime.now().isBefore(turno.getFechaHora())) {
                turno.setEstado("DISPONIBLE");
                turno.setPaciente(null);
                turno.setFechaReserva(null);
                turno.setPrestacion(null);
            } else {
                turno.setEstado("CANCELADO");
            }
        } else {
            turno.setEstado(nuevoEstado);
        }

        turno = turnoRepository.save(turno);

        // RF-F1: Al pasar un turno a "Atendido" se genera automáticamente un registro de facturación pendiente
        if ("ATENDIDO".equalsIgnoreCase(nuevoEstado)) {
            facturacionService.crearFacturacionAutomatica(turno);
        }

        return mapToDTO(turno);
    }

    @Override
    public TurnoResponseDTO obtenerTurno(Integer id) {
        Turno turno = turnoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Turno no encontrado."));
        return mapToDTO(turno);
    }

    @Override
    public List<TurnoResponseDTO> listarTurnos(Integer idMedico, Integer idPaciente, String estado, LocalDate fecha, List<Integer> idsMedico) {
        return turnoRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (idMedico != null) {
                predicates.add(cb.equal(root.get("medico").get("idMedico"), idMedico));
            }
            if (idsMedico != null && !idsMedico.isEmpty()) {
                predicates.add(root.get("medico").get("idMedico").in(idsMedico));
            }
            if (idPaciente != null) {
                predicates.add(cb.equal(root.get("paciente").get("idPaciente"), idPaciente));
            }
            if (estado != null && !estado.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("estado")), estado.toUpperCase()));
            }
            if (fecha != null) {
                LocalDateTime desde = fecha.atStartOfDay();
                LocalDateTime hasta = fecha.atTime(LocalTime.MAX);
                predicates.add(cb.between(root.get("fechaHora"), desde, hasta));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }).stream().map(this::mapToDTO).toList();
    }

    @Override
    @Transactional
    public void eliminarTurno(Integer id) {
        Turno turno = turnoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Turno no encontrado."));
        if ("ATENDIDO".equalsIgnoreCase(turno.getEstado())) {
            throw new BusinessRuleException("RN-004: Un turno en estado ATENDIDO es inmutable.");
        }
        turnoRepository.delete(turno);
    }

    private TurnoResponseDTO mapToDTO(Turno t) {
        return TurnoResponseDTO.builder()
                .idTurno(t.getIdTurno())
                .idMedico(t.getMedico().getIdMedico())
                .nombreMedico(t.getMedico().getNombre() + " " + t.getMedico().getApellido())
                .nombreEspecialidad(t.getMedico().getEspecialidad() != null ? t.getMedico().getEspecialidad().getNombre() : null)
                .idPaciente(t.getPaciente() != null ? t.getPaciente().getIdPaciente() : null)
                .nombrePaciente(t.getPaciente() != null ? t.getPaciente().getNombre() + " " + t.getPaciente().getApellido() : null)
                .telefonoPaciente(t.getPaciente() != null ? t.getPaciente().getTelefono() : null)
                .idConsultorio(t.getConsultorio() != null ? t.getConsultorio().getIdConsultorio() : null)
                .numeroConsultorio(t.getConsultorio() != null ? t.getConsultorio().getNumeroConsultorio() : null)
                .fechaHora(t.getFechaHora())
                .idPrestacion(t.getPrestacion() != null ? t.getPrestacion().getIdPrestacion() : null)
                .nombrePrestacion(t.getPrestacion() != null ? t.getPrestacion().getNombre() : null)
                .estado(t.getEstado())
                .fechaReserva(t.getFechaReserva())
                .tipoConsulta(t.getTipoConsulta())
                .metodoPagoPlanificado(t.getMetodoPagoPlanificado())
                .obraSocialPlanificada(t.getObraSocialPlanificada())
                .importeCopagoPlanificado(t.getImporteCopagoPlanificado())
                .build();
    }
}
