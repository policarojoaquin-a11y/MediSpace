package com.medispace.app.service;

import com.medispace.app.dto.facturacion.GenerarLiquidacionDTO;
import com.medispace.app.dto.facturacion.LiquidacionResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.LiquidacionMedica;
import com.medispace.app.model.Medico;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.LiquidacionMedicaRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.service.impl.LiquidacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LiquidacionServiceTest {

    @Mock
    private LiquidacionMedicaRepository liquidacionMedicaRepository;

    @Mock
    private FacturacionRepository facturacionRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private LiquidacionServiceImpl liquidacionService;

    private Medico medico;

    @BeforeEach
    void setUp() {
        medico = Medico.builder().idMedico(1).nombre("Dr. Carlos").build();
    }

    @Test
    void testRN005_NoLiquidaSiHayFacturasPendientes() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(any(), any(), any(), any()))
                .thenReturn(2L); // 2 facturas pendientes

        GenerarLiquidacionDTO dto = GenerarLiquidacionDTO.builder()
                .idMedico(1)
                .fechaDesde(LocalDate.now().minusDays(30))
                .fechaHasta(LocalDate.now())
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            liquidacionService.generarLiquidacion(dto);
        });

        assertTrue(ex.getMessage().contains("RN-005"));
        assertTrue(ex.getMessage().contains("sin cobro registrado"));
    }

    @Test
    void testRN007_AnulacionLiquidacionEmitida() {
        LiquidacionMedica liq = LiquidacionMedica.builder()
                .idLiquidacion(5)
                .medico(medico)
                .estado("EMITIDA")
                .build();

        when(liquidacionMedicaRepository.findById(5)).thenReturn(Optional.of(liq));
        when(liquidacionMedicaRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        LiquidacionResponseDTO dto = liquidacionService.anularLiquidacion(5, "Error en el cálculo del período.");

        assertNotNull(dto);
        assertEquals("ANULADA", dto.getEstado());
        assertTrue(dto.getObservaciones().contains("Error en el cálculo del período."));
        verify(liquidacionMedicaRepository, times(1)).save(liq);
    }

    @Test
    void testAnulacionSinMotivoDebeFallar() {
        LiquidacionMedica liq = LiquidacionMedica.builder()
                .idLiquidacion(5)
                .medico(medico)
                .estado("EMITIDA")
                .build();

        when(liquidacionMedicaRepository.findById(5)).thenReturn(Optional.of(liq));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            liquidacionService.anularLiquidacion(5, "  ");
        });

        assertTrue(ex.getMessage().contains("motivo"));
        verify(liquidacionMedicaRepository, never()).save(any());
    }

    @Test
    void testRN007_BloqueaDobleAnulacion() {
        LiquidacionMedica liq = LiquidacionMedica.builder()
                .idLiquidacion(5)
                .medico(medico)
                .estado("ANULADA")
                .build();

        when(liquidacionMedicaRepository.findById(5)).thenReturn(Optional.of(liq));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            liquidacionService.anularLiquidacion(5, "Motivo cualquiera");
        });

        assertTrue(ex.getMessage().contains("RN-007"));
        verify(liquidacionMedicaRepository, never()).save(any());
    }

    @Test
    void testRN018_BloqueaLiquidacionSiHayAtencionesSinCobro() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(any(), any(), any(), any()))
                .thenReturn(0L);
        when(turnoRepository.countAtendidosSinCobro(any(), any(), any())).thenReturn(3L);

        GenerarLiquidacionDTO dto = GenerarLiquidacionDTO.builder()
                .idMedico(1)
                .fechaDesde(LocalDate.now().minusDays(30))
                .fechaHasta(LocalDate.now())
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            liquidacionService.generarLiquidacion(dto);
        });

        assertTrue(ex.getMessage().contains("RN-018"));
        assertTrue(ex.getMessage().contains("atenciones sin cobro"));
    }

    @Test
    void testRN018_PermiteLiquidacionSinAtencionesSinCobro() {
        Facturacion factura = Facturacion.builder()
                .idFacturacion(10)
                .medico(medico)
                .importeTotal(new java.math.BigDecimal("1000.00"))
                .porcentajeMedico(new java.math.BigDecimal("70.00"))
                .porcentajeConsultorio(new java.math.BigDecimal("30.00"))
                .estadoPago("PAGADO")
                .build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(any(), any(), any(), any()))
                .thenReturn(0L);
        when(turnoRepository.countAtendidosSinCobro(any(), any(), any())).thenReturn(0L);
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(any(), any(), any()))
                .thenReturn(java.util.List.of(factura));
        when(liquidacionMedicaRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        GenerarLiquidacionDTO dto = GenerarLiquidacionDTO.builder()
                .idMedico(1)
                .fechaDesde(LocalDate.now().minusDays(30))
                .fechaHasta(LocalDate.now())
                .build();

        LiquidacionResponseDTO response = liquidacionService.generarLiquidacion(dto);

        assertNotNull(response);
        assertEquals("EMITIDA", response.getEstado());
        verify(liquidacionMedicaRepository, times(1)).save(any());
    }

    @Test
    void testRN024_BloqueaLiquidacionSuperpuesta() {
        Facturacion factura = Facturacion.builder()
                .idFacturacion(10).medico(medico)
                .importeTotal(new java.math.BigDecimal("1000.00"))
                .porcentajeMedico(new java.math.BigDecimal("70.00"))
                .porcentajeConsultorio(new java.math.BigDecimal("30.00"))
                .estadoPago("PAGADO").build();
        LiquidacionMedica existente = LiquidacionMedica.builder()
                .idLiquidacion(3).medico(medico).estado("EMITIDA")
                .fechaDesde(LocalDate.now().minusDays(10)).fechaHasta(LocalDate.now().minusDays(1))
                .build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(any(), any(), any(), any()))
                .thenReturn(0L);
        when(turnoRepository.countAtendidosSinCobro(any(), any(), any())).thenReturn(0L);
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(any(), any(), any()))
                .thenReturn(java.util.List.of(factura));
        when(liquidacionMedicaRepository.findSuperpuestas(any(), any(), any()))
                .thenReturn(java.util.List.of(existente));

        GenerarLiquidacionDTO dto = GenerarLiquidacionDTO.builder()
                .idMedico(1)
                .fechaDesde(LocalDate.now().minusDays(5))
                .fechaHasta(LocalDate.now())
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                liquidacionService.generarLiquidacion(dto));

        assertTrue(ex.getMessage().contains("RN-024"));
        verify(liquidacionMedicaRepository, never()).save(any());
    }

    @Test
    void testBUG003_FacturasAnuladasNoSumanAlTotal() {
        Facturacion pagada = Facturacion.builder()
                .idFacturacion(1).medico(medico)
                .importeTotal(new java.math.BigDecimal("20000.00"))
                .porcentajeMedico(new java.math.BigDecimal("70.00"))
                .porcentajeConsultorio(new java.math.BigDecimal("30.00"))
                .estadoPago("PAGADO").build();
        Facturacion anulada = Facturacion.builder()
                .idFacturacion(2).medico(medico)
                .importeTotal(new java.math.BigDecimal("20000.00"))
                .porcentajeMedico(new java.math.BigDecimal("70.00"))
                .porcentajeConsultorio(new java.math.BigDecimal("30.00"))
                .estadoPago("ANULADO").build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(any(), any(), any(), any()))
                .thenReturn(0L);
        when(turnoRepository.countAtendidosSinCobro(any(), any(), any())).thenReturn(0L);
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(any(), any(), any()))
                .thenReturn(java.util.List.of(pagada, anulada));
        when(liquidacionMedicaRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        GenerarLiquidacionDTO dto = GenerarLiquidacionDTO.builder()
                .idMedico(1)
                .fechaDesde(LocalDate.now().minusDays(1))
                .fechaHasta(LocalDate.now())
                .build();

        LiquidacionResponseDTO response = liquidacionService.generarLiquidacion(dto);

        assertEquals(0, new java.math.BigDecimal("20000.00").compareTo(response.getTotalFacturado()),
                "La factura ANULADA no debe sumar al total (esperado 20000, no 40000)");
    }

    @Test
    void testRN025_SplitSeCalculaSobreLoCobradoEnManoNoSobreElTotal() {
        // Consulta $4.800, obra social cubre $20 (Facturacion.importeCopago = 4780, ya es lo
        // que paga el paciente en mano). El 70/30 debe calcularse sobre 4780, no sobre 4800.
        Facturacion factura = Facturacion.builder()
                .idFacturacion(1).medico(medico)
                .importeTotal(new java.math.BigDecimal("4800.00"))
                .importeCopago(new java.math.BigDecimal("4780.00"))
                .porcentajeMedico(new java.math.BigDecimal("70.00"))
                .porcentajeConsultorio(new java.math.BigDecimal("30.00"))
                .estadoPago("PAGADO").build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(any(), any(), any(), any()))
                .thenReturn(0L);
        when(turnoRepository.countAtendidosSinCobro(any(), any(), any())).thenReturn(0L);
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(any(), any(), any()))
                .thenReturn(java.util.List.of(factura));
        when(liquidacionMedicaRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        GenerarLiquidacionDTO dto = GenerarLiquidacionDTO.builder()
                .idMedico(1)
                .fechaDesde(LocalDate.now().minusDays(1))
                .fechaHasta(LocalDate.now())
                .build();

        LiquidacionResponseDTO response = liquidacionService.generarLiquidacion(dto);

        assertEquals(0, new java.math.BigDecimal("4780.00").compareTo(response.getTotalFacturado()));
        assertEquals(0, new java.math.BigDecimal("3346.00").compareTo(response.getTotalMedico()));
        assertEquals(0, new java.math.BigDecimal("1434.00").compareTo(response.getTotalConsultorio()));
    }
}
