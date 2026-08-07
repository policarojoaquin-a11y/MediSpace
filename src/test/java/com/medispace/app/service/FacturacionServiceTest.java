package com.medispace.app.service;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.model.ArrendamientoModulo;
import com.medispace.app.model.Cobro;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.Medico;
import com.medispace.app.model.Paciente;
import com.medispace.app.model.Turno;
import com.medispace.app.repository.ArrendamientoModuloRepository;
import com.medispace.app.repository.CobroRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.service.impl.FacturacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FacturacionServiceTest {

    @Mock
    private FacturacionRepository facturacionRepository;

    @Mock
    private CobroRepository cobroRepository;

    @Mock
    private ArrendamientoModuloRepository arrendamientoModuloRepository;

    @InjectMocks
    private FacturacionServiceImpl facturacionService;

    private Turno turno;
    private Medico medico;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        medico = Medico.builder().idMedico(1).importeConsulta(new BigDecimal("10000.00")).build();
        paciente = Paciente.builder().idPaciente(10).nombre("Juan").apellido("Perez").build();
        turno = Turno.builder().idTurno(100).medico(medico).paciente(paciente).estado("ATENDIDO").build();
    }

    @Test
    void testRN006_SinContratoVigenteUsaSplitDeFallback7030() {
        // turno sin consultorio asociado (p. ej. datos de test/seed) -> no hay contrato que
        // resolver, se mantiene el fallback 70/30 y idArrendamiento queda null.
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turno);

        assertNotNull(dto);
        assertEquals(new BigDecimal("70.00"), dto.getPorcentajeMedico());
        assertEquals(new BigDecimal("30.00"), dto.getPorcentajeConsultorio());
        assertEquals(new BigDecimal("10000.00"), dto.getImporteTotal());
        assertEquals("PENDIENTE", dto.getEstadoPago());

        ArgumentCaptor<Facturacion> captor = ArgumentCaptor.forClass(Facturacion.class);
        verify(facturacionRepository).save(captor.capture());
        assertNull(captor.getValue().getIdArrendamiento());
    }

    @Test
    void testRN006_UsaSplitDelContratoVigenteEnVezDe7030() {
        Consultorio consultorio = Consultorio.builder().idConsultorio(5).numeroConsultorio("101").build();
        Turno turnoConConsultorio = Turno.builder()
                .idTurno(100)
                .medico(medico)
                .paciente(paciente)
                .consultorio(consultorio)
                .estado("ATENDIDO")
                .fechaHora(LocalDateTime.of(2026, 8, 10, 10, 0)) // lunes
                .build();

        ArrendamientoModulo contrato = ArrendamientoModulo.builder()
                .idArrendamiento(55)
                .porcentajeMedico(new BigDecimal("60.00"))
                .porcentajeConsultorio(new BigDecimal("40.00"))
                .build();

        when(arrendamientoModuloRepository.findContratoVigente(any(), any(), any(), any(), any()))
                .thenReturn(List.of(contrato));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turnoConConsultorio);

        assertEquals(new BigDecimal("60.00"), dto.getPorcentajeMedico());
        assertEquals(new BigDecimal("40.00"), dto.getPorcentajeConsultorio());

        ArgumentCaptor<Facturacion> captor = ArgumentCaptor.forClass(Facturacion.class);
        verify(facturacionRepository).save(captor.capture());
        assertEquals(55, captor.getValue().getIdArrendamiento());
    }

    @Test
    void testCrearFacturacionAutomatica_UsaDatosPlanificadosDelTurnoEnVezDePerderlos() {
        Turno turnoConDatosDeReserva = Turno.builder()
                .idTurno(100)
                .medico(medico)
                .paciente(paciente)
                .estado("ATENDIDO")
                .tipoConsulta("ESTUDIO")
                .metodoPagoPlanificado("TRANSFERENCIA")
                .obraSocialPlanificada("OSDE")
                .importeCopagoPlanificado(new BigDecimal("1500.00"))
                .build();
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turnoConDatosDeReserva);

        assertEquals("ESTUDIO", dto.getTipoConsulta());
        assertEquals("OSDE", dto.getObraSocial());
        assertEquals(new BigDecimal("1500.00"), dto.getImporteCopago());
        assertEquals("TRANSFERENCIA", dto.getMetodoPagoPlanificado());
        // El estado real de pago sigue naciendo PENDIENTE — el método planificado es solo
        // para prellenar el formulario de cobro, no reemplaza el flujo de registrarCobro.
        assertEquals("PENDIENTE", dto.getMetodoPago());
    }

    private Facturacion facturacionPendiente() {
        return Facturacion.builder()
                .idFacturacion(1)
                .turno(turno)
                .paciente(paciente)
                .medico(medico)
                .tipoConsulta("Consulta Médica")
                .metodoPago("PENDIENTE")
                .obraSocial("Particular")
                .importeTotal(new BigDecimal("10000.00"))
                .importeCopago(BigDecimal.ZERO)
                .porcentajeMedico(new BigDecimal("70.00"))
                .porcentajeConsultorio(new BigDecimal("30.00"))
                .estadoPago("PENDIENTE")
                .visible(true)
                .build();
    }

    @Test
    void testRegistrarCobro_PersisteImporteCubiertoOsCorrectamente() {
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(facturacionPendiente()));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder()
                .metodoPago("EFECTIVO")
                .importeTotal(new BigDecimal("10000.00"))
                .importeCubiertoOs(new BigDecimal("3000.00"))
                .importeCopago(new BigDecimal("500.00"))
                .build();

        facturacionService.registrarCobro(1, dto);

        ArgumentCaptor<Cobro> captor = ArgumentCaptor.forClass(Cobro.class);
        verify(cobroRepository, times(1)).save(captor.capture());
        assertEquals(new BigDecimal("3000.00"), captor.getValue().getImporteCubiertoOs());
    }

    @Test
    void testRegistrarCobro_TransferenciaQuedaPendiente() {
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(facturacionPendiente()));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder()
                .metodoPago("TRANSFERENCIA")
                .importeTotal(new BigDecimal("10000.00"))
                .build();

        FacturacionResponseDTO response = facturacionService.registrarCobro(1, dto);

        assertEquals("PENDIENTE", response.getEstadoPago());
    }

    @Test
    void testRegistrarCobro_EfectivoQuedaPagado() {
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(facturacionPendiente()));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder()
                .metodoPago("EFECTIVO")
                .importeTotal(new BigDecimal("10000.00"))
                .build();

        FacturacionResponseDTO response = facturacionService.registrarCobro(1, dto);

        assertEquals("PAGADO", response.getEstadoPago());
    }
}
