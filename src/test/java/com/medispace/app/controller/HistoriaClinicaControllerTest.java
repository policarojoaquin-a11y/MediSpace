package com.medispace.app.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class HistoriaClinicaControllerTest {

    // Sección 4.5: GERENTE no debe poder consultar Historias Clínicas en absoluto ("No" en la
    // tabla de permisos). Este test es una salvaguarda declarativa: si alguien vuelve a agregar
    // GERENTE al @PreAuthorize de este endpoint, el test falla sin necesidad de un contexto
    // completo de Spring Security.
    @Test
    void obtenerHistoriaClinica_PreAuthorizeExcluyeAGerente() throws NoSuchMethodException {
        Method metodo = HistoriaClinicaController.class.getMethod("obtenerHistoriaClinica", Integer.class);
        PreAuthorize preAuthorize = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize, "El endpoint de consulta de Historia Clínica debe estar protegido con @PreAuthorize.");
        assertFalse(preAuthorize.value().contains("GERENTE"),
                "GERENTE no debe poder acceder a Historias Clínicas (sección 4.5: 'Ver historias clínicas: No').");
        assertTrue(preAuthorize.value().contains("ADMINISTRATIVO"));
        assertTrue(preAuthorize.value().contains("MEDICO"));
    }
}
