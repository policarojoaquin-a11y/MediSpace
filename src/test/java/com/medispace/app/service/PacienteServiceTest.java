package com.medispace.app.service;

import com.medispace.app.dto.paciente.PacienteCreateDTO;
import com.medispace.app.dto.paciente.PacienteResponseDTO;
import com.medispace.app.dto.paciente.PacienteUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Paciente;
import com.medispace.app.repository.HistoriaClinicaRepository;
import com.medispace.app.repository.ObraSocialRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.service.impl.PacienteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ObraSocialRepository obraSocialRepository;

    @Mock
    private HistoriaClinicaRepository historiaClinicaRepository;

    @InjectMocks
    private PacienteServiceImpl pacienteService;

    private Paciente pacienteGuardado;

    @BeforeEach
    void setUp() {
        pacienteGuardado = Paciente.builder()
                .idPaciente(1)
                .nombre("Juan")
                .apellido("Perez")
                .dni("12345678")
                .build();
    }

    @Test
    void testRN009_DniUnicoDebeFallarSiExiste() {
        PacienteCreateDTO dto = PacienteCreateDTO.builder()
                .dni("12345678")
                .build();

        when(pacienteRepository.countByDniIncludingInactive("12345678")).thenReturn(1L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            pacienteService.crearPaciente(dto);
        });

        assertTrue(ex.getMessage().contains("RN-009"));
        assertTrue(ex.getMessage().contains("El DNI ya se encuentra registrado"));
    }

    @Test
    void testRN008_DniInmutableDebeFallarSiSeIntentaCambiar() {
        PacienteUpdateDTO dto = PacienteUpdateDTO.builder()
                .dni("87654321") // DNI diferente al original ("12345678")
                .build();

        when(pacienteRepository.findById(1)).thenReturn(Optional.of(pacienteGuardado));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            pacienteService.actualizarPaciente(1, dto);
        });

        assertTrue(ex.getMessage().contains("RN-008"));
        assertTrue(ex.getMessage().contains("El DNI del paciente es inmutable"));
    }

    @Test
    void testRN008_ActualizacionExitosaSinCambiarDNI() {
        PacienteUpdateDTO dto = PacienteUpdateDTO.builder()
                .dni("12345678") // Mismo DNI
                .nombre("Juan Modificado")
                .build();

        when(pacienteRepository.findById(1)).thenReturn(Optional.of(pacienteGuardado));
        when(pacienteRepository.save(any(Paciente.class))).thenReturn(pacienteGuardado);

        PacienteResponseDTO response = pacienteService.actualizarPaciente(1, dto);

        assertNotNull(response);
        assertEquals("Juan Modificado", pacienteGuardado.getNombre());
        verify(pacienteRepository, times(1)).save(pacienteGuardado);
    }
}
