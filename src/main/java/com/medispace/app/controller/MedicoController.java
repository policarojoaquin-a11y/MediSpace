package com.medispace.app.controller;

import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.dto.medico.MedicoUpdateDTO;
import com.medispace.app.service.MedicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicos")
@RequiredArgsConstructor
public class MedicoController {

    private final MedicoService medicoService;

    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<MedicoResponseDTO> crearMedico(@RequestBody MedicoCreateDTO dto) {
        MedicoResponseDTO response = medicoService.crearMedico(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<MedicoResponseDTO> actualizarMedico(
            @PathVariable Integer id,
            @RequestBody MedicoUpdateDTO dto) {
        MedicoResponseDTO response = medicoService.actualizarMedico(id, dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<MedicoResponseDTO> obtenerMisDatos(Authentication authentication) {
        return ResponseEntity.ok(medicoService.obtenerMedicoPorEmail(authentication.getName()));
    }

    @GetMapping("/me/prestaciones")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<List<MedicoPrestacionDTO>> obtenerMisPrestaciones(Authentication authentication) {
        MedicoResponseDTO yo = medicoService.obtenerMedicoPorEmail(authentication.getName());
        return ResponseEntity.ok(medicoService.listarPrestacionesDeMedico(yo.getIdMedico()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<MedicoResponseDTO> obtenerMedico(@PathVariable Integer id) {
        MedicoResponseDTO response = medicoService.obtenerMedico(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<MedicoResponseDTO>> listarMedicos() {
        return ResponseEntity.ok(medicoService.listarMedicos());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> eliminarMedico(@PathVariable Integer id) {
        medicoService.eliminarMedico(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/prestaciones")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<MedicoPrestacionDTO>> listarPrestaciones(@PathVariable Integer id) {
        return ResponseEntity.ok(medicoService.listarPrestacionesDeMedico(id));
    }

    @PostMapping("/{id}/prestaciones")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<MedicoPrestacionDTO> agregarPrestacion(
            @PathVariable Integer id,
            @RequestBody MedicoPrestacionDTO dto) {
        return new ResponseEntity<>(medicoService.agregarPrestacion(id, dto), HttpStatus.CREATED);
    }

    @PutMapping("/prestaciones/{idMedicoPrestacion}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<MedicoPrestacionDTO> actualizarPrestacion(
            @PathVariable Integer idMedicoPrestacion,
            @RequestBody MedicoPrestacionDTO dto) {
        return ResponseEntity.ok(medicoService.actualizarPrestacion(idMedicoPrestacion, dto));
    }

    @DeleteMapping("/prestaciones/{idMedicoPrestacion}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> eliminarPrestacion(@PathVariable Integer idMedicoPrestacion) {
        medicoService.eliminarPrestacion(idMedicoPrestacion);
        return ResponseEntity.noContent().build();
    }
}
