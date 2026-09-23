package com.medispace.app.controller;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.service.FacturacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    // Sección 4.6: validar el ingreso de una transferencia pendiente → la factura pasa a PAGADO.
    @PutMapping("/{id}/confirmar-transferencia")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<FacturacionResponseDTO> confirmarTransferencia(@PathVariable Integer id) {
        return ResponseEntity.ok(facturacionService.confirmarTransferencia(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<FacturacionResponseDTO> obtenerFacturacion(@PathVariable Integer id) {
        return ResponseEntity.ok(facturacionService.obtenerFacturacion(id));
    }

    // Filtro por fecha de facturación: ?desde=YYYY-MM-DD (un día) o ?desde=...&hasta=... (rango).
    // Sin parámetros devuelve todas. El frontend arranca pidiendo el día de hoy.
    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<FacturacionResponseDTO>> listarFacturaciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(facturacionService.listarFacturaciones(desde, hasta));
    }
}
