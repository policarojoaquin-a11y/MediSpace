package com.medispace.app.controller;

import com.medispace.app.dto.EspecialidadCreateDTO;
import com.medispace.app.dto.EspecialidadResponseDTO;
import com.medispace.app.service.EspecialidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/especialidades")
@RequiredArgsConstructor
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<EspecialidadResponseDTO>> listarEspecialidades() {
        return ResponseEntity.ok(especialidadService.listarEspecialidades());
    }

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<EspecialidadResponseDTO> crearEspecialidad(@RequestBody EspecialidadCreateDTO dto) {
        return new ResponseEntity<>(especialidadService.crearEspecialidad(dto), HttpStatus.CREATED);
    }
}
