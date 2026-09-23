package com.medispace.app.service;

import com.medispace.app.dto.turno.CambiarEstadoTurnoDTO;
import com.medispace.app.dto.turno.CancelacionDiaResponseDTO;
import com.medispace.app.dto.turno.CancelarDiaMedicoDTO;
import com.medispace.app.dto.turno.ReservarTurnoDTO;
import com.medispace.app.dto.turno.TurnoResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Medico;
import com.medispace.app.model.Paciente;
import com.medispace.app.model.Turno;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.repository.PrestacionMedicaRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.service.impl.TurnoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TurnoServiceTest {

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private PrestacionMedicaRepository prestacionMedicaRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private FacturacionRepository facturacionRepository;

    @Mock
    private FacturacionService facturacionService;

    @InjectMocks
    private TurnoServiceImpl turnoService;

    private Medico medico;
    private Paciente paciente;
    private Turno turnoDisponible;
    private Turno turnoAtendido;

    @BeforeEach
    void setUp() {
        medico = Medico.builder().idMedico(1).nombre("Dr. Carlos").apellido("Gomez").build();
        paciente = Paciente.builder().idPaciente(10).nombre("Ana").apellido("Lopez").build();

        turnoDisponible = Turno.builder()
                .idTurno(100)
                .medico(medico)
                .fechaHora(LocalDateTime.now().plusDays(1))
                .estado("DISPONIBLE")
                .build();

        turnoAtendido = Turno.builder()
                .idTurno(200)
                .medico(medico)
                .paciente(paciente)
                .fechaHora(LocalDateTime.now().minusHours(2))
                .estado("ATENDIDO")
                .build();
    }

    @Test
    void testRN001_SoloReservarDisponibles() {
        Turno turnoReservado = Turno.builder()
                .idTurno(101)
                .medico(medico)
                .estado("RESERVADO")
                .build();

        when(turnoRepository.findByIdForUpdate(101)).thenReturn(Optional.of(turnoReservado));

        ReservarTurnoDTO dto = ReservarTurnoDTO.builder().idPaciente(10).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            turnoService.reservarTurno(101, dto);
        });

        assertTrue(ex.getMessage().contains("RN-001"));
    }

    @Test
    void testRN002_PacienteNoDuplicaHorario() {
        when(turnoRepository.findByIdForUpdate(100)).thenReturn(Optional.of(turnoDisponible));
        when(pacienteRepository.findById(10)).thenReturn(Optional.of(paciente));
        when(turnoRepository.existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(eq(10), any(), any()))
                .thenReturn(true);

        ReservarTurnoDTO dto = ReservarTurnoDTO.builder().idPaciente(10).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            turnoService.reservarTurno(100, dto);
        });

        assertTrue(ex.getMessage().contains("RN-002"));
    }

    @Test
    void testRN003_MedicoNoDuplicaHorario() {
        when(turnoRepository.findByIdForUpdate(100)).thenReturn(Optional.of(turnoDisponible));
        when(pacienteRepository.findById(10)).thenReturn(Optional.of(paciente));
        when(turnoRepository.existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(eq(10), any(), any()))
                .thenReturn(false);
        when(turnoRepository.existsByMedicoIdMedicoAndFechaHoraAndEstadoIn(eq(1), any(), any()))
                .thenReturn(true);

        ReservarTurnoDTO dto = ReservarTurnoDTO.builder().idPaciente(10).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            turnoService.reservarTurno(100, dto);
        });

        assertTrue(ex.getMessage().contains("RN-003"));
    }

    @Test
    void testRN004_TurnoAtendidoEsInmutable() {
        when(turnoRepository.findById(200)).thenReturn(Optional.of(turnoAtendido));

        CambiarEstadoTurnoDTO dto = CambiarEstadoTurnoDTO.builder().nuevoEstado("CANCELADO").build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            turnoService.cambiarEstadoTurno(200, dto);
        });

        assertTrue(ex.getMessage().contains("RN-004"));
    }

    @Test
    void testReservaExitosa() {
        when(turnoRepository.findByIdForUpdate(100)).thenReturn(Optional.of(turnoDisponible));
        when(pacienteRepository.findById(10)).thenReturn(Optional.of(paciente));
        when(turnoRepository.existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(eq(10), any(), any()))
                .thenReturn(false);
        when(turnoRepository.existsByMedicoIdMedicoAndFechaHoraAndEstadoIn(eq(1), any(), any()))
                .thenReturn(false);
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        ReservarTurnoDTO dto = ReservarTurnoDTO.builder().idPaciente(10).build();

        TurnoResponseDTO res = turnoService.reservarTurno(100, dto);

        assertNotNull(res);
        assertEquals("RESERVADO", res.getEstado());
        assertEquals(10, res.getIdPaciente());
    }

    @Test
    void testReservarTurno_UsaLockPesimistaNoFindByIdSimple() {
        // Confirma que reservarTurno lee el turno vía el método con lock pesimista
        // (findByIdForUpdate) y no vía el findById simple sin lock — es lo que evita la
        // condición de carrera bajo reservas concurrentes.
        when(turnoRepository.findByIdForUpdate(100)).thenReturn(Optional.of(turnoDisponible));
        when(pacienteRepository.findById(10)).thenReturn(Optional.of(paciente));
        when(turnoRepository.existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(eq(10), any(), any()))
                .thenReturn(false);
        when(turnoRepository.existsByMedicoIdMedicoAndFechaHoraAndEstadoIn(eq(1), any(), any()))
                .thenReturn(false);
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        turnoService.reservarTurno(100, ReservarTurnoDTO.builder().idPaciente(10).build());

        verify(turnoRepository, times(1)).findByIdForUpdate(100);
        verify(turnoRepository, never()).findById(any());
    }

    @Test
    void testReservarTurno_PersisteDatosDeLaReservaEnVezDePerderlos() {
        when(turnoRepository.findByIdForUpdate(100)).thenReturn(Optional.of(turnoDisponible));
        when(pacienteRepository.findById(10)).thenReturn(Optional.of(paciente));
        when(turnoRepository.existsByPacienteIdPacienteAndFechaHoraAndEstadoIn(eq(10), any(), any()))
                .thenReturn(false);
        when(turnoRepository.existsByMedicoIdMedicoAndFechaHoraAndEstadoIn(eq(1), any(), any()))
                .thenReturn(false);
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        ReservarTurnoDTO dto = ReservarTurnoDTO.builder()
                .idPaciente(10)
                .tipoConsulta("ESTUDIO")
                .metodoPago("TRANSFERENCIA")
                .obraSocial("OSDE")
                .copago(new java.math.BigDecimal("1500.00"))
                .build();

        TurnoResponseDTO res = turnoService.reservarTurno(100, dto);

        assertEquals("ESTUDIO", res.getTipoConsulta());
        assertEquals("TRANSFERENCIA", res.getMetodoPagoPlanificado());
        assertEquals("OSDE", res.getObraSocialPlanificada());
        assertEquals(new java.math.BigDecimal("1500.00"), res.getImporteCopagoPlanificado());
        assertNotNull(turnoDisponible.getTipoConsulta());
    }

    @Test
    void testListarTurnos_FiltraPorMultiplesMedicos() {
        when(turnoRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Turno>>any()))
                .thenReturn(java.util.List.of(turnoDisponible));

        var result = turnoService.listarTurnos(null, null, null, null, java.util.List.of(1, 2, 3));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testListarTurnos_MantieneCompatibilidadConIdMedicoUnico() {
        when(turnoRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Turno>>any()))
                .thenReturn(java.util.List.of(turnoDisponible));

        var result = turnoService.listarTurnos(1, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ---- RF-T8 / RN-021: cancelar un día del médico ----

    @Test
    void cancelarDiaMedico_DisponibleYReservadoPasanACanceladoAtendidoIntacto() {
        Paciente conTelefono = Paciente.builder().idPaciente(10).nombre("Ana").apellido("Lopez").telefono("11-5555").build();
        LocalDate dia = LocalDate.now().plusDays(3);
        Turno disp = Turno.builder().idTurno(1).medico(medico).estado("DISPONIBLE").fechaHora(dia.atTime(9, 0)).build();
        Turno resv = Turno.builder().idTurno(2).medico(medico).paciente(conTelefono).estado("RESERVADO").fechaHora(dia.atTime(10, 0)).build();
        Turno atendido = Turno.builder().idTurno(3).medico(medico).paciente(conTelefono).estado("ATENDIDO").fechaHora(dia.atTime(11, 0)).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(turnoRepository.findByMedicoIdMedicoAndFechaHoraBetween(eq(1), any(), any()))
                .thenReturn(List.of(disp, resv, atendido));

        CancelarDiaMedicoDTO dto = CancelarDiaMedicoDTO.builder().idMedico(1).fecha(dia).motivo("Vacaciones").build();
        CancelacionDiaResponseDTO res = turnoService.cancelarDiaMedico(dto);

        assertEquals(2, res.getTurnosCancelados());
        assertEquals(1, res.getDisponiblesCancelados());
        assertEquals(1, res.getReservadosCancelados());
        assertEquals("CANCELADO", disp.getEstado());
        assertEquals("CANCELADO", resv.getEstado());
        assertEquals("ATENDIDO", atendido.getEstado());
        assertEquals(1, res.getPacientesAContactar().size());
        assertEquals("11-5555", res.getPacientesAContactar().get(0).getTelefonoPaciente());
        assertEquals("Vacaciones", res.getMotivo());
        verify(turnoRepository, times(1)).saveAll(any());
    }

    @Test
    void cancelarDiaMedico_IgnoraTurnosYaTranscurridos() {
        Turno pasado = Turno.builder().idTurno(1).medico(medico).estado("DISPONIBLE").fechaHora(LocalDateTime.now().minusHours(1)).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(turnoRepository.findByMedicoIdMedicoAndFechaHoraBetween(eq(1), any(), any()))
                .thenReturn(List.of(pasado));

        CancelacionDiaResponseDTO res = turnoService.cancelarDiaMedico(
                CancelarDiaMedicoDTO.builder().idMedico(1).fecha(LocalDate.now()).build());

        assertEquals(0, res.getTurnosCancelados());
        assertEquals("DISPONIBLE", pasado.getEstado());
    }

    @Test
    void cancelarDiaMedico_RangoDeFechasCubreVariosDias() {
        LocalDate desde = LocalDate.now().plusDays(2);
        LocalDate hasta = LocalDate.now().plusDays(6);
        Turno d1 = Turno.builder().idTurno(1).medico(medico).estado("DISPONIBLE").fechaHora(desde.atTime(9, 0)).build();
        Turno d2 = Turno.builder().idTurno(2).medico(medico).estado("DISPONIBLE").fechaHora(hasta.atTime(9, 0)).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(turnoRepository.findByMedicoIdMedicoAndFechaHoraBetween(eq(1), any(), any()))
                .thenReturn(List.of(d1, d2));

        CancelacionDiaResponseDTO res = turnoService.cancelarDiaMedico(
                CancelarDiaMedicoDTO.builder().idMedico(1).fecha(desde).fechaHasta(hasta).build());

        assertEquals(2, res.getTurnosCancelados());
        assertEquals(hasta, res.getFechaHasta());
    }

    @Test
    void cancelarDiaMedico_RangoInvalidoLanzaExcepcion() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));

        CancelarDiaMedicoDTO dto = CancelarDiaMedicoDTO.builder()
                .idMedico(1).fecha(LocalDate.now().plusDays(5)).fechaHasta(LocalDate.now().plusDays(2)).build();

        assertThrows(BusinessRuleException.class, () -> turnoService.cancelarDiaMedico(dto));
    }

    // ---- RF-F1: facturación se genera al pasar a EN_ESPERA (no solo a ATENDIDO) ----

    @Test
    void cambiarEstado_EnEspera_GeneraFacturacionPendiente() {
        Turno reservado = Turno.builder().idTurno(101).medico(medico).paciente(paciente)
                .fechaHora(LocalDateTime.now().plusDays(1)).estado("RESERVADO").build();
        when(turnoRepository.findById(101)).thenReturn(Optional.of(reservado));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        turnoService.cambiarEstadoTurno(101, CambiarEstadoTurnoDTO.builder().nuevoEstado("EN_ESPERA").build());

        assertEquals("EN_ESPERA", reservado.getEstado());
        verify(facturacionService, times(1)).crearFacturacionAutomatica(reservado);
    }

    @Test
    void cambiarEstado_Atendido_TambienGeneraFacturacion() {
        Turno enEspera = Turno.builder().idTurno(102).medico(medico).paciente(paciente)
                .fechaHora(LocalDateTime.now().minusMinutes(5)).estado("EN_ESPERA").build();
        when(turnoRepository.findById(102)).thenReturn(Optional.of(enEspera));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        turnoService.cambiarEstadoTurno(102, CambiarEstadoTurnoDTO.builder().nuevoEstado("ATENDIDO").build());

        verify(facturacionService, times(1)).crearFacturacionAutomatica(enEspera);
    }

    @Test
    void cambiarEstado_CanceladoAnulaLaFacturacionDelTurno() {
        Turno enEspera = Turno.builder().idTurno(103).medico(medico).paciente(paciente)
                .fechaHora(LocalDateTime.now().minusMinutes(5)).estado("EN_ESPERA").build();
        when(turnoRepository.findById(103)).thenReturn(Optional.of(enEspera));
        when(turnoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        turnoService.cambiarEstadoTurno(103, CambiarEstadoTurnoDTO.builder().nuevoEstado("CANCELADO").build());

        verify(facturacionService, times(1)).anularFacturacionDeTurno(103);
        verify(facturacionService, never()).crearFacturacionAutomatica(any());
    }
}
