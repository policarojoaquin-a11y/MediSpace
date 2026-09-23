package com.medispace.app.controller;

import com.medispace.app.dto.historiaclinica.*;
import com.medispace.app.model.Usuario;
import com.medispace.app.service.HistoriaClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/historias-clinicas")
@RequiredArgsConstructor
public class HistoriaClinicaController {

    private final HistoriaClinicaService historiaClinicaService;

    @GetMapping("/paciente/{idPaciente}")
    // Sección 4.5: GERENTE "No" tiene acceso a Historias Clínicas — a diferencia del resto de
    // los módulos, acá el rol determina si el endpoint es alcanzable en absoluto. ADMINISTRATIVO
    // ve el contenido clínico completo (alineado con el documento original, "Ver historias
    // clínicas: Sí"), igual que MEDICO.
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<HistoriaClinicaResponseDTO> obtenerHistoriaClinica(@PathVariable Integer idPaciente) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario actor = (Usuario) auth.getPrincipal();
        HistoriaClinicaResponseDTO response = historiaClinicaService.obtenerHistoriaClinicaPorPaciente(idPaciente, actor);
        return ResponseEntity.ok(response);
    }

    // RF-H2 / Hallazgo 4 (checklist 27/08): la evolución la crea únicamente el médico
    // responsable de la atención. Se sacó GERENTE (no es médico — el service ya fallaba al
    // resolverlo, pero el rol no debía estar habilitado) y el service valida además que el
    // médico autenticado tenga a ese paciente vinculado por un turno propio (RN-010).
    @PostMapping("/{idHC}/evoluciones")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<EvolucionResponseDTO> agregarEvolucion(
            @PathVariable Integer idHC,
            @RequestBody EvolucionCreateDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario medicoUser = (Usuario) auth.getPrincipal();

        EvolucionResponseDTO response = historiaClinicaService.agregarEvolucion(idHC, medicoUser.getIdUsuario(), dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // RN-010 (tabla 1.7 / propuesta original): solo el médico que hizo la atención puede
    // registrar o modificar su evolución — "ningún otro rol puede editarla". GERENTE estaba
    // habilitado acá por error: el service (validarAutoriaMedico) igual lo hubiera rechazado
    // siempre (un Gerente no tiene fila en Medicos), pero con un 400 "Médico no encontrado"
    // confuso en vez de un 403 claro — corregido a MEDICO únicamente.
    @PutMapping("/evoluciones/{idEvolucion}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<EvolucionResponseDTO> editarEvolucion(
            @PathVariable Integer idEvolucion,
            @RequestBody EvolucionUpdateDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario medicoUser = (Usuario) auth.getPrincipal();

        EvolucionResponseDTO response = historiaClinicaService.editarEvolucion(idEvolucion, medicoUser.getIdUsuario(), dto);
        return ResponseEntity.ok(response);
    }

    // RN-010/RN-011: misma corrección que editarEvolucion — solo el médico tratante anula su
    // propia evolución (con motivo, RN-011). GERENTE no debe poder anular evoluciones ajenas.
    @DeleteMapping("/evoluciones/{idEvolucion}/anular")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<EvolucionResponseDTO> anularEvolucion(
            @PathVariable Integer idEvolucion,
            @RequestBody EvolucionAnularDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario user = (Usuario) auth.getPrincipal();

        EvolucionResponseDTO response = historiaClinicaService.anularEvolucion(idEvolucion, user.getIdUsuario(), dto);
        return ResponseEntity.ok(response);
    }

    // RF-H3: adjuntos por evolución. Mismos roles que el acceso principal de lectura de HC
    // (línea 22) — Gerente no tiene acceso a Historias Clínicas en absoluto (sección 4.5).
    @PostMapping(value = "/evoluciones/{idEvolucion}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<AdjuntoDTO> agregarAdjunto(
            @PathVariable Integer idEvolucion,
            @RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario actor = (Usuario) auth.getPrincipal();

        AdjuntoDTO response = historiaClinicaService.agregarAdjunto(idEvolucion, actor, file);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/adjuntos/{idAdjunto}/descargar")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<Resource> descargarAdjunto(@PathVariable Integer idAdjunto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario actor = (Usuario) auth.getPrincipal();
        AdjuntoArchivoDTO archivo = historiaClinicaService.descargarAdjunto(idAdjunto, actor);
        String nombreSeguro = archivo.getNombreArchivo().replace("\"", "'");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreSeguro + "\"")
                .body(archivo.getRecurso());
    }
}
