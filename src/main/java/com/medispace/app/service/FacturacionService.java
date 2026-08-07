package com.medispace.app.service;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.model.Turno;

import java.util.List;

public interface FacturacionService {
    FacturacionResponseDTO crearFacturacionAutomatica(Turno turno);
    FacturacionResponseDTO registrarCobro(Integer idFacturacion, RegistrarCobroDTO dto);
    FacturacionResponseDTO obtenerFacturacion(Integer id);
    List<FacturacionResponseDTO> listarFacturaciones();
}
