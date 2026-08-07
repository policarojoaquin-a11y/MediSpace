package com.medispace.app.controller;

import com.medispace.app.dto.historiaclinica.*;
import com.medispace.app.model.Usuario;
import com.medispace.app.service.HistoriaClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/historias-clinicas")
@RequiredArgsConstructor
public class HistoriaClinicaController {

    private final HistoriaClinicaService historiaClinicaService;

    @GetMapping("/paciente/{idPaciente}")
    // Sección 4.5: GERENTE "No" tiene acceso a Historias Clínicas — a diferencia del resto de
    // los módulos, acá el rol determina si el endpoint es alcanzable en absoluto, no solo qué
    // subconjunto de datos ve (ADMINISTRATIVO sí entra, pero con contenido clínico enmascarado
    // en mapEvolucionToDTO — ver HistoriaClinicaServiceImpl).
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<HistoriaClinicaResponseDTO> obtenerHistoriaClinica(@PathVariable Integer idPaciente) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userRole = "ADMINISTRATIVO";
        if (auth != null && auth.getPrincipal() instanceof Usuario user) {
            userRole = user.getRol();
        }

        HistoriaClinicaResponseDTO response = historiaClinicaService.obtenerHistoriaClinicaPorPaciente(idPaciente, userRole);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{idHC}/evoluciones")
    @PreAuthorize("hasAnyRole('GERENTE', 'MEDICO')")
    public ResponseEntity<EvolucionResponseDTO> agregarEvolucion(
            @PathVariable Integer idHC,
            @RequestBody EvolucionCreateDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario medicoUser = (Usuario) auth.getPrincipal();

        EvolucionResponseDTO response = historiaClinicaService.agregarEvolucion(idHC, medicoUser.getIdUsuario(), dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/evoluciones/{idEvolucion}")
    @PreAuthorize("hasAnyRole('GERENTE', 'MEDICO')")
    public ResponseEntity<EvolucionResponseDTO> editarEvolucion(
            @PathVariable Integer idEvolucion,
            @RequestBody EvolucionUpdateDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario medicoUser = (Usuario) auth.getPrincipal();

        EvolucionResponseDTO response = historiaClinicaService.editarEvolucion(idEvolucion, medicoUser.getIdUsuario(), dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/evoluciones/{idEvolucion}/anular")
    @PreAuthorize("hasAnyRole('GERENTE', 'MEDICO')")
    public ResponseEntity<EvolucionResponseDTO> anularEvolucion(
            @PathVariable Integer idEvolucion,
            @RequestBody EvolucionAnularDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario user = (Usuario) auth.getPrincipal();

        EvolucionResponseDTO response = historiaClinicaService.anularEvolucion(idEvolucion, user.getIdUsuario(), dto);
        return ResponseEntity.ok(response);
    }
}
