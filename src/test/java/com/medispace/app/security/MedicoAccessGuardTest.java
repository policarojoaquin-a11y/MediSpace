package com.medispace.app.security;

import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.service.MedicoService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicoAccessGuardTest {

    @Mock
    private MedicoService medicoService;

    @InjectMocks
    private MedicoAccessGuard medicoAccessGuard;

    private Authentication authMedico(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_MEDICO")));
    }

    private Authentication authGerente() {
        return new UsernamePasswordAuthenticationToken("gerente@medispace.com", null, List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
    }

    private Authentication authAdministrativo() {
        return new UsernamePasswordAuthenticationToken("admin@medispace.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVO")));
    }

    @Test
    void idMedicoPropioSiAplica_ResuelveDesdeJwtCuandoEsMedico() {
        when(medicoService.obtenerMedicoPorEmail("medico1@medispace.com"))
                .thenReturn(MedicoResponseDTO.builder().idMedico(1).build());

        Integer id = medicoAccessGuard.idMedicoPropioSiAplica(authMedico("medico1@medispace.com"));

        assertEquals(1, id);
    }

    @Test
    void idMedicoPropioSiAplica_DevuelveNullParaGerenteSinConsultarMedicoService() {
        Integer id = medicoAccessGuard.idMedicoPropioSiAplica(authGerente());

        assertNull(id);
        verify(medicoService, never()).obtenerMedicoPorEmail(any());
    }

    @Test
    void idMedicoPropioSiAplica_DevuelveNullParaAdministrativoSinConsultarMedicoService() {
        Integer id = medicoAccessGuard.idMedicoPropioSiAplica(authAdministrativo());

        assertNull(id);
        verify(medicoService, never()).obtenerMedicoPorEmail(any());
    }

    @Test
    void verificarAccesoPropio_MedicoPidiendoSuPropioIdNoLanza() {
        when(medicoService.obtenerMedicoPorEmail("medico1@medispace.com"))
                .thenReturn(MedicoResponseDTO.builder().idMedico(1).build());

        assertDoesNotThrow(() ->
                medicoAccessGuard.verificarAccesoPropio(1, authMedico("medico1@medispace.com")));
    }

    @Test
    void verificarAccesoPropio_MedicoPidiendoIdDeOtroMedicoLanza403() {
        when(medicoService.obtenerMedicoPorEmail("medico1@medispace.com"))
                .thenReturn(MedicoResponseDTO.builder().idMedico(1).build());

        assertThrows(AccessDeniedException.class, () ->
                medicoAccessGuard.verificarAccesoPropio(2, authMedico("medico1@medispace.com")));
    }

    @Test
    void verificarAccesoPropio_GerentePuedePedirCualquierIdMedico() {
        assertDoesNotThrow(() -> medicoAccessGuard.verificarAccesoPropio(2, authGerente()));
        verify(medicoService, never()).obtenerMedicoPorEmail(any());
    }

    @Test
    void verificarAccesoPropio_AdministrativoPuedePedirCualquierIdMedico() {
        assertDoesNotThrow(() -> medicoAccessGuard.verificarAccesoPropio(2, authAdministrativo()));
        verify(medicoService, never()).obtenerMedicoPorEmail(any());
    }
}
