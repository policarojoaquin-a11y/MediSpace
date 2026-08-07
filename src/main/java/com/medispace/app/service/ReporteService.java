package com.medispace.app.service;

import com.medispace.app.dto.reporte.DashboardResponseDTO;
import com.medispace.app.dto.reporte.ReporteFacturacionDTO;
import com.medispace.app.dto.reporte.ReporteUsoConsultorioDTO;

import java.time.LocalDate;
import java.util.List;

public interface ReporteService {
    DashboardResponseDTO getDashboardHoy();
    DashboardResponseDTO recalcularDashboard(LocalDate fecha);
    List<ReporteFacturacionDTO> reporteFacturacionPorMedico(LocalDate desde, LocalDate hasta);
    List<ReporteFacturacionDTO> reporteFacturacionPorObraSocial(LocalDate desde, LocalDate hasta);
    List<ReporteUsoConsultorioDTO> reporteUsoConsultorios(LocalDate desde, LocalDate hasta);
}
