package com.medispace.app.security;

import com.medispace.app.service.MedicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Resuelve el idMedico "real" del usuario autenticado cuando su rol es MEDICO, replicando
 * el mismo patrón que ya usa MedicoController#obtenerMisDatos (resolver desde el JWT vía
 * MedicoService.obtenerMedicoPorEmail). GERENTE y ADMINISTRATIVO no tienen restricción de
 * pertenencia: para ellos ambos métodos son no-ops (devuelven null / no lanzan).
 */
@Component
@RequiredArgsConstructor
public class MedicoAccessGuard {

    private static final String ROLE_MEDICO = "ROLE_MEDICO";

    private final MedicoService medicoService;

    /**
     * @return el idMedico propio si el usuario autenticado tiene rol MEDICO, o null si es
     * GERENTE/ADMINISTRATIVO (roles sin restricción de "solo lo propio").
     */
    public Integer idMedicoPropioSiAplica(Authentication authentication) {
        boolean esMedico = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_MEDICO.equals(a.getAuthority()));
        if (!esMedico) {
            return null;
        }
        return medicoService.obtenerMedicoPorEmail(authentication.getName()).getIdMedico();
    }

    /**
     * Si el usuario autenticado tiene rol MEDICO, exige que idMedicoObjetivo coincida con el
     * suyo propio (403 si no coincide). GERENTE/ADMINISTRATIVO pueden pedir cualquier idMedico.
     */
    public void verificarAccesoPropio(Integer idMedicoObjetivo, Authentication authentication) {
        Integer idPropio = idMedicoPropioSiAplica(authentication);
        if (idPropio != null && !idPropio.equals(idMedicoObjetivo)) {
            throw new AccessDeniedException("No tenés permisos para acceder a datos de otro médico.");
        }
    }
}
