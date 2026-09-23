package com.medispace.app.service.impl;

import com.medispace.app.dto.reporte.DashboardResponseDTO;
import com.medispace.app.dto.reporte.ReporteFacturacionDTO;
import com.medispace.app.dto.reporte.ReporteUsoConsultorioDTO;
import com.medispace.app.model.DashboardGerencial;
import com.medispace.app.model.Facturacion;
import com.medispace.app.repository.*;
import com.medispace.app.service.ReporteService;
import com.medispace.app.util.SplitFinancieroCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    private final DashboardGerencialRepository dashboardRepository;
    private final TurnoRepository turnoRepository;
    private final FacturacionRepository facturacionRepository;
    private final PacienteRepository pacienteRepository;

    @Override
    @Transactional
    public DashboardResponseDTO getDashboardHoy() {
        // El día en curso es información viva: durante toda la jornada se atienden turnos, se
        // factura y se dan de alta pacientes. Devolver la primera "foto" guardada del día (lo
        // que hacía findFirst + orElseGet) dejaba el dashboard congelado en los valores de la
        // mañana — normalmente casi todo en cero — y el botón "Actualizar" de la UI no cambiaba
        // nada porque el GET seguía devolviendo esa misma fila. Para "hoy" siempre se recalcula;
        // recalcularDashboard igual persiste/actualiza la fila (RF-R4: la tabla
        // Dashboard_Gerencial queda como registro del día). Para fechas pasadas, el endpoint
        // /dashboard/recalcular sigue disponible y la fila guardada es un histórico válido.
        return recalcularDashboard(LocalDate.now());
    }

    @Override
    @Transactional
    public DashboardResponseDTO recalcularDashboard(LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        // atTime(23,59,59) dejaba fuera lo facturado/agendado en el último segundo del día
        // (23:59:59.000001 .. 23:59:59.999999). LocalTime.MAX cubre el día completo — mismo
        // criterio que FacturacionServiceImpl.
        LocalDateTime hasta = fecha.atTime(LocalTime.MAX);

        // Contar turnos por estado
        var turnos = turnoRepository.findByFechaHoraBetween(desde, hasta);
        long atendidos = turnos.stream().filter(t -> "ATENDIDO".equalsIgnoreCase(t.getEstado())).count();
        long cancelados = turnos.stream().filter(t -> "CANCELADO".equalsIgnoreCase(t.getEstado())).count();
        long noAsistio = turnos.stream().filter(t -> "NO_ASISTIO".equalsIgnoreCase(t.getEstado())).count();
        long disponibles = turnos.stream().filter(t -> "DISPONIBLE".equalsIgnoreCase(t.getEstado())).count();

        // Facturación del día — filtrada a nivel de query, no findAll() + stream. Se descartan
        // las facturas ANULADO / REINTEGRADO: cuando un turno EN_ESPERA (que ya generó su
        // factura pendiente) se cancela, anularFacturacionDeTurno() la deja en ANULADO pero
        // conserva su Importe_Total — sumarla infla la "Facturación del Día" con plata que
        // nunca se facturó de verdad.
        // RN-025: se suma lo efectivamente cobrado en mano (no el precio de lista) — el coseguro
        // que la obra social le paga al médico directo nunca entra a la caja del consultorio.
        var facturas = facturacionRepository.findByFechaFacturacionBetween(desde, hasta);
        BigDecimal totalFacturado = facturas.stream()
                .filter(f -> !"ANULADO".equalsIgnoreCase(f.getEstadoPago())
                        && !"REINTEGRADO".equalsIgnoreCase(f.getEstadoPago()))
                .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendiente = facturas.stream()
                .filter(f -> "PENDIENTE".equalsIgnoreCase(f.getEstadoPago()))
                .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nuevos pacientes del día — COUNT a nivel de query, no findAll() + stream
        long nuevosPacientes = pacienteRepository.countByFechaCreacionBetween(desde, hasta);

        DashboardGerencial dash = dashboardRepository.findFirstByFechaOrderByIdDashboardDesc(fecha)
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
        // LocalTime.MAX cubre el día completo (23:59:59.999999999) — mismo criterio que el
        // dashboard, reporteUsoConsultorios y FacturacionServiceImpl. atTime(23,59,59) dejaba
        // fuera lo facturado en la última fracción del día.
        LocalDateTime hastaDT = hasta.atTime(LocalTime.MAX);

        // Se excluyen ANULADO/REINTEGRADO antes de agrupar — mismo criterio que
        // ReporteServiceImpl.recalcularDashboard (Dashboard Gerencial): conservan su
        // Importe_Total pero no representan plata realmente facturada.
        var facturas = facturacionRepository.findByFechaFacturacionBetween(desdeDT, hastaDT).stream()
                .filter(f -> !"ANULADO".equalsIgnoreCase(f.getEstadoPago()) && !"REINTEGRADO".equalsIgnoreCase(f.getEstadoPago()))
                .collect(Collectors.groupingBy(f -> f.getMedico().getIdMedico()));

        return facturas.entrySet().stream().map(entry -> {
            var lista = entry.getValue();
            var medico = lista.get(0).getMedico();
            // RN-025: los totales reflejan lo cobrado en mano; totalCubiertoOs es lo que
            // corresponde reclamarle a la obra social (informativo, el médico ya lo cobra aparte).
            BigDecimal total = lista.stream()
                    .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cobrado = lista.stream().filter(f -> "PAGADO".equalsIgnoreCase(f.getEstadoPago()))
                    .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pend = lista.stream().filter(f -> "PENDIENTE".equalsIgnoreCase(f.getEstadoPago()))
                    .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cubiertoOs = lista.stream()
                    .map(f -> SplitFinancieroCalculator.montoCubiertoPorObraSocial(f.getImporteTotal(), f.getImporteCopago()))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            return ReporteFacturacionDTO.builder()
                    .idMedico(medico.getIdMedico())
                    .nombreMedico(medico.getNombre() + " " + medico.getApellido())
                    .periodo(desde + " al " + hasta)
                    .totalFacturado(total)
                    .totalCobrado(cobrado)
                    .totalPendiente(pend)
                    .totalCubiertoOs(cubiertoOs)
                    .cantidadTurnos((long) lista.size())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<ReporteFacturacionDTO> reporteFacturacionPorObraSocial(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDT = desde.atStartOfDay();
        LocalDateTime hastaDT = hasta.atTime(LocalTime.MAX);

        // Se excluyen ANULADO/REINTEGRADO — mismo criterio que el resto de los reportes
        // financieros y el Dashboard Gerencial.
        var facturas = facturacionRepository.findByFechaFacturacionBetween(desdeDT, hastaDT).stream()
                .filter(f -> !"ANULADO".equalsIgnoreCase(f.getEstadoPago()) && !"REINTEGRADO".equalsIgnoreCase(f.getEstadoPago()))
                .collect(Collectors.groupingBy(f -> f.getObraSocial() != null ? f.getObraSocial() : "Particular"));

        return facturas.entrySet().stream().map(entry -> {
            var lista = entry.getValue();
            // RN-025: totalFacturado = lo cobrado en mano; totalCubiertoOs = lo que corresponde
            // reclamarle a esta obra social en el período (útil para saber cuánto cobrarle).
            BigDecimal total = lista.stream()
                    .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cubiertoOs = lista.stream()
                    .map(f -> SplitFinancieroCalculator.montoCubiertoPorObraSocial(f.getImporteTotal(), f.getImporteCopago()))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

            return ReporteFacturacionDTO.builder()
                    .obraSocial(entry.getKey())
                    .periodo(desde + " al " + hasta)
                    .totalFacturado(total)
                    .totalCubiertoOs(cubiertoOs)
                    .cantidadTurnos((long) lista.size())
                    .build();
        }).collect(Collectors.toList());
    }

    // El reporte de uso de consultorios se arma desde las Facturaciones del período (cada
    // Facturacion = 1 turno atendido, con su consultorio y los porcentajes YA resueltos del
    // contrato vigente en ese momento). Antes se leía de la tabla Uso_Consultorio, que solo se
    // llena vía un endpoint sin botón en la UI — por eso el reporte "no funcionaba" (siempre
    // vacío) y usaba un split 70/30 hardcodeado. Ver checklist 27/08 y docs/ANEXO.
    @Override
    @Transactional
    public List<ReporteUsoConsultorioDTO> reporteUsoConsultorios(LocalDate desde, LocalDate hasta) {
        LocalDateTime desdeDT = desde.atStartOfDay();
        LocalDateTime hastaDT = hasta.atTime(LocalTime.MAX);

        // Se excluyen ANULADO/REINTEGRADO — mismo criterio que el resto de los reportes
        // financieros y el Dashboard Gerencial.
        return facturacionRepository.findByFechaFacturacionBetween(desdeDT, hastaDT).stream()
                .filter(f -> f.getTurno() != null && f.getTurno().getConsultorio() != null)
                .filter(f -> !"ANULADO".equalsIgnoreCase(f.getEstadoPago()) && !"REINTEGRADO".equalsIgnoreCase(f.getEstadoPago()))
                .collect(Collectors.groupingBy(f -> f.getTurno().getConsultorio().getIdConsultorio() + "-" + f.getMedico().getIdMedico()))
                .entrySet().stream().map(entry -> {
                    var lista = entry.getValue();
                    var primero = lista.get(0);
                    var consultorio = primero.getTurno().getConsultorio();

                    // RN-025: se factura/reparte sobre lo cobrado en mano, no sobre el precio de
                    // lista — el coseguro que la obra social le paga al médico directo nunca
                    // entra a la caja del consultorio.
                    BigDecimal totalFact = lista.stream()
                            .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal parteConsultorio = lista.stream()
                            .map(f -> SplitFinancieroCalculator.calcular(
                                    SplitFinancieroCalculator.montoCobradoEnMano(
                                            f.getImporteTotal() != null ? f.getImporteTotal() : BigDecimal.ZERO, f.getImporteCopago()),
                                    f.getPorcentajeMedico() != null ? f.getPorcentajeMedico() : new BigDecimal("70"),
                                    f.getPorcentajeConsultorio() != null ? f.getPorcentajeConsultorio() : new BigDecimal("30")
                            ).parteConsultorio())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal parteMedico = totalFact.subtract(parteConsultorio);
                    long pacientesDistintos = lista.stream()
                            .map(f -> f.getPaciente() != null ? f.getPaciente().getIdPaciente() : null)
                            .filter(Objects::nonNull).distinct().count();

                    return ReporteUsoConsultorioDTO.builder()
                            .idConsultorio(consultorio.getIdConsultorio())
                            .numeroConsultorio(consultorio.getNumeroConsultorio())
                            .idMedico(primero.getMedico().getIdMedico())
                            .nombreMedico(primero.getMedico().getNombre() + " " + primero.getMedico().getApellido())
                            .periodo(desde + " al " + hasta)
                            .totalSesiones(lista.size())
                            .totalPacientesAtendidos((int) pacientesDistintos)
                            .facturacionGenerada(totalFact)
                            .importeConsultorio(parteConsultorio)
                            .importeMedico(parteMedico)
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
