package com.medispace.app.service;

import com.medispace.app.dto.turno.CambiarEstadoTurnoDTO;
import com.medispace.app.dto.turno.ReservarTurnoDTO;
import com.medispace.app.dto.turno.TurnoResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Medico;
import com.medispace.app.model.Paciente;
import com.medispace.app.model.Turno;
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

import java.time.LocalDateTime;
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
}
