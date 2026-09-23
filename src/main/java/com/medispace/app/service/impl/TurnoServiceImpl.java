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
    private final MedicoRepository medicoRepository;
    private final FacturacionRepository facturacionRepository;
    private final FacturacionService facturacionService;

    // Estados del turno en los que puede existir una facturación asociada.
    private static final List<String> ESTADOS_CON_FACTURA = List.of("EN_ESPERA", "ATENDIDO");

    private static final List<String> ESTADOS_OCUPADOS = List.of("RESERVADO", "EN_ESPERA");
    // RN-004 / RN-021: turnos que no se modifican al cancelar un día.
    private static final List<String> ESTADOS_CERRADOS = List.of("ATENDIDO", "CANCELADO", "NO_ASISTIO");

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

        // RF-F1: el registro de facturación pendiente se genera cuando el paciente pasa a
        // "En Espera" (así la administración puede cobrar mientras espera) o directo a
        // "Atendido". crearFacturacionAutomatica es idempotente: no duplica si ya existe.
        if (("EN_ESPERA".equalsIgnoreCase(nuevoEstado) || "ATENDIDO".equalsIgnoreCase(nuevoEstado))
                && turno.getPaciente() != null) {
            facturacionService.crearFacturacionAutomatica(turno);
        }
        // Si el turno se cancela o se marca "No Asistió" habiendo generado ya la factura
        // (el paciente estuvo En Espera), esa factura se anula — si no, quedaría en PENDIENTE
        // y bloquearía la liquidación del médico (RN-005).
        if ("CANCELADO".equalsIgnoreCase(nuevoEstado) || "NO_ASISTIO".equalsIgnoreCase(nuevoEstado)) {
            facturacionService.anularFacturacionDeTurno(idTurno);
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

    @Override
    @Transactional
    public CancelacionDiaResponseDTO cancelarDiaMedico(CancelarDiaMedicoDTO dto) {
        if (dto.getIdMedico() == null || dto.getFecha() == null) {
            throw new BusinessRuleException("Médico y fecha son obligatorios para cancelar un día.");
        }
        Medico medico = medicoRepository.findById(dto.getIdMedico())
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));

        LocalDate desde = dto.getFecha();
        LocalDate hasta = dto.getFechaHasta() != null ? dto.getFechaHasta() : desde;
        if (hasta.isBefore(desde)) {
            throw new BusinessRuleException("Rango de fechas inválido: 'hasta' es anterior a 'desde'.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        List<Turno> turnos = turnoRepository.findByMedicoIdMedicoAndFechaHoraBetween(
                medico.getIdMedico(), desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));

        List<Turno> aCancelar = new ArrayList<>();
        List<PacienteAContactarDTO> contactar = new ArrayList<>();
        List<Integer> turnosConFacturaAAnular = new ArrayList<>();
        int disponibles = 0;
        int reservados = 0;

        for (Turno t : turnos) {
            String estado = t.getEstado() == null ? "" : t.getEstado().toUpperCase(Locale.ROOT);
            // RN-021 / RN-004: no se tocan turnos ya cerrados (ATENDIDO/CANCELADO/NO_ASISTIO)
            // ni los que ya transcurrieron.
            if (ESTADOS_CERRADOS.contains(estado) || !t.getFechaHora().isAfter(ahora)) {
                continue;
            }
            if (ESTADOS_OCUPADOS.contains(estado)) {
                reservados++;
                if (t.getPaciente() != null) {
                    contactar.add(PacienteAContactarDTO.builder()
                            .nombrePaciente(t.getPaciente().getNombre() + " " + t.getPaciente().getApellido())
                            .telefonoPaciente(t.getPaciente().getTelefono())
                            .fechaHora(t.getFechaHora())
                            .estadoPrevio(estado)
                            .build());
                }
                // Un turno EN_ESPERA ya generó su facturación pendiente (RF-F1): al cancelarlo
                // hay que anularla (RN-005).
                if ("EN_ESPERA".equals(estado)) {
                    turnosConFacturaAAnular.add(t.getIdTurno());
                }
            } else {
                disponibles++;
            }
            t.setEstado("CANCELADO");
            aCancelar.add(t);
        }

        turnoRepository.saveAll(aCancelar);
        turnosConFacturaAAnular.forEach(facturacionService::anularFacturacionDeTurno);
        contactar.sort(java.util.Comparator.comparing(PacienteAContactarDTO::getFechaHora));

        return CancelacionDiaResponseDTO.builder()
                .idMedico(medico.getIdMedico())
                .fecha(desde)
                .fechaHasta(dto.getFechaHasta())
                .motivo(dto.getMotivo())
                .turnosCancelados(aCancelar.size())
                .disponiblesCancelados(disponibles)
                .reservadosCancelados(reservados)
                .pacientesAContactar(contactar)
                .build();
    }

    private TurnoResponseDTO mapToDTO(Turno t) {
        Integer idFacturacion = null;
        String estadoPagoFacturacion = null;
        if (t.getEstado() != null && ESTADOS_CON_FACTURA.contains(t.getEstado().toUpperCase(Locale.ROOT))) {
            var factura = facturacionRepository.findByTurnoIdTurno(t.getIdTurno()).orElse(null);
            if (factura != null) {
                idFacturacion = factura.getIdFacturacion();
                estadoPagoFacturacion = factura.getEstadoPago();
            }
        }
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
                .idFacturacion(idFacturacion)
                .estadoPagoFacturacion(estadoPagoFacturacion)
                .build();
    }
}
