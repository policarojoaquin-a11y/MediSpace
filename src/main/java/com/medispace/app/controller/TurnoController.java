package com.medispace.app.controller;

import com.medispace.app.dto.turno.*;
import com.medispace.app.security.MedicoAccessGuard;
import com.medispace.app.service.TurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/turnos")
@RequiredArgsConstructor
public class TurnoController {

    private final TurnoService turnoService;
    private final MedicoAccessGuard medicoAccessGuard;

    @PostMapping("/{id}/reservar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<TurnoResponseDTO> reservarTurno(
            @PathVariable Integer id,
            @RequestBody ReservarTurnoDTO dto) {
        TurnoResponseDTO response = turnoService.reservarTurno(id, dto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<TurnoResponseDTO> cambiarEstado(
            @PathVariable Integer id,
            @RequestBody CambiarEstadoTurnoDTO dto,
            Authentication authentication) {
        TurnoResponseDTO turnoActual = turnoService.obtenerTurno(id);
        medicoAccessGuard.verificarAccesoPropio(turnoActual.getIdMedico(), authentication);
        TurnoResponseDTO response = turnoService.cambiarEstadoTurno(id, dto);
        return ResponseEntity.ok(response);
    }

    // Hallazgo 2 (checklist 27/08): un MEDICO solo puede pedir por id un turno propio —
    // mismo guard de ownership que ya aplica PUT /{id}/estado. Sin esto, un médico podía leer
    // datos del turno y del paciente de otro médico.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<TurnoResponseDTO> obtenerTurno(@PathVariable Integer id, Authentication authentication) {
        TurnoResponseDTO turno = turnoService.obtenerTurno(id);
        medicoAccessGuard.verificarAccesoPropio(turno.getIdMedico(), authentication);
        return ResponseEntity.ok(turno);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<List<TurnoResponseDTO>> listarTurnos(
            @RequestParam(required = false) Integer idMedico,
            @RequestParam(required = false) Integer idPaciente,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) List<Integer> idsMedico,
            Authentication authentication) {
        // Si el caller es MEDICO, el idMedico efectivo siempre se resuelve desde el JWT —
        // se ignora cualquier idMedico/idsMedico que venga en la query string.
        Integer idMedicoPropio = medicoAccessGuard.idMedicoPropioSiAplica(authentication);
        if (idMedicoPropio != null) {
            idMedico = idMedicoPropio;
            idsMedico = null;
        }
        return ResponseEntity.ok(turnoService.listarTurnos(idMedico, idPaciente, estado, fecha, idsMedico));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarTurno(@PathVariable Integer id) {
        turnoService.eliminarTurno(id);
        return ResponseEntity.noContent().build();
    }

    // RF-T8: cancelar un día completo (o rango) de la agenda de un médico — ej. vacaciones/licencia.
    // Un MEDICO solo puede cancelar días propios: su idMedico se resuelve desde el JWT y se ignora
    // el que venga en el body (mismo criterio que GET /api/turnos con idMedico).
    @PostMapping("/cancelar-dia")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO', 'MEDICO')")
    public ResponseEntity<CancelacionDiaResponseDTO> cancelarDiaMedico(
            @RequestBody CancelarDiaMedicoDTO dto,
            Authentication authentication) {
        Integer idMedicoPropio = medicoAccessGuard.idMedicoPropioSiAplica(authentication);
        if (idMedicoPropio != null) {
            dto.setIdMedico(idMedicoPropio);
        } else {
            medicoAccessGuard.verificarAccesoPropio(dto.getIdMedico(), authentication);
        }
        return ResponseEntity.ok(turnoService.cancelarDiaMedico(dto));
    }
}
