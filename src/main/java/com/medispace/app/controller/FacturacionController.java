package com.medispace.app.controller;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.service.FacturacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facturacion")
@RequiredArgsConstructor
public class FacturacionController {

    private final FacturacionService facturacionService;

    @PostMapping("/{id}/cobro")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<FacturacionResponseDTO> registrarCobro(
            @PathVariable Integer id,
            @RequestBody RegistrarCobroDTO dto) {
        FacturacionResponseDTO response = facturacionService.registrarCobro(id, dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<FacturacionResponseDTO> obtenerFacturacion(@PathVariable Integer id) {
        return ResponseEntity.ok(facturacionService.obtenerFacturacion(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<FacturacionResponseDTO>> listarFacturaciones() {
        return ResponseEntity.ok(facturacionService.listarFacturaciones());
    }
}
