package com.medispace.app.controller;

import com.medispace.app.dto.historiaclinica.EvolucionAnularDTO;
import com.medispace.app.dto.historiaclinica.EvolucionUpdateDTO;
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

    // RN-010 / tabla 1.7 de la propuesta original: "Solo el médico que realizó la atención puede
    // registrar o modificar la evolución clínica correspondiente. Ningún otro rol puede editarla."
    // GERENTE estaba habilitado por error — corregido 2026-09-23. Salvaguarda declarativa igual
    // que el test de arriba.
    @Test
    void editarEvolucion_PreAuthorizeExcluyeAGerente() throws NoSuchMethodException {
        Method metodo = HistoriaClinicaController.class.getMethod("editarEvolucion", Integer.class, EvolucionUpdateDTO.class);
        PreAuthorize preAuthorize = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertFalse(preAuthorize.value().contains("GERENTE"),
                "RN-010: ningún otro rol además del médico tratante puede editar una evolución clínica.");
        assertTrue(preAuthorize.value().contains("MEDICO"));
    }

    @Test
    void anularEvolucion_PreAuthorizeExcluyeAGerente() throws NoSuchMethodException {
        Method metodo = HistoriaClinicaController.class.getMethod("anularEvolucion", Integer.class, EvolucionAnularDTO.class);
        PreAuthorize preAuthorize = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertFalse(preAuthorize.value().contains("GERENTE"),
                "RN-010/RN-011: ningún otro rol además del médico tratante puede anular una evolución clínica.");
        assertTrue(preAuthorize.value().contains("MEDICO"));
    }
}
