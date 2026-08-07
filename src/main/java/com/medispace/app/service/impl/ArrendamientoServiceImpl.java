package com.medispace.app.service.impl;

import com.medispace.app.dto.arrendamiento.*;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.*;
import com.medispace.app.repository.*;
import com.medispace.app.service.ArrendamientoService;
import com.medispace.app.service.TurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArrendamientoServiceImpl implements ArrendamientoService {

    private final ArrendamientoModuloRepository arrendamientoRepository;
    private final MedicoRepository medicoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final TurnoService turnoService;

    private static final int DURACION_MINIMA_MINUTOS = 240; // RN-014: mínimo 4 horas
    private static final int DURACION_TURNO_DEFAULT_MIN = 30;
    private static final int CUPO_MAXIMO_DEFAULT = 8;
    private static final int MESES_HORIZONTE_SIN_FECHA_FIN = 6; // RF-T1: contrato indefinido

    @Override
    @Transactional
    public ArrendamientoResponseDTO crearArrendamiento(ArrendamientoCreateDTO dto) {
        Medico medico = medicoRepository.findById(dto.getIdMedico())
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        Consultorio consultorio = consultorioRepository.findById(dto.getIdConsultorio())
                .orElseThrow(() -> new BusinessRuleException("Consultorio no encontrado."));

        // RN-014: Duración mínima 4 horas
        long minutosAsignados = Duration.between(dto.getHoraInicio(), dto.getHoraFin()).toMinutes();
        if (minutosAsignados < DURACION_MINIMA_MINUTOS) {
            throw new BusinessRuleException(
                    "RN-014: La asignación mínima de consultorio es de 4 horas (240 minutos). Se asignaron solo " + minutosAsignados + " min.");
        }

        if (dto.getDiaSemana() == null || dto.getDiaSemana().trim().isEmpty()) {
            throw new BusinessRuleException("El día de la semana es obligatorio.");
        }

        // RN-013: Verificar que no haya superposición horaria para ese consultorio, ese día
        long superposiciones = arrendamientoRepository.countSuperposicionesConsultorio(
                dto.getIdConsultorio(), dto.getDiaSemana(), dto.getFechaInicio(), dto.getFechaFin(),
                dto.getHoraInicio(), dto.getHoraFin());
        if (superposiciones > 0) {
            throw new BusinessRuleException(
                    "RN-013: El consultorio ya tiene un contrato activo que se superpone con el día/horario/período solicitado.");
        }

        Integer duracionTurnoMin = dto.getDuracionTurnoMin() != null ? dto.getDuracionTurnoMin() : DURACION_TURNO_DEFAULT_MIN;
        Integer cupoMaximoDiario = dto.getCupoMaximoDiario() != null ? dto.getCupoMaximoDiario() : CUPO_MAXIMO_DEFAULT;

        ArrendamientoModulo arrendamiento = ArrendamientoModulo.builder()
                .medico(medico)
                .consultorio(consultorio)
                .fechaInicio(dto.getFechaInicio())
                .fechaFin(dto.getFechaFin())
                .diaSemana(dto.getDiaSemana().toUpperCase())
                .horaInicio(dto.getHoraInicio())
                .horaFin(dto.getHoraFin())
                .duracionTurnoMin(duracionTurnoMin)
                .cupoMaximoDiario(cupoMaximoDiario)
                .porcentajeConsultorio(dto.getPorcentajeConsultorio() != null ? dto.getPorcentajeConsultorio() : new BigDecimal("30.00"))
                .porcentajeMedico(dto.getPorcentajeMedico() != null ? dto.getPorcentajeMedico() : new BigDecimal("70.00"))
                .observaciones(dto.getObservaciones())
                .estado("ACTIVO")
                .visible(true)
                .build();

        arrendamiento = arrendamientoRepository.save(arrendamiento);

        // RF-T1: el contrato genera automáticamente los turnos "Disponible" correspondientes.
        // Si no tiene fecha fin, se genera un horizonte fijo de meses hacia adelante.
        LocalDate fechaHastaGeneracion = arrendamiento.getFechaFin() != null
                ? arrendamiento.getFechaFin()
                : arrendamiento.getFechaInicio().plusMonths(MESES_HORIZONTE_SIN_FECHA_FIN);

        int turnosGenerados = turnoService.generarTurnosParaContrato(
                medico, consultorio, arrendamiento.getDiaSemana(),
                arrendamiento.getHoraInicio(), arrendamiento.getHoraFin(),
                duracionTurnoMin, cupoMaximoDiario,
                arrendamiento.getFechaInicio(), fechaHastaGeneracion
        ).size();

        ArrendamientoResponseDTO response = mapArrendamientoToDTO(arrendamiento);
        response.setTurnosGenerados(turnosGenerados);
        return response;
    }

    @Override
    public ArrendamientoResponseDTO obtenerArrendamiento(Integer id) {
        ArrendamientoModulo a = arrendamientoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Arrendamiento no encontrado."));
        return mapArrendamientoToDTO(a);
    }

    @Override
    public List<ArrendamientoResponseDTO> listarPorMedico(Integer idMedico) {
        return arrendamientoRepository.findByMedicoIdMedico(idMedico).stream()
                .map(this::mapArrendamientoToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArrendamientoResponseDTO> listarContratos(Integer medicoId) {
        List<ArrendamientoModulo> arrendamientos = medicoId != null
                ? arrendamientoRepository.findByMedicoIdMedico(medicoId)
                : arrendamientoRepository.findAll();
        return arrendamientos.stream()
                .map(this::mapArrendamientoToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void darDeBajaArrendamiento(Integer id) {
        ArrendamientoModulo a = arrendamientoRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Arrendamiento no encontrado."));
        a.setEstado("INACTIVO");
        arrendamientoRepository.save(a);
    }

    private ArrendamientoResponseDTO mapArrendamientoToDTO(ArrendamientoModulo a) {
        return ArrendamientoResponseDTO.builder()
                .idArrendamiento(a.getIdArrendamiento())
                .idMedico(a.getMedico().getIdMedico())
                .nombreMedico(a.getMedico().getNombre() + " " + a.getMedico().getApellido())
                .idConsultorio(a.getConsultorio().getIdConsultorio())
                .numeroConsultorio(a.getConsultorio().getNumeroConsultorio())
                .fechaInicio(a.getFechaInicio())
                .fechaFin(a.getFechaFin())
                .diaSemana(a.getDiaSemana())
                .horaInicio(a.getHoraInicio())
                .horaFin(a.getHoraFin())
                .duracionTurnoMin(a.getDuracionTurnoMin())
                .cupoMaximoDiario(a.getCupoMaximoDiario())
                .porcentajeConsultorio(a.getPorcentajeConsultorio())
                .porcentajeMedico(a.getPorcentajeMedico())
                .estado(a.getEstado())
                .observaciones(a.getObservaciones())
                .build();
    }
}
