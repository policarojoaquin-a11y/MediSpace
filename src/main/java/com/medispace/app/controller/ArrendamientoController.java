package com.medispace.app.controller;

import com.medispace.app.dto.arrendamiento.*;
import com.medispace.app.security.MedicoAccessGuard;
import com.medispace.app.service.ArrendamientoService;
import com.medispace.app.service.CierreDiarioService;
import com.medispace.app.service.UsoConsultorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/arrendamientos")
@RequiredArgsConstructor
public class ArrendamientoController {

    private final ArrendamientoService arrendamientoService;
    private final UsoConsultorioService usoConsultorioService;
    private final CierreDiarioService cierreDiarioService;
    private final MedicoAccessGuard medicoAccessGuard;

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<ArrendamientoResponseDTO> crearArrendamiento(@RequestBody ArrendamientoCreateDTO dto) {
        ArrendamientoResponseDTO response = arrendamientoService.crearArrendamiento(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<ArrendamientoResponseDTO> obtenerArrendamiento(@PathVariable Integer id) {
        return ResponseEntity.ok(arrendamientoService.obtenerArrendamiento(id));
    }

    @GetMapping("/medico/{idMedico}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<ArrendamientoResponseDTO>> listarPorMedico(
            @PathVariable Integer idMedico, Authentication authentication) {
        medicoAccessGuard.verificarAccesoPropio(idMedico, authentication);
        return ResponseEntity.ok(arrendamientoService.listarPorMedico(idMedico));
    }

    @GetMapping("/contratos")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<ArrendamientoResponseDTO>> listarContratos(
            @RequestParam(required = false) Integer medicoId) {
        return ResponseEntity.ok(arrendamientoService.listarContratos(medicoId));
    }

    @PutMapping("/{id}/baja")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> darDeBaja(@PathVariable Integer id) {
        arrendamientoService.darDeBajaArrendamiento(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uso")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<UsoConsultorioDTO> registrarUso(@RequestBody UsoConsultorioDTO dto) {
        UsoConsultorioDTO response = usoConsultorioService.registrarUso(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/cierre-diario")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<CierreDiarioResponseDTO> generarCierreDiario(@RequestBody CierreDiarioRequestDTO dto) {
        CierreDiarioResponseDTO response = cierreDiarioService.generarCierreDiario(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
