package com.medispace.app.service;

import com.medispace.app.dto.facturacion.GenerarLiquidacionDTO;
import com.medispace.app.dto.facturacion.LiquidacionResponseDTO;

import java.util.List;

public interface LiquidacionService {
    LiquidacionResponseDTO generarLiquidacion(GenerarLiquidacionDTO dto);
    LiquidacionResponseDTO anularLiquidacion(Integer idLiquidacion);
    LiquidacionResponseDTO obtenerLiquidacion(Integer id);
    List<LiquidacionResponseDTO> listarLiquidacionesPorMedico(Integer idMedico);
}
