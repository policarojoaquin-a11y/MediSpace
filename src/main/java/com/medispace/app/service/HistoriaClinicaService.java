package com.medispace.app.service;

import com.medispace.app.dto.historiaclinica.*;
import com.medispace.app.model.Usuario;
import org.springframework.web.multipart.MultipartFile;

public interface HistoriaClinicaService {
    HistoriaClinicaResponseDTO obtenerHistoriaClinicaPorPaciente(Integer idPaciente, Usuario actor);
    EvolucionResponseDTO agregarEvolucion(Integer idHistoriaClinica, Integer idMedicoAuth, EvolucionCreateDTO dto);
    EvolucionResponseDTO editarEvolucion(Integer idEvolucion, Integer idMedicoAuth, EvolucionUpdateDTO dto);
    EvolucionResponseDTO anularEvolucion(Integer idEvolucion, Integer idUsuarioAuth, EvolucionAnularDTO dto);
    AdjuntoDTO agregarAdjunto(Integer idEvolucion, Usuario actor, MultipartFile file);
    AdjuntoArchivoDTO descargarAdjunto(Integer idAdjunto, Usuario actor);
}
