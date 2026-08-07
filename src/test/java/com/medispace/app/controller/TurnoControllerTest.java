package com.medispace.app.controller;

import com.medispace.app.dto.turno.CambiarEstadoTurnoDTO;
import com.medispace.app.dto.turno.TurnoResponseDTO;
import com.medispace.app.security.MedicoAccessGuard;
import com.medispace.app.service.TurnoService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoControllerTest {

    @Mock
    private TurnoService turnoService;

    @Mock
    private MedicoAccessGuard medicoAccessGuard;

    @InjectMocks
    private TurnoController controller;

    private Authentication authMedico() {
        return new UsernamePasswordAuthenticationToken("medico1@medispace.com", null, List.of(new SimpleGrantedAuthority("ROLE_MEDICO")));
    }

    private Authentication authGerente() {
        return new UsernamePasswordAuthenticationToken("gerente@medispace.com", null, List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
    }

    @Test
    void listarTurnos_MedicoFuerzaSuPropioIdEIgnoraIdsMedicoDelRequest() {
        Authentication auth = authMedico();
        when(medicoAccessGuard.idMedicoPropioSiAplica(auth)).thenReturn(1);
        when(turnoService.listarTurnos(1, null, null, null, null)).thenReturn(List.of());

        // El caller pide explícitamente idMedico=99 e idsMedico=[5,6] — deben ser ignorados.
        controller.listarTurnos(99, null, null, null, List.of(5, 6), auth);

        verify(turnoService, times(1)).listarTurnos(1, null, null, null, null);
        verify(turnoService, never()).listarTurnos(eq(99), any(), any(), any(), any());
    }

    @Test
    void listarTurnos_GerenteOAdministrativoMantieneLosFiltrosSolicitados() {
        Authentication auth = authGerente();
        when(medicoAccessGuard.idMedicoPropioSiAplica(auth)).thenReturn(null);
        when(turnoService.listarTurnos(3, null, null, null, List.of(5, 6))).thenReturn(List.of());

        controller.listarTurnos(3, null, null, null, List.of(5, 6), auth);

        verify(turnoService, times(1)).listarTurnos(3, null, null, null, List.of(5, 6));
    }

    @Test
    void cambiarEstado_MedicoNoPuedeModificarTurnoDeOtroMedico() {
        Authentication auth = authMedico();
        TurnoResponseDTO turnoDeOtroMedico = TurnoResponseDTO.builder().idTurno(50).idMedico(2).build();
        when(turnoService.obtenerTurno(50)).thenReturn(turnoDeOtroMedico);
        doThrow(new AccessDeniedException("No tenés permisos para acceder a datos de otro médico."))
                .when(medicoAccessGuard).verificarAccesoPropio(2, auth);

        CambiarEstadoTurnoDTO dto = CambiarEstadoTurnoDTO.builder().nuevoEstado("EN_ESPERA").build();

        assertThrows(AccessDeniedException.class, () -> controller.cambiarEstado(50, dto, auth));
        verify(turnoService, never()).cambiarEstadoTurno(anyInt(), any());
    }

    @Test
    void cambiarEstado_MedicoPuedeModificarSuPropioTurno() {
        Authentication auth = authMedico();
        TurnoResponseDTO turnoPropio = TurnoResponseDTO.builder().idTurno(50).idMedico(1).build();
        when(turnoService.obtenerTurno(50)).thenReturn(turnoPropio);
        when(turnoService.cambiarEstadoTurno(eq(50), any())).thenReturn(turnoPropio);

        CambiarEstadoTurnoDTO dto = CambiarEstadoTurnoDTO.builder().nuevoEstado("EN_ESPERA").build();
        var response = controller.cambiarEstado(50, dto, auth);

        assertEquals(200, response.getStatusCode().value());
        verify(medicoAccessGuard, times(1)).verificarAccesoPropio(1, auth);
    }

    @Test
    void cambiarEstado_GerentePuedeModificarTurnoDeCualquierMedico() {
        Authentication auth = authGerente();
        TurnoResponseDTO turnoDeOtroMedico = TurnoResponseDTO.builder().idTurno(50).idMedico(2).build();
        when(turnoService.obtenerTurno(50)).thenReturn(turnoDeOtroMedico);
        when(turnoService.cambiarEstadoTurno(eq(50), any())).thenReturn(turnoDeOtroMedico);

        CambiarEstadoTurnoDTO dto = CambiarEstadoTurnoDTO.builder().nuevoEstado("EN_ESPERA").build();
        var response = controller.cambiarEstado(50, dto, auth);

        assertEquals(200, response.getStatusCode().value());
        verify(medicoAccessGuard, times(1)).verificarAccesoPropio(2, auth);
    }
}
