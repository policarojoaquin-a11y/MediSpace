package com.medispace.app.controller;

import com.medispace.app.dto.reporte.DashboardResponseDTO;
import com.medispace.app.dto.reporte.ReporteFacturacionDTO;
import com.medispace.app.dto.reporte.ReporteUsoConsultorioDTO;
import com.medispace.app.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    /** Dashboard con indicadores del día de hoy (precalculado) */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<DashboardResponseDTO> getDashboard() {
        return ResponseEntity.ok(reporteService.getDashboardHoy());
    }

    /** Forzar recálculo del dashboard para una fecha específica */
    @PostMapping("/dashboard/recalcular")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<DashboardResponseDTO> recalcularDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reporteService.recalcularDashboard(fecha));
    }

    /** Reporte de facturación agrupado por médico */
    @GetMapping("/facturacion/medico")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<List<ReporteFacturacionDTO>> reporteFacturacionMedico(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteService.reporteFacturacionPorMedico(desde, hasta));
    }

    /** Reporte de facturación agrupado por obra social */
    @GetMapping("/facturacion/obra-social")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<List<ReporteFacturacionDTO>> reporteFacturacionObraSocial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteService.reporteFacturacionPorObraSocial(desde, hasta));
    }

    /** Reporte de uso de consultorios por período */
    @GetMapping("/consultorios")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<List<ReporteUsoConsultorioDTO>> reporteConsultorios(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(reporteService.reporteUsoConsultorios(desde, hasta));
    }
}
