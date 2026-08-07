package com.medispace.app.service;

import com.medispace.app.dto.arrendamiento.UsoConsultorioDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Medico;
import com.medispace.app.model.UsoConsultorio;
import com.medispace.app.repository.ConsultorioRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.UsoConsultorioRepository;
import com.medispace.app.service.impl.UsoConsultorioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UsoConsultorioServiceTest {

    @Mock
    private ConsultorioRepository consultorioRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private UsoConsultorioRepository usoConsultorioRepository;

    @InjectMocks
    private UsoConsultorioServiceImpl usoConsultorioService;

    private Medico medico;
    private Consultorio consultorio;

    @BeforeEach
    void setUp() {
        medico = Medico.builder().idMedico(1).nombre("Dr. Lopez").build();
        consultorio = Consultorio.builder().idConsultorio(1).numeroConsultorio("101").build();
    }

    @Test
    void testRegistrarUso_PersisteConMedicoYConsultorioResueltos() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));

        UsoConsultorioDTO dto = UsoConsultorioDTO.builder()
                .idMedico(1)
                .idConsultorio(1)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(12, 0))
                .cantidadPacientes(5)
                .facturacionGenerada(new BigDecimal("1000.00"))
                .build();

        UsoConsultorioDTO response = usoConsultorioService.registrarUso(dto);

        assertNotNull(response);
        ArgumentCaptor<UsoConsultorio> captor = ArgumentCaptor.forClass(UsoConsultorio.class);
        verify(usoConsultorioRepository, times(1)).save(captor.capture());
        assertEquals(consultorio, captor.getValue().getConsultorio());
        assertEquals(medico, captor.getValue().getMedico());
        assertEquals(5, captor.getValue().getCantidadPacientes());
    }

    @Test
    void testRegistrarUso_ConsultorioInexistenteLanzaExcepcion() {
        when(consultorioRepository.findById(99)).thenReturn(Optional.empty());

        UsoConsultorioDTO dto = UsoConsultorioDTO.builder().idMedico(1).idConsultorio(99).build();

        assertThrows(BusinessRuleException.class, () -> usoConsultorioService.registrarUso(dto));
        verify(usoConsultorioRepository, never()).save(any());
    }
}
