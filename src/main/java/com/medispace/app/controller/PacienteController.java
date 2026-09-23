package com.medispace.app.controller;

import com.medispace.app.dto.paciente.PacienteCreateDTO;
import com.medispace.app.dto.paciente.PacienteResponseDTO;
import com.medispace.app.dto.paciente.PacienteUpdateDTO;
import com.medispace.app.security.MedicoAccessGuard;
import com.medispace.app.service.PacienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;
    private final MedicoAccessGuard medicoAccessGuard;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<PacienteResponseDTO> crearPaciente(@RequestBody PacienteCreateDTO dto) {
        PacienteResponseDTO response = pacienteService.crearPaciente(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<PacienteResponseDTO> actualizarPaciente(
            @PathVariable Integer id,
            @RequestBody PacienteUpdateDTO dto) {
        PacienteResponseDTO response = pacienteService.actualizarPaciente(id, dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<PacienteResponseDTO> obtenerPaciente(@PathVariable Integer id, Authentication authentication) {
        Integer idMedicoFiltro = medicoAccessGuard.idMedicoPropioSiAplica(authentication);
        PacienteResponseDTO response = pacienteService.obtenerPaciente(id, idMedicoFiltro);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<PacienteResponseDTO>> listarPacientes(Authentication authentication) {
        Integer idMedicoFiltro = medicoAccessGuard.idMedicoPropioSiAplica(authentication);
        return ResponseEntity.ok(pacienteService.listarPacientes(idMedicoFiltro));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarPaciente(@PathVariable Integer id) {
        pacienteService.eliminarPaciente(id);
        return ResponseEntity.noContent().build();
    }
}
