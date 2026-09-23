package com.medispace.app.controller;

import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoObraSocialDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.dto.medico.MedicoResponseDTO;
import com.medispace.app.dto.medico.MedicoSelfUpdateDTO;
import com.medispace.app.dto.medico.MedicoUpdateDTO;
import com.medispace.app.security.MedicoAccessGuard;
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
    private final MedicoAccessGuard medicoAccessGuard;

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

    @PutMapping("/me")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<MedicoResponseDTO> actualizarMisDatos(
            Authentication authentication, @RequestBody MedicoSelfUpdateDTO dto) {
        return ResponseEntity.ok(medicoService.actualizarMisDatos(authentication.getName(), dto));
    }

    @GetMapping("/me/prestaciones")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<List<MedicoPrestacionDTO>> obtenerMisPrestaciones(Authentication authentication) {
        MedicoResponseDTO yo = medicoService.obtenerMedicoPorEmail(authentication.getName());
        return ResponseEntity.ok(medicoService.listarPrestacionesDeMedico(yo.getIdMedico()));
    }

    // El propio médico gestiona su cartilla de prestaciones (RF-M4 / relevamiento: "actualizable
    // cada vez que el médico lo desee"). Gerente/Administrativo mantienen los endpoints
    // /{id}/prestaciones para hacerlo en nombre de un médico.
    @PostMapping("/me/prestaciones")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<MedicoPrestacionDTO> agregarMiPrestacion(
            Authentication authentication, @RequestBody MedicoPrestacionDTO dto) {
        return new ResponseEntity<>(
                medicoService.agregarPrestacionPropia(authentication.getName(), dto), HttpStatus.CREATED);
    }

    @PutMapping("/me/prestaciones/{idMedicoPrestacion}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<MedicoPrestacionDTO> actualizarMiPrestacion(
            Authentication authentication,
            @PathVariable Integer idMedicoPrestacion,
            @RequestBody MedicoPrestacionDTO dto) {
        return ResponseEntity.ok(
                medicoService.actualizarPrestacionPropia(authentication.getName(), idMedicoPrestacion, dto));
    }

    @DeleteMapping("/me/prestaciones/{idMedicoPrestacion}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<Void> eliminarMiPrestacion(
            Authentication authentication, @PathVariable Integer idMedicoPrestacion) {
        medicoService.eliminarPrestacionPropia(authentication.getName(), idMedicoPrestacion);
        return ResponseEntity.noContent().build();
    }

    // Hallazgo 1 (checklist 27/08): un MEDICO solo puede pedir sus propios datos por id —
    // GERENTE/ADMINISTRATIVO siguen pudiendo consultar cualquier médico. Sin este guard, un
    // médico podía leer nombre, matrícula, importe y coseguros pactados de otro profesional.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<MedicoResponseDTO> obtenerMedico(@PathVariable Integer id, Authentication authentication) {
        medicoAccessGuard.verificarAccesoPropio(id, authentication);
        MedicoResponseDTO response = medicoService.obtenerMedico(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<MedicoResponseDTO>> listarMedicos(
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos) {
        return ResponseEntity.ok(medicoService.listarMedicos(incluirInactivos));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> eliminarMedico(@PathVariable Integer id) {
        medicoService.eliminarMedico(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<MedicoResponseDTO> reactivarMedico(@PathVariable Integer id) {
        medicoService.reactivarMedico(id);
        return ResponseEntity.ok(medicoService.obtenerMedico(id));
    }

    @GetMapping("/{id}/prestaciones")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<MedicoPrestacionDTO>> listarPrestaciones(@PathVariable Integer id, Authentication authentication) {
        medicoAccessGuard.verificarAccesoPropio(id, authentication);
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

    // ---- Obras Sociales del médico (RF-M3 + coseguro decidido por el médico) ----
    // La relación (qué obras sociales trabaja + el coseguro de cada una) la gestiona tanto
    // Gerente/Administrativo en nombre del médico (endpoints /{id}/obras-sociales) como el
    // propio médico sobre su cartilla (endpoints /me/obras-sociales, más abajo).

    @GetMapping("/{id}/obras-sociales")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<MedicoObraSocialDTO>> listarObrasSociales(@PathVariable Integer id, Authentication authentication) {
        medicoAccessGuard.verificarAccesoPropio(id, authentication);
        return ResponseEntity.ok(medicoService.listarObrasSocialesDeMedico(id));
    }

    @PostMapping("/{id}/obras-sociales")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<MedicoObraSocialDTO> agregarObraSocial(
            @PathVariable Integer id,
            @RequestBody MedicoObraSocialDTO dto) {
        return new ResponseEntity<>(medicoService.agregarObraSocial(id, dto), HttpStatus.CREATED);
    }

    @PutMapping("/obras-sociales/{idMedicoObraSocial}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<MedicoObraSocialDTO> actualizarObraSocial(
            @PathVariable Integer idMedicoObraSocial,
            @RequestBody MedicoObraSocialDTO dto) {
        return ResponseEntity.ok(medicoService.actualizarObraSocial(idMedicoObraSocial, dto));
    }

    @DeleteMapping("/obras-sociales/{idMedicoObraSocial}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarObraSocial(@PathVariable Integer idMedicoObraSocial) {
        medicoService.eliminarObraSocial(idMedicoObraSocial);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/obras-sociales")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<List<MedicoObraSocialDTO>> obtenerMisObrasSociales(Authentication authentication) {
        MedicoResponseDTO yo = medicoService.obtenerMedicoPorEmail(authentication.getName());
        return ResponseEntity.ok(medicoService.listarObrasSocialesDeMedico(yo.getIdMedico()));
    }

    @PostMapping("/me/obras-sociales")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<MedicoObraSocialDTO> agregarMiObraSocial(
            Authentication authentication, @RequestBody MedicoObraSocialDTO dto) {
        return new ResponseEntity<>(
                medicoService.agregarObraSocialPropia(authentication.getName(), dto), HttpStatus.CREATED);
    }

    @PutMapping("/me/obras-sociales/{idMedicoObraSocial}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<MedicoObraSocialDTO> actualizarMiCoseguro(
            Authentication authentication,
            @PathVariable Integer idMedicoObraSocial,
            @RequestBody MedicoObraSocialDTO dto) {
        return ResponseEntity.ok(medicoService.actualizarCoseguroPropio(authentication.getName(), idMedicoObraSocial, dto));
    }

    @DeleteMapping("/me/obras-sociales/{idMedicoObraSocial}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<Void> eliminarMiObraSocial(
            Authentication authentication, @PathVariable Integer idMedicoObraSocial) {
        medicoService.eliminarObraSocialPropia(authentication.getName(), idMedicoObraSocial);
        return ResponseEntity.noContent().build();
    }
}
