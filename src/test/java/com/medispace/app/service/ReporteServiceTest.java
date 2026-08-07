package com.medispace.app.service;

import com.medispace.app.dto.reporte.DashboardResponseDTO;
import com.medispace.app.dto.reporte.ReporteFacturacionDTO;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.Medico;
import com.medispace.app.model.Turno;
import com.medispace.app.repository.DashboardGerencialRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.repository.UsoConsultorioRepository;
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

    @Mock
    private UsoConsultorioRepository usoConsultorioRepository;

    @InjectMocks
    private ReporteServiceImpl reporteService;

    @Test
    void testReporteFacturacionPorMedico_AgrupaPorMedicoDistinto() {
        Medico medico1 = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Medico medico2 = Medico.builder().idMedico(2).nombre("Luis").apellido("Diaz").build();

        Facturacion f1 = Facturacion.builder().medico(medico1)
                .importeTotal(new BigDecimal("1000.00")).importeCopago(BigDecimal.ZERO).estadoPago("PAGADO").build();
        Facturacion f2 = Facturacion.builder().medico(medico2)
                .importeTotal(new BigDecimal("2000.00")).importeCopago(BigDecimal.ZERO).estadoPago("PENDIENTE").build();

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
        Medico medico = Medico.builder().idMedico(1).nombre("Ana").apellido("Gomez").build();
        Facturacion f1 = Facturacion.builder().medico(medico).obraSocial("OSDE")
                .importeTotal(new BigDecimal("1000.00")).importeCopago(new BigDecimal("100.00")).build();
        Facturacion f2 = Facturacion.builder().medico(medico).obraSocial("Particular")
                .importeTotal(new BigDecimal("500.00")).importeCopago(BigDecimal.ZERO).build();

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
        when(dashboardRepository.findByFecha(any())).thenReturn(Optional.empty());
        when(dashboardRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        DashboardResponseDTO dto = reporteService.recalcularDashboard(LocalDate.now());

        assertEquals(3, dto.getTotalTurnosDia());
        assertEquals(1, dto.getTurnosAtendidos());
        assertEquals(1, dto.getTurnosCancelados());
        assertEquals(1, dto.getTurnosDisponibles());
        assertEquals(3, dto.getNuevosPacientes());
    }
}
