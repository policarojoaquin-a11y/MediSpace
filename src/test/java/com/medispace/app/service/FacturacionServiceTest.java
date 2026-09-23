package com.medispace.app.service;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.ArrendamientoModulo;
import com.medispace.app.model.Cobro;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.Medico;
import com.medispace.app.model.MedicoPrestacion;
import com.medispace.app.model.Paciente;
import com.medispace.app.model.PrestacionMedica;
import com.medispace.app.model.Turno;
import com.medispace.app.repository.ArrendamientoModuloRepository;
import com.medispace.app.repository.CobroRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.MedicoPrestacionRepository;
import com.medispace.app.service.impl.FacturacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    @Mock
    private MedicoPrestacionRepository medicoPrestacionRepository;

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
    void testCrearFacturacionAutomatica_UsaImporteParticularDeLaPrestacionElegida() {
        PrestacionMedica ecografia = PrestacionMedica.builder().idPrestacion(7).nombre("Ecografía").build();
        Turno turnoConPrestacion = Turno.builder()
                .idTurno(100).medico(medico).paciente(paciente).estado("ATENDIDO")
                .prestacion(ecografia)
                .build();
        MedicoPrestacion mp = MedicoPrestacion.builder()
                .idMedicoPrestacion(3).medico(medico).prestacion(ecografia)
                .importeParticular(new BigDecimal("6000.00")).build();

        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 7))
                .thenReturn(Optional.of(mp));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turnoConPrestacion);

        // El importe total sale del precio de la prestación, no del importeConsulta (10000) del médico.
        assertEquals(0, dto.getImporteTotal().compareTo(new BigDecimal("6000.00")));
    }

    @Test
    void testCrearFacturacionAutomatica_CaeAImporteConsultaSiLaPrestacionNoTienePrecio() {
        PrestacionMedica consulta = PrestacionMedica.builder().idPrestacion(9).nombre("Consulta general").build();
        Turno turnoConPrestacion = Turno.builder()
                .idTurno(100).medico(medico).paciente(paciente).estado("ATENDIDO")
                .prestacion(consulta)
                .build();
        MedicoPrestacion mpSinPrecio = MedicoPrestacion.builder()
                .idMedicoPrestacion(4).medico(medico).prestacion(consulta).importeParticular(null).build();

        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 9))
                .thenReturn(Optional.of(mpSinPrecio));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turnoConPrestacion);

        assertEquals(0, dto.getImporteTotal().compareTo(new BigDecimal("10000.00")));
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

    @Test
    void testCrearFacturacionAutomatica_ConCoseguroExponeImporteCubiertoOsCorrecto() {
        // RN-025: consulta $4.800, obra social cubre $20 — Turno.importeCopagoPlanificado ya
        // llega resuelto como "lo que paga el paciente en mano" ($4.780) desde el frontend de
        // reserva. Facturacion.importeCopago debe quedar en 4780 e importeCubiertoOs en 20.
        Medico medicoConsulta4800 = Medico.builder().idMedico(1).importeConsulta(new BigDecimal("4800.00")).build();
        Turno turnoConOS = Turno.builder()
                .idTurno(100).medico(medicoConsulta4800).paciente(paciente).estado("ATENDIDO")
                .obraSocialPlanificada("OSDE")
                .importeCopagoPlanificado(new BigDecimal("4780.00"))
                .build();
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turnoConOS);

        assertEquals(0, dto.getImporteTotal().compareTo(new BigDecimal("4800.00")));
        assertEquals(0, dto.getImporteCopago().compareTo(new BigDecimal("4780.00")));
        assertEquals(0, dto.getImporteCubiertoOs().compareTo(new BigDecimal("20.00")));
    }

    @Test
    void testCrearFacturacionAutomatica_SinCopagoPlanificadoAsumeQueSeCobraElTotal() {
        // Fallback de RN-025: turno legacy o reservado sin pasar por el modal (importeCopagoPlanificado
        // null) — antes caía a ZERO (liquidaría $0 al médico), ahora cae al importe de consulta
        // completo, igual que el caso "Particular".
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turno);

        assertEquals(0, dto.getImporteTotal().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, dto.getImporteCopago().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, dto.getImporteCubiertoOs().compareTo(BigDecimal.ZERO));
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
    void testRegistrarCobro_CalculaImporteCubiertoOsComoTotalMenosCopago() {
        // RN-025: "Cubierto OS" ya no se toma del formulario, se calcula server-side.
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(facturacionPendiente()));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder()
                .metodoPago("EFECTIVO")
                .importeTotal(new BigDecimal("10000.00"))
                .importeCopago(new BigDecimal("500.00"))
                .build();

        facturacionService.registrarCobro(1, dto);

        ArgumentCaptor<Cobro> captor = ArgumentCaptor.forClass(Cobro.class);
        verify(cobroRepository, times(1)).save(captor.capture());
        assertEquals(new BigDecimal("9500.00"), captor.getValue().getImporteCubiertoOs());
    }

    @Test
    void testRegistrarCobro_ImporteCopagoNuloAsumeQueSeCobroTodoEnMano() {
        // Casos existentes (efectivo/transferencia) a veces no mandan copago — no debe explotar
        // ni dar un "cubierto OS" negativo: sin dato, se asume que se cobró el total en mano
        // (mismo fallback conservador que montoCobradoEnMano), por lo tanto cubierto OS = 0.
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(facturacionPendiente()));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder()
                .metodoPago("EFECTIVO")
                .importeTotal(new BigDecimal("10000.00"))
                .build();

        facturacionService.registrarCobro(1, dto);

        ArgumentCaptor<Cobro> captor = ArgumentCaptor.forClass(Cobro.class);
        verify(cobroRepository, times(1)).save(captor.capture());
        assertEquals(new BigDecimal("0.00"), captor.getValue().getImporteCubiertoOs());
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

    // ---- Filtro por fecha en el listado (checklist 2026-09-01) ----

    @Test
    void listarFacturaciones_SinFechas_TraeTodasConFindAll() {
        when(facturacionRepository.findAll()).thenReturn(List.of(facturacionPendiente()));

        List<FacturacionResponseDTO> res = facturacionService.listarFacturaciones(null, null);

        assertEquals(1, res.size());
        verify(facturacionRepository).findAll();
        verify(facturacionRepository, never()).findByFechaFacturacionBetween(any(), any());
    }

    @Test
    void listarFacturaciones_SoloDesde_FiltraEseUnicoDia() {
        LocalDate dia = LocalDate.of(2026, 9, 1);
        when(facturacionRepository.findByFechaFacturacionBetween(dia.atStartOfDay(), dia.atTime(LocalTime.MAX)))
                .thenReturn(List.of(facturacionPendiente()));

        List<FacturacionResponseDTO> res = facturacionService.listarFacturaciones(dia, null);

        assertEquals(1, res.size());
        verify(facturacionRepository).findByFechaFacturacionBetween(dia.atStartOfDay(), dia.atTime(LocalTime.MAX));
        verify(facturacionRepository, never()).findAll();
    }

    @Test
    void listarFacturaciones_Rango_UsaLosLimitesInclusive() {
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 7);
        when(facturacionRepository.findByFechaFacturacionBetween(desde.atStartOfDay(), hasta.atTime(LocalTime.MAX)))
                .thenReturn(List.of(facturacionPendiente(), facturacionPendiente()));

        List<FacturacionResponseDTO> res = facturacionService.listarFacturaciones(desde, hasta);

        assertEquals(2, res.size());
        verify(facturacionRepository).findByFechaFacturacionBetween(desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
    }

    // ---- RF-F1: cobro anticipado / factura idempotente / anulación por cancelación ----

    @Test
    void crearFacturacionAutomatica_EsIdempotente_NoDuplicaSiYaExiste() {
        when(facturacionRepository.findByTurnoIdTurno(100)).thenReturn(Optional.of(facturacionPendiente()));

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turno);

        assertEquals(1, dto.getIdFacturacion());
        verify(facturacionRepository, never()).save(any());
    }

    @Test
    void crearFacturacionAutomatica_RefacturaReutilizandoLaFilaSiLaAnteriorEstabaAnulada() {
        // El turno se canceló (factura -> ANULADO) y el cupo se re-reservó para otra atención.
        // Antes: findByTurnoIdTurno devolvía la ANULADA y la nueva atención no podía cobrarse.
        Facturacion anulada = facturacionPendiente();
        anulada.setEstadoPago("ANULADO");
        anulada.setObservaciones("[Anulada automáticamente: el turno se canceló.]");
        when(facturacionRepository.findByTurnoIdTurno(100)).thenReturn(Optional.of(anulada));
        when(facturacionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        FacturacionResponseDTO dto = facturacionService.crearFacturacionAutomatica(turno);

        assertEquals("PENDIENTE", dto.getEstadoPago());
        assertEquals("PENDIENTE", dto.getMetodoPago());
        ArgumentCaptor<Facturacion> captor = ArgumentCaptor.forClass(Facturacion.class);
        verify(facturacionRepository).save(captor.capture());
        // Reutiliza la misma fila (mismo id), no crea una segunda.
        assertEquals(1, captor.getValue().getIdFacturacion());
        assertTrue(captor.getValue().getObservaciones().contains("Refacturada"));
    }

    @Test
    void registrarCobro_RechazaSiLaFacturaYaEstaPagada() {
        Facturacion pagada = facturacionPendiente();
        pagada.setEstadoPago("PAGADO");
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(pagada));

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder()
                .metodoPago("EFECTIVO").importeTotal(new BigDecimal("10000.00")).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> facturacionService.registrarCobro(1, dto));
        assertTrue(ex.getMessage().toLowerCase().contains("cobrada"));
        verify(cobroRepository, never()).save(any());
    }

    @Test
    void registrarCobro_RechazaSiFaltaElImporteTotal() {
        when(facturacionRepository.findById(1)).thenReturn(Optional.of(facturacionPendiente()));

        RegistrarCobroDTO dto = RegistrarCobroDTO.builder().metodoPago("EFECTIVO").build(); // sin importeTotal

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> facturacionService.registrarCobro(1, dto));
        assertTrue(ex.getMessage().toLowerCase().contains("importe"));
        verify(cobroRepository, never()).save(any());
    }

    @Test
    void anularFacturacionDeTurno_MarcaAnuladoYDejaObservacion() {
        Facturacion f = facturacionPendiente();
        when(facturacionRepository.findByTurnoIdTurno(100)).thenReturn(Optional.of(f));
        when(cobroRepository.findByTurnoIdTurno(100)).thenReturn(Optional.empty());

        facturacionService.anularFacturacionDeTurno(100);

        assertEquals("ANULADO", f.getEstadoPago());
        assertNotNull(f.getObservaciones());
        verify(facturacionRepository).save(f);
    }

    @Test
    void anularFacturacionDeTurno_NoHaceNadaSiElTurnoNoTieneFacturacion() {
        when(facturacionRepository.findByTurnoIdTurno(999)).thenReturn(Optional.empty());

        facturacionService.anularFacturacionDeTurno(999);

        verify(facturacionRepository, never()).save(any());
    }
}
