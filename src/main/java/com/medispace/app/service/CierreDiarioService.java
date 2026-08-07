package com.medispace.app.service;

import com.medispace.app.dto.arrendamiento.CierreDiarioRequestDTO;
import com.medispace.app.dto.arrendamiento.CierreDiarioResponseDTO;

public interface CierreDiarioService {
    CierreDiarioResponseDTO generarCierreDiario(CierreDiarioRequestDTO dto);
}
