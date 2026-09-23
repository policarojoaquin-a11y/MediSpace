package com.medispace.app.service;

import com.medispace.app.dto.ObraSocialCreateDTO;
import com.medispace.app.dto.ObraSocialResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.ObraSocial;
import com.medispace.app.repository.ObraSocialRepository;
import com.medispace.app.repository.PacienteRepository;
import com.medispace.app.service.impl.ObraSocialServiceImpl;
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
public class ObraSocialServiceTest {

    @Mock
    private ObraSocialRepository obraSocialRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private ObraSocialServiceImpl obraSocialService;

    @Test
    void testCrearObraSocial_Success() {
        when(obraSocialRepository.existsByNombre("Swiss Medical")).thenReturn(false);
        when(obraSocialRepository.save(any())).thenAnswer(i -> {
            ObraSocial o = i.getArgument(0);
            o.setIdObraSocial(7);
            return o;
        });

        ObraSocialCreateDTO dto = ObraSocialCreateDTO.builder().nombre("Swiss Medical").build();
        ObraSocialResponseDTO response = obraSocialService.crearObraSocial(dto);

        assertNotNull(response);
        assertEquals(7, response.getIdObraSocial());
        assertEquals("Swiss Medical", response.getNombre());
        verify(obraSocialRepository, times(1)).save(any());
    }

    @Test
    void testCrearObraSocial_DuplicadaLanzaBusinessRuleException() {
        when(obraSocialRepository.existsByNombre("OSDE")).thenReturn(true);

        ObraSocialCreateDTO dto = ObraSocialCreateDTO.builder().nombre("OSDE").build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            obraSocialService.crearObraSocial(dto);
        });

        assertTrue(ex.getMessage().toLowerCase().contains("obra social"));
        verify(obraSocialRepository, never()).save(any());
    }

    @Test
    void testCrearObraSocial_NombreVacioLanzaBusinessRuleException() {
        ObraSocialCreateDTO dto = ObraSocialCreateDTO.builder().nombre("  ").build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            obraSocialService.crearObraSocial(dto);
        });

        assertTrue(ex.getMessage().toLowerCase().contains("obligatorio"));
        verify(obraSocialRepository, never()).save(any());
    }

    @Test
    void testEliminarObraSocial_RN016_BloqueaSiHayMedicosActivos() {
        ObraSocial obraSocial = ObraSocial.builder().idObraSocial(6).nombre("OSDE").visible(true).build();
        when(obraSocialRepository.findById(6)).thenReturn(Optional.of(obraSocial));
        when(obraSocialRepository.countMedicosActivosAsociados(6)).thenReturn(1L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            obraSocialService.eliminarObraSocial(6);
        });

        assertTrue(ex.getMessage().contains("RN-016"));
        verify(obraSocialRepository, never()).delete(any());
    }

    @Test
    void testEliminarObraSocial_RN019_BloqueaSiHayPacientesActivos() {
        ObraSocial obraSocial = ObraSocial.builder().idObraSocial(6).nombre("Coperativa obrera").visible(true).build();
        when(obraSocialRepository.findById(6)).thenReturn(Optional.of(obraSocial));
        when(obraSocialRepository.countMedicosActivosAsociados(6)).thenReturn(0L);
        when(pacienteRepository.countByObraSocial_IdObraSocial(6)).thenReturn(1L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            obraSocialService.eliminarObraSocial(6);
        });

        assertTrue(ex.getMessage().contains("RN-019"));
        assertTrue(ex.getMessage().toLowerCase().contains("pacientes"));
        verify(obraSocialRepository, never()).delete(any());
    }

    @Test
    void testEliminarObraSocial_PermiteBajaSinMedicosNiPacientesActivos() {
        ObraSocial obraSocial = ObraSocial.builder().idObraSocial(6).nombre("Sin uso").visible(true).build();
        when(obraSocialRepository.findById(6)).thenReturn(Optional.of(obraSocial));
        when(obraSocialRepository.countMedicosActivosAsociados(6)).thenReturn(0L);
        when(pacienteRepository.countByObraSocial_IdObraSocial(6)).thenReturn(0L);

        obraSocialService.eliminarObraSocial(6);

        verify(obraSocialRepository, times(1)).delete(obraSocial);
    }
}
