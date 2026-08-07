package com.medispace.app.service;

import com.medispace.app.dto.EspecialidadCreateDTO;
import com.medispace.app.dto.EspecialidadResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Especialidad;
import com.medispace.app.repository.EspecialidadRepository;
import com.medispace.app.service.impl.EspecialidadServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EspecialidadServiceTest {

    @Mock
    private EspecialidadRepository especialidadRepository;

    @InjectMocks
    private EspecialidadServiceImpl especialidadService;

    @Test
    void testCrearEspecialidad_Success() {
        when(especialidadRepository.existsByNombre("Traumatología")).thenReturn(false);
        when(especialidadRepository.save(any())).thenAnswer(i -> {
            Especialidad e = i.getArgument(0);
            e.setIdEspecialidad(5);
            return e;
        });

        EspecialidadCreateDTO dto = EspecialidadCreateDTO.builder().nombre("Traumatología").build();
        EspecialidadResponseDTO response = especialidadService.crearEspecialidad(dto);

        assertNotNull(response);
        assertEquals(5, response.getIdEspecialidad());
        assertEquals("Traumatología", response.getNombre());
        verify(especialidadRepository, times(1)).save(any());
    }

    @Test
    void testCrearEspecialidad_DuplicadaLanzaBusinessRuleException() {
        when(especialidadRepository.existsByNombre("Cardiología")).thenReturn(true);

        EspecialidadCreateDTO dto = EspecialidadCreateDTO.builder().nombre("Cardiología").build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            especialidadService.crearEspecialidad(dto);
        });

        assertTrue(ex.getMessage().toLowerCase().contains("especialidad"));
        verify(especialidadRepository, never()).save(any());
    }
}
