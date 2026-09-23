package com.medispace.app.service;

import com.medispace.app.dto.facturacion.GenerarLiquidacionDTO;
import com.medispace.app.dto.facturacion.LiquidacionResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface LiquidacionService {
    LiquidacionResponseDTO generarLiquidacion(GenerarLiquidacionDTO dto);
    LiquidacionResponseDTO anularLiquidacion(Integer idLiquidacion, String motivo);
    LiquidacionResponseDTO obtenerLiquidacion(Integer id);
    List<LiquidacionResponseDTO> listarLiquidacionesPorMedico(Integer idMedico);
    List<LiquidacionResponseDTO> buscarLiquidaciones(Integer idMedico, LocalDate desde, LocalDate hasta);
}
