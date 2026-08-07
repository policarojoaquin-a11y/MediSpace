package com.medispace.app.controller;

import com.medispace.app.dto.ObraSocialResponseDTO;
import com.medispace.app.service.ObraSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/obras-sociales")
@RequiredArgsConstructor
public class ObraSocialController {

    private final ObraSocialService obraSocialService;

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<ObraSocialResponseDTO>> listarObrasSociales() {
        return ResponseEntity.ok(obraSocialService.listarObrasSociales());
    }
}
