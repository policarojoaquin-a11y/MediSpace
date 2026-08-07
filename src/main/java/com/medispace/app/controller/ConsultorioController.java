package com.medispace.app.controller;

import com.medispace.app.dto.ConsultorioCreateDTO;
import com.medispace.app.dto.ConsultorioResponseDTO;
import com.medispace.app.service.ConsultorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultorios")
@RequiredArgsConstructor
public class ConsultorioController {

    private final ConsultorioService consultorioService;

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<ConsultorioResponseDTO>> listarConsultorios() {
        return ResponseEntity.ok(consultorioService.listarConsultorios());
    }

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<ConsultorioResponseDTO> crearConsultorio(@RequestBody ConsultorioCreateDTO dto) {
        ConsultorioResponseDTO response = consultorioService.crearConsultorio(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
