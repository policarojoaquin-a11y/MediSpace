package com.medispace.app.service;

import com.medispace.app.dto.arrendamiento.ArrendamientoCreateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Medico;
import com.medispace.app.repository.*;
import com.medispace.app.service.impl.ArrendamientoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medispace.app.model.ArrendamientoModulo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ArrendamientoServiceTest {

    @Mock
    private ArrendamientoModuloRepository arrendamientoRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private ConsultorioRepository consultorioRepository;

    @Mock
    private TurnoService turnoService;

    @InjectMocks
    private ArrendamientoServiceImpl arrendamientoService;

    private Medico medico;
    private Consultorio consultorio;

    @BeforeEach
    void setUp() {
        medico = Medico.builder().idMedico(1).nombre("Dr. Lopez").build();
        consultorio = Consultorio.builder().idConsultorio(1).numeroConsultorio("101").build();
    }

    @Test
    void testRN014_MenorA4HorasRechazado() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));

        ArrendamientoCreateDTO dto = ArrendamientoCreateDTO.builder()
                .idMedico(1)
                .idConsultorio(1)
                .fechaInicio(LocalDate.now())
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(11, 30)) // Solo 2.5 horas
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                arrendamientoService.crearArrendamiento(dto));

        assertTrue(ex.getMessage().contains("RN-014"));
        assertTrue(ex.getMessage().contains("mínima"));
    }

    @Test
    void testRN014_Exactamente4HorasPermitido() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));
        when(arrendamientoRepository.countSuperposicionesConsultorio(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(arrendamientoRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(turnoService.generarTurnosParaContrato(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(java.util.List.of());

        ArrendamientoCreateDTO dto = ArrendamientoCreateDTO.builder()
                .idMedico(1)
                .idConsultorio(1)
                .fechaInicio(LocalDate.now())
                .diaSemana("LUNES")
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(12, 0)) // Exactamente 4 horas
                .porcentajeConsultorio(new BigDecimal("30"))
                .porcentajeMedico(new BigDecimal("70"))
                .build();

        assertDoesNotThrow(() -> arrendamientoService.crearArrendamiento(dto));
    }

    @Test
    void testRN013_SuperposicionHorariaRechazada() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));
        when(arrendamientoRepository.countSuperposicionesConsultorio(any(), any(), any(), any(), any(), any())).thenReturn(1L);

        ArrendamientoCreateDTO dto = ArrendamientoCreateDTO.builder()
                .idMedico(1)
                .idConsultorio(1)
                .fechaInicio(LocalDate.now())
                .diaSemana("LUNES")
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(14, 0)) // 6 horas — cumple RN-014
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                arrendamientoService.crearArrendamiento(dto));

        assertTrue(ex.getMessage().contains("RN-013"));
        assertTrue(ex.getMessage().contains("superpone"));
    }

    @Test
    void testListarContratos_SinFiltroDevuelveTodos() {
        ArrendamientoModulo a1 = ArrendamientoModulo.builder().idArrendamiento(1).medico(medico).consultorio(consultorio).estado("ACTIVO").build();
        ArrendamientoModulo a2 = ArrendamientoModulo.builder().idArrendamiento(2).medico(medico).consultorio(consultorio).estado("INACTIVO").build();
        when(arrendamientoRepository.findAll()).thenReturn(List.of(a1, a2));

        var result = arrendamientoService.listarContratos(null);

        assertEquals(2, result.size());
        verify(arrendamientoRepository, times(1)).findAll();
        verify(arrendamientoRepository, never()).findByMedicoIdMedico(any());
    }

    @Test
    void testListarContratos_ConMedicoIdFiltra() {
        ArrendamientoModulo a1 = ArrendamientoModulo.builder().idArrendamiento(1).medico(medico).consultorio(consultorio).estado("ACTIVO").build();
        when(arrendamientoRepository.findByMedicoIdMedico(1)).thenReturn(List.of(a1));

        var result = arrendamientoService.listarContratos(1);

        assertEquals(1, result.size());
        verify(arrendamientoRepository, times(1)).findByMedicoIdMedico(1);
        verify(arrendamientoRepository, never()).findAll();
    }
}
