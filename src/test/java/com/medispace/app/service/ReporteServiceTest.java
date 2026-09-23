package com.medispace.app.service;

import com.medispace.app.dto.reporte.DashboardResponseDTO;
import com.medispace.app.dto.reporte.ReporteFacturacionDTO;
import com.medispace.app.model.DashboardGerencial;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.Medico;
import com.medispace.app.model.Turno;
import com.medispace.app.model.Consultorio;
import com.medispace.app.repository.DashboardGerencialRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.service.impl.ReporteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReporteServiceTest {

    @Mock
    private DashboardGerencialRepository dashboardRepository;

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private FacturacionRepository facturacionRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private ReporteServiceImpl reporteService;

    @Test
    void testReporteFacturacionPorMedico_AgrupaPorMedicoDistinto() {
        Medico medico1 = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Medico medico2 = Medico.builder().idMedico(2).nombre("Luis").apellido("Diaz").build();

        // Sin importeCopago explícito: cae al fallback (= importeTotal, RN-025), foco de este
        // test es el agrupamiento por médico, no la distinción cobrado-en-mano/cubierto-OS.
        Facturacion f1 = Facturacion.builder().medico(medico1)
                .importeTotal(new BigDecimal("1000.00")).estadoPago("PAGADO").build();
        Facturacion f2 = Facturacion.builder().medico(medico2)
                .importeTotal(new BigDecimal("2000.00")).estadoPago("PENDIENTE").build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(f1, f2));

        List<ReporteFacturacionDTO> reporte = reporteService.reporteFacturacionPorMedico(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(2, reporte.size());
        assertTrue(reporte.stream().anyMatch(r ->
                r.getIdMedico().equals(1) && r.getTotalFacturado().compareTo(new BigDecimal("1000.00")) == 0));
        assertTrue(reporte.stream().anyMatch(r ->
                r.getIdMedico().equals(2) && r.getTotalPendiente().compareTo(new BigDecimal("2000.00")) == 0));
    }

    @Test
    void testReporteFacturacionPorObraSocial_AgrupaPorObraSocial() {
        // Sin importeCopago explícito: cae al fallback (= importeTotal, RN-025), foco de este
        // test es el agrupamiento por obra social, no la distinción cobrado-en-mano/cubierto-OS
        // (esa se cubre en testReporteFacturacionPorObraSocial_CalculaTotalCubiertoOs).
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Facturacion f1 = Facturacion.builder().medico(medico).obraSocial("OSDE")
                .importeTotal(new BigDecimal("1000.00")).build();
        Facturacion f2 = Facturacion.builder().medico(medico).obraSocial("Particular")
                .importeTotal(new BigDecimal("500.00")).build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(f1, f2));

        List<ReporteFacturacionDTO> reporte = reporteService.reporteFacturacionPorObraSocial(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(2, reporte.size());
        assertTrue(reporte.stream().anyMatch(r ->
                "OSDE".equals(r.getObraSocial()) && r.getTotalFacturado().compareTo(new BigDecimal("1000.00")) == 0));
        assertTrue(reporte.stream().anyMatch(r ->
                "Particular".equals(r.getObraSocial()) && r.getTotalFacturado().compareTo(new BigDecimal("500.00")) == 0));
    }

    @Test
    void testRecalcularDashboard_CuentaEstadosDeTurnoYNuevosPacientes() {
        Turno atendido = Turno.builder().estado("ATENDIDO").build();
        Turno cancelado = Turno.builder().estado("CANCELADO").build();
        Turno disponible = Turno.builder().estado("DISPONIBLE").build();

        when(turnoRepository.findByFechaHoraBetween(any(), any())).thenReturn(List.of(atendido, cancelado, disponible));
        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of());
        when(pacienteRepository.countByFechaCreacionBetween(any(), any())).thenReturn(3L);
        when(dashboardRepository.findFirstByFechaOrderByIdDashboardDesc(any())).thenReturn(Optional.empty());
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        DashboardResponseDTO dto = reporteService.recalcularDashboard(LocalDate.now());

        assertEquals(3, dto.getTotalTurnosDia());
        assertEquals(1, dto.getTurnosAtendidos());
        assertEquals(1, dto.getTurnosCancelados());
        assertEquals(1, dto.getTurnosDisponibles());
        assertEquals(3, dto.getNuevosPacientes());
    }

    @Test
    void testGetDashboardHoy_RecalculaAunqueYaExistaLaFotoDelDia() {
        // Regresión: getDashboardHoy devolvía la fila cacheada del día si existía y solo
        // recalculaba cuando NO había ninguna. La foto se creaba a la mañana (casi todo en 0) y
        // quedaba congelada toda la jornada. Ahora "hoy" siempre se recalcula.
        DashboardGerencial fotoVieja = DashboardGerencial.builder()
                .fecha(LocalDate.now())
                .totalTurnosDia(0).turnosAtendidos(0).turnosCancelados(0)
                .turnosNoAsistio(0).turnosDisponibles(0)
                .facturacionTotalDia(BigDecimal.ZERO).cobrosPendientes(BigDecimal.ZERO)
                .nuevosPacientes(0)
                .build();

        Turno atendido1 = Turno.builder().estado("ATENDIDO").build();
        Turno atendido2 = Turno.builder().estado("ATENDIDO").build();
        Facturacion factura = Facturacion.builder()
                .importeTotal(new BigDecimal("5000.00")).estadoPago("PAGADO").build();

        when(dashboardRepository.findFirstByFechaOrderByIdDashboardDesc(any()))
                .thenReturn(Optional.of(fotoVieja));
        when(turnoRepository.findByFechaHoraBetween(any(), any()))
                .thenReturn(List.of(atendido1, atendido2));
        when(facturacionRepository.findByFechaFacturacionBetween(any(), any()))
                .thenReturn(List.of(factura));
        when(pacienteRepository.countByFechaCreacionBetween(any(), any())).thenReturn(4L);
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        DashboardResponseDTO dto = reporteService.getDashboardHoy();

        assertEquals(2, dto.getTotalTurnosDia());
        assertEquals(2, dto.getTurnosAtendidos());
        assertEquals(4, dto.getNuevosPacientes());
        assertEquals(0, dto.getFacturacionTotalDia().compareTo(new BigDecimal("5000.00")));
        verify(dashboardRepository).save(any());
    }

    @Test
    void testRecalcularDashboard_ExcluyeFacturasAnuladasDelTotal() {
        Facturacion pagada = Facturacion.builder()
                .importeTotal(new BigDecimal("1000.00")).estadoPago("PAGADO").build();
        Facturacion pendiente = Facturacion.builder()
                .importeTotal(new BigDecimal("800.00")).estadoPago("PENDIENTE").build();
        Facturacion anulada = Facturacion.builder()
                .importeTotal(new BigDecimal("500.00")).estadoPago("ANULADO").build();
        Facturacion reintegrada = Facturacion.builder()
                .importeTotal(new BigDecimal("300.00")).estadoPago("REINTEGRADO").build();

        when(turnoRepository.findByFechaHoraBetween(any(), any())).thenReturn(List.of());
        when(facturacionRepository.findByFechaFacturacionBetween(any(), any()))
                .thenReturn(List.of(pagada, pendiente, anulada, reintegrada));
        when(pacienteRepository.countByFechaCreacionBetween(any(), any())).thenReturn(0L);
        when(dashboardRepository.findFirstByFechaOrderByIdDashboardDesc(any())).thenReturn(Optional.empty());
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        DashboardResponseDTO dto = reporteService.recalcularDashboard(LocalDate.now());

        // 1000 + 800, sin la anulada (500) ni la reintegrada (300)
        assertEquals(0, dto.getFacturacionTotalDia().compareTo(new BigDecimal("1800.00")));
        // Cobros pendientes = solo la PENDIENTE
        assertEquals(0, dto.getCobrosPendientes().compareTo(new BigDecimal("800.00")));
    }

    @Test
    void testReporteUsoConsultorios_SeArmaDesdeFacturacionConSplitReal() {
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Consultorio consultorio = Consultorio.builder().idConsultorio(7).numeroConsultorio("101").build();
        com.medispace.app.model.Paciente pac = com.medispace.app.model.Paciente.builder().idPaciente(50).build();
        Turno turno = Turno.builder().idTurno(200).consultorio(consultorio).medico(medico).build();

        Facturacion f = Facturacion.builder()
                .turno(turno).medico(medico).paciente(pac)
                .importeTotal(new BigDecimal("1000.00"))
                .porcentajeMedico(new BigDecimal("60"))
                .porcentajeConsultorio(new BigDecimal("40"))
                .build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(f));

        var reporte = reporteService.reporteUsoConsultorios(LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(1, reporte.size());
        var fila = reporte.get(0);
        assertEquals("101", fila.getNumeroConsultorio());
        assertEquals(1, fila.getTotalSesiones());
        assertEquals(1, fila.getTotalPacientesAtendidos());
        assertEquals(0, fila.getImporteConsultorio().compareTo(new BigDecimal("400.00")));
        assertEquals(0, fila.getImporteMedico().compareTo(new BigDecimal("600.00")));
    }

    @Test
    void testBUG003_ReporteFacturacionPorMedico_ExcluyeAnuladasYReintegradas() {
        Medico medico1 = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();

        Facturacion pagada = Facturacion.builder().medico(medico1)
                .importeTotal(new BigDecimal("1000.00")).estadoPago("PAGADO").build();
        Facturacion anulada = Facturacion.builder().medico(medico1)
                .importeTotal(new BigDecimal("1000.00")).estadoPago("ANULADO").build();
        Facturacion reintegrada = Facturacion.builder().medico(medico1)
                .importeTotal(new BigDecimal("1000.00")).estadoPago("REINTEGRADO").build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any()))
                .thenReturn(List.of(pagada, anulada, reintegrada));

        List<ReporteFacturacionDTO> reporte = reporteService.reporteFacturacionPorMedico(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(1, reporte.size());
        assertEquals(0, reporte.get(0).getTotalFacturado().compareTo(new BigDecimal("1000.00")),
                "No debe incluir la ANULADA ni la REINTEGRADA (esperado 1000, no 3000)");
    }

    @Test
    void testBUG003_ReporteFacturacionPorObraSocial_ExcluyeAnuladas() {
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Facturacion pagada = Facturacion.builder().medico(medico).obraSocial("OSDE")
                .importeTotal(new BigDecimal("1000.00")).estadoPago("PAGADO").build();
        Facturacion anulada = Facturacion.builder().medico(medico).obraSocial("OSDE")
                .importeTotal(new BigDecimal("500.00")).estadoPago("ANULADO").build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any()))
                .thenReturn(List.of(pagada, anulada));

        List<ReporteFacturacionDTO> reporte = reporteService.reporteFacturacionPorObraSocial(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(1, reporte.size());
        assertEquals(0, reporte.get(0).getTotalFacturado().compareTo(new BigDecimal("1000.00")),
                "No debe incluir la ANULADA (esperado 1000, no 1500)");
    }

    @Test
    void testBUG003_ReporteUsoConsultorios_ExcluyeAnuladas() {
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Consultorio consultorio = Consultorio.builder().idConsultorio(7).numeroConsultorio("101").build();
        Turno turno = Turno.builder().idTurno(200).consultorio(consultorio).medico(medico).build();

        Facturacion pagada = Facturacion.builder().turno(turno).medico(medico)
                .importeTotal(new BigDecimal("1000.00"))
                .porcentajeMedico(new BigDecimal("70")).porcentajeConsultorio(new BigDecimal("30"))
                .estadoPago("PAGADO").build();
        Facturacion anulada = Facturacion.builder().turno(turno).medico(medico)
                .importeTotal(new BigDecimal("1000.00"))
                .porcentajeMedico(new BigDecimal("70")).porcentajeConsultorio(new BigDecimal("30"))
                .estadoPago("ANULADO").build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(pagada, anulada));

        var reporte = reporteService.reporteUsoConsultorios(LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(1, reporte.size());
        assertEquals(1, reporte.get(0).getTotalSesiones(), "Solo debe contar la sesion PAGADA, no la ANULADA");
        assertEquals(0, reporte.get(0).getFacturacionGenerada().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void testReporteFacturacionPorMedico_UsaLoCobradoEnManoYCalculaTotalCubiertoOs() {
        // RN-025: consulta $4.800, obra social cubre $20 (importeCopago = 4780, lo que paga el
        // paciente en mano). totalFacturado debe ser 4780, totalCubiertoOs debe ser 20.
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Facturacion f = Facturacion.builder().medico(medico)
                .importeTotal(new BigDecimal("4800.00")).importeCopago(new BigDecimal("4780.00"))
                .estadoPago("PAGADO").build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(f));

        List<ReporteFacturacionDTO> reporte = reporteService.reporteFacturacionPorMedico(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(1, reporte.size());
        assertEquals(0, reporte.get(0).getTotalFacturado().compareTo(new BigDecimal("4780.00")));
        assertEquals(0, reporte.get(0).getTotalCubiertoOs().compareTo(new BigDecimal("20.00")));
    }

    @Test
    void testReporteFacturacionPorObraSocial_CalculaTotalCubiertoOs() {
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Facturacion f = Facturacion.builder().medico(medico).obraSocial("OSDE")
                .importeTotal(new BigDecimal("4800.00")).importeCopago(new BigDecimal("4780.00")).build();

        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(f));

        List<ReporteFacturacionDTO> reporte = reporteService.reporteFacturacionPorObraSocial(
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(1, reporte.size());
        assertEquals(0, reporte.get(0).getTotalFacturado().compareTo(new BigDecimal("4780.00")));
        assertEquals(0, reporte.get(0).getTotalCubiertoOs().compareTo(new BigDecimal("20.00")));
    }

    @Test
    void testRecalcularDashboard_UsaLoCobradoEnManoCuandoHayObraSocial() {
        Facturacion f = Facturacion.builder()
                .importeTotal(new BigDecimal("4800.00")).importeCopago(new BigDecimal("4780.00"))
                .estadoPago("PAGADO").build();

        when(turnoRepository.findByFechaHoraBetween(any(), any())).thenReturn(List.of());
        when(facturacionRepository.findByFechaFacturacionBetween(any(), any())).thenReturn(List.of(f));
        when(pacienteRepository.countByFechaCreacionBetween(any(), any())).thenReturn(0L);
        when(dashboardRepository.findFirstByFechaOrderByIdDashboardDesc(any())).thenReturn(Optional.empty());
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        DashboardResponseDTO dto = reporteService.recalcularDashboard(LocalDate.now());

        assertEquals(0, dto.getFacturacionTotalDia().compareTo(new BigDecimal("4780.00")));
    }
}
