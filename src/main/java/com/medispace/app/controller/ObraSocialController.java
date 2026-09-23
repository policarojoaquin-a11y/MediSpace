package com.medispace.app.controller;

import com.medispace.app.dto.ObraSocialCreateDTO;
import com.medispace.app.dto.ObraSocialResponseDTO;
import com.medispace.app.dto.ObraSocialUpdateDTO;
import com.medispace.app.service.ObraSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/obras-sociales")
@RequiredArgsConstructor
public class ObraSocialController {

    private final ObraSocialService obraSocialService;

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<ObraSocialResponseDTO>> buscarObrasSociales(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) Boolean requiereBono,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivas) {
        return ResponseEntity.ok(obraSocialService.buscarObrasSociales(nombre, requiereBono, incluirInactivas));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<ObraSocialResponseDTO> crearObraSocial(@RequestBody ObraSocialCreateDTO dto) {
        return new ResponseEntity<>(obraSocialService.crearObraSocial(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<ObraSocialResponseDTO> actualizarObraSocial(
            @PathVariable Integer id,
            @RequestBody ObraSocialUpdateDTO dto) {
        return ResponseEntity.ok(obraSocialService.actualizarObraSocial(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarObraSocial(@PathVariable Integer id) {
        obraSocialService.eliminarObraSocial(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<Void> reactivarObraSocial(@PathVariable Integer id) {
        obraSocialService.reactivarObraSocial(id);
        return ResponseEntity.noContent().build();
    }
}
