package com.medispace.app.service.impl;

import com.medispace.app.dto.reporte.DashboardResponseDTO;
import com.medispace.app.dto.reporte.ReporteFacturacionDTO;
import com.medispace.app.dto.reporte.ReporteUsoConsultorioDTO;
import com.medispace.app.model.DashboardGerencial;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.UsoConsultorio;
import com.medispace.app.repository.*;
import com.medispace.app.service.ReporteService;
import com.medispace.app.util.SplitFinancieroCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    private final DashboardGerencialRepository dashboardRepository;
    private final TurnoRepository turnoRepository;
    private final FacturacionRepository facturacionRepository;
    private final PacienteRepository pacienteRepository;
    private final UsoConsultorioRepository usoConsultorioRepository;

    @Override
    public DashboardResponseDTO getDashboardHoy() {
        LocalDate hoy = LocalDate.now();
        return dashboardRepository.findByFecha(hoy)
                .map(this::mapDashboardToDTO)
                .orElseGet(() -> recalcularDashboard(hoy));
    }

    @Override
    @Transactional
    public DashboardResponseDTO recalcularDashboard(LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        LocalDateTime hasta = fecha.atTime(23, 59, 59);

        // Contar turnos por estado
        var turnos = turnoRepository.findByFechaHoraBetween(desde, hasta);
        long atendidos = turnos.stream().filter(t -> "ATENDIDO".equalsIgnoreCase(t.getEstado())).count();
        long cancelados = turnos.stream().filter(t -> "CANCELADO".equalsIgnoreCase(t.getEstado())).count();
        long noAsistio = turnos.stream().filter(t -> "NO_ASISTIO".equalsIgnoreCase(t.getEstado())).count();
        long disponibles = turnos.stream().filter(t -> "DISPONIBLE".equalsIgnoreCase(t.getEstado())).count();

        // Facturación del día — filtrada a nivel de query, no findAll() + stream
        var facturas = facturacionRepository.findByFechaFacturacionBetween(desde, hasta);
        BigDecimal totalFacturado = facturas.stream().map(Facturacion::getImporteTotal)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendiente = facturas.stream()
                .filter(f -> "PENDIENTE".equalsIgnoreCase(f.getEstadoPago()))
                .map(Facturacion::getImporteTotal).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nuevos pacientes del día — COUNT a nivel de query, no findAll() + stream
        long nuevosPacientes = pacienteRepository.countByFechaCreacionBetween(desde, hasta);

        DashboardGerencial dash = dashboardRepository.findByFecha(fecha)
                .orElse(DashboardGerencial.builder().visible(true).build());
        dash.setFecha(fecha);
        dash.setTotalTurnosDia((int) turnos.size());
        dash.setTurnosAtendidos((int) atendidos);
        dash.setTurnosCancelados((int) cancelados);
        dash.setTurnosNoAsistio((int) noAsistio);
        dash.setTurnosDisponibles((int) disponibles);
        dash.setFacturacionTotalDia(totalFacturado);
        dash.setCobrosPendientes(pendiente);
        dash.setNuevosPacientes((int) nuevosPacientes);
        dash.setFechaActualizacion(LocalDateTime.now());
        dashboardRepository.save(dash);

        return mapDashboardToDTO(dash);
    }

    @Override
    public List<ReporteFacturacionDTO> reporteFacturacionPorMedico(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDT = desde.atStartOfDay();
        LocalDateTime hastaDT = hasta.atTime(23, 59, 59);

        var facturas = facturacionRepository.findByFechaFacturacionBetween(desdeDT, hastaDT).stream()
                .collect(Collectors.groupingBy(f -> f.getMedico().getIdMedico()));

        return facturas.entrySet().stream().map(entry -> {
            var lista = entry.getValue();
            var medico = lista.get(0).getMedico();
            BigDecimal total = lista.stream().map(Facturacion::getImporteTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cobrado = lista.stream().filter(f -> "PAGADO".equalsIgnoreCase(f.getEstadoPago()))
                    .map(Facturacion::getImporteTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pend = lista.stream().filter(f -> "PENDIENTE".equalsIgnoreCase(f.getEstadoPago()))
                    .map(Facturacion::getImporteTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal copago = lista.stream().map(Facturacion::getImporteCopago).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            return ReporteFacturacionDTO.builder()
                    .idMedico(medico.getIdMedico())
                    .nombreMedico(medico.getNombre() + " " + medico.getApellido())
                    .periodo(desde + " al " + hasta)
                    .totalFacturado(total)
                    .totalCobrado(cobrado)
                    .totalPendiente(pend)
                    .totalCopago(copago)
                    .cantidadTurnos((long) lista.size())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<ReporteFacturacionDTO> reporteFacturacionPorObraSocial(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDT = desde.atStartOfDay();
        LocalDateTime hastaDT = hasta.atTime(23, 59, 59);

        var facturas = facturacionRepository.findByFechaFacturacionBetween(desdeDT, hastaDT).stream()
                .collect(Collectors.groupingBy(f -> f.getObraSocial() != null ? f.getObraSocial() : "Particular"));

        return facturas.entrySet().stream().map(entry -> {
            var lista = entry.getValue();
            BigDecimal total = lista.stream().map(Facturacion::getImporteTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal copago = lista.stream().map(Facturacion::getImporteCopago).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            return ReporteFacturacionDTO.builder()
                    .obraSocial(entry.getKey())
                    .periodo(desde + " al " + hasta)
                    .totalFacturado(total)
                    .totalCopago(copago)
                    .cantidadTurnos((long) lista.size())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<ReporteUsoConsultorioDTO> reporteUsoConsultorios(LocalDate desde, LocalDate hasta) {
        return usoConsultorioRepository.findByFechaBetween(desde, hasta).stream()
                .collect(Collectors.groupingBy(u -> u.getConsultorio().getIdConsultorio() + "-" + u.getMedico().getIdMedico()))
                .entrySet().stream().map(entry -> {
                    var lista = entry.getValue();
                    var primero = lista.get(0);
                    BigDecimal totalFact = lista.stream().map(UsoConsultorio::getFacturacionGenerada).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    int totalPac = lista.stream().mapToInt(u -> u.getCantidadPacientes() != null ? u.getCantidadPacientes() : 0).sum();

                    // Split 30/70 hardcodeado (no leído de ArrendamientoModulo): un uso agrupa
                    // múltiples registros que pueden abarcar distintos contratos en el tiempo,
                    // a diferencia de una Facturacion (1 turno = 1 contrato vigente). Se deja
                    // documentado como pendiente en vez de resolverlo acá — ver auditoría.
                    SplitFinancieroCalculator.Split split = SplitFinancieroCalculator.calcular(
                            totalFact, new BigDecimal("70"), new BigDecimal("30"));

                    return ReporteUsoConsultorioDTO.builder()
                            .idConsultorio(primero.getConsultorio().getIdConsultorio())
                            .numeroConsultorio(primero.getConsultorio().getNumeroConsultorio())
                            .idMedico(primero.getMedico().getIdMedico())
                            .nombreMedico(primero.getMedico().getNombre() + " " + primero.getMedico().getApellido())
                            .periodo(desde + " al " + hasta)
                            .totalSesiones(lista.size())
                            .totalPacientesAtendidos(totalPac)
                            .facturacionGenerada(totalFact)
                            .importeConsultorio(split.parteConsultorio())
                            .importeMedico(split.parteMedico())
                            .build();
                }).collect(Collectors.toList());
    }

    private DashboardResponseDTO mapDashboardToDTO(DashboardGerencial d) {
        return DashboardResponseDTO.builder()
                .fecha(d.getFecha())
                .totalTurnosDia(d.getTotalTurnosDia())
                .turnosAtendidos(d.getTurnosAtendidos())
                .turnosCancelados(d.getTurnosCancelados())
                .turnosNoAsistio(d.getTurnosNoAsistio())
                .turnosDisponibles(d.getTurnosDisponibles())
                .facturacionTotalDia(d.getFacturacionTotalDia())
                .cobrosPendientes(d.getCobrosPendientes())
                .nuevosPacientes(d.getNuevosPacientes())
                .fechaActualizacion(d.getFechaActualizacion())
                .build();
    }
}
