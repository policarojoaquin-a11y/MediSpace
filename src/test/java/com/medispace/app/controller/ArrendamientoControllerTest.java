package com.medispace.app.controller;

import com.medispace.app.dto.arrendamiento.ArrendamientoResponseDTO;
import com.medispace.app.security.MedicoAccessGuard;
import com.medispace.app.service.ArrendamientoService;
import com.medispace.app.service.CierreDiarioService;
import com.medispace.app.service.UsoConsultorioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArrendamientoControllerTest {

    @Mock
    private ArrendamientoService arrendamientoService;

    @Mock
    private UsoConsultorioService usoConsultorioService;

    @Mock
    private CierreDiarioService cierreDiarioService;

    @Mock
    private MedicoAccessGuard medicoAccessGuard;

    @InjectMocks
    private ArrendamientoController controller;

    private Authentication authMedico() {
        return new UsernamePasswordAuthenticationToken("medico1@medispace.com", null, List.of(new SimpleGrantedAuthority("ROLE_MEDICO")));
    }

    @Test
    void listarPorMedico_MedicoPidiendoIdDeOtroMedicoRecibe403() {
        Authentication auth = authMedico();
        doThrow(new AccessDeniedException("No tenés permisos para acceder a datos de otro médico."))
                .when(medicoAccessGuard).verificarAccesoPropio(2, auth);

        assertThrows(AccessDeniedException.class, () -> controller.listarPorMedico(2, auth));
        verify(arrendamientoService, never()).listarPorMedico(any());
    }

    @Test
    void listarPorMedico_MedicoPidiendoSuPropioIdRecibeSusDatos() {
        Authentication auth = authMedico();
        when(arrendamientoService.listarPorMedico(1))
                .thenReturn(List.of(ArrendamientoResponseDTO.builder().idArrendamiento(1).build()));

        var response = controller.listarPorMedico(1, auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        verify(medicoAccessGuard, times(1)).verificarAccesoPropio(1, auth);
    }
}
