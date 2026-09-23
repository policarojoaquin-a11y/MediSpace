package com.medispace.app.controller;

import com.medispace.app.dto.facturacion.AnularLiquidacionDTO;
import com.medispace.app.dto.facturacion.GenerarLiquidacionDTO;
import com.medispace.app.dto.facturacion.LiquidacionResponseDTO;
import com.medispace.app.security.MedicoAccessGuard;
import com.medispace.app.service.LiquidacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/liquidaciones")
@RequiredArgsConstructor
public class LiquidacionController {

    private final LiquidacionService liquidacionService;
    private final MedicoAccessGuard medicoAccessGuard;

    @PostMapping("/generar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<LiquidacionResponseDTO> generarLiquidacion(@RequestBody GenerarLiquidacionDTO dto) {
        LiquidacionResponseDTO response = liquidacionService.generarLiquidacion(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/anular")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<LiquidacionResponseDTO> anularLiquidacion(
            @PathVariable Integer id, @RequestBody AnularLiquidacionDTO dto) {
        LiquidacionResponseDTO response = liquidacionService.anularLiquidacion(id, dto.getMotivo());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/medico/{idMedico}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<LiquidacionResponseDTO>> listarPorMedico(
            @PathVariable Integer idMedico, Authentication authentication) {
        medicoAccessGuard.verificarAccesoPropio(idMedico, authentication);
        return ResponseEntity.ok(liquidacionService.listarLiquidacionesPorMedico(idMedico));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<List<LiquidacionResponseDTO>> listarMisLiquidaciones(Authentication authentication) {
        Integer idMedico = medicoAccessGuard.idMedicoPropioSiAplica(authentication);
        return ResponseEntity.ok(liquidacionService.listarLiquidacionesPorMedico(idMedico));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<LiquidacionResponseDTO>> buscarLiquidaciones(
            @RequestParam(required = false) Integer idMedico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(liquidacionService.buscarLiquidaciones(idMedico, desde, hasta));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<LiquidacionResponseDTO> obtenerLiquidacion(
            @PathVariable Integer id, Authentication authentication) {
        LiquidacionResponseDTO response = liquidacionService.obtenerLiquidacion(id);
        medicoAccessGuard.verificarAccesoPropio(response.getIdMedico(), authentication);
        return ResponseEntity.ok(response);
    }
}
