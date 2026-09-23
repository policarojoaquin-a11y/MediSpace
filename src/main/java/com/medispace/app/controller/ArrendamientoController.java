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

    // Hallazgo 3 (checklist 27/08): un MEDICO necesita ver la disponibilidad de todos los
    // consultorios (día/horario ocupado), pero NO los porcentajes pactados en los contratos
    // ajenos. Para el caller MEDICO se devuelven todos los contratos con los porcentajes y las
    // observaciones enmascarados en las filas que no son propias.
    @GetMapping("/contratos")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<ArrendamientoResponseDTO>> listarContratos(
            @RequestParam(required = false) Integer medicoId, Authentication authentication) {
        Integer idMedicoPropio = medicoAccessGuard.idMedicoPropioSiAplica(authentication);
        List<ArrendamientoResponseDTO> contratos = arrendamientoService.listarContratos(medicoId);
        if (idMedicoPropio != null) {
            contratos.forEach(c -> {
                if (!idMedicoPropio.equals(c.getIdMedico())) {
                    c.setPorcentajeConsultorio(null);
                    c.setPorcentajeMedico(null);
                    c.setObservaciones(null);
                }
            });
        }
        return ResponseEntity.ok(contratos);
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
