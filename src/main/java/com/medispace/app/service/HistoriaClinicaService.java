package com.medispace.app.service;

import com.medispace.app.dto.historiaclinica.*;

public interface HistoriaClinicaService {
    HistoriaClinicaResponseDTO obtenerHistoriaClinicaPorPaciente(Integer idPaciente, String userRole);
    EvolucionResponseDTO agregarEvolucion(Integer idHistoriaClinica, Integer idMedicoAuth, EvolucionCreateDTO dto);
    EvolucionResponseDTO editarEvolucion(Integer idEvolucion, Integer idMedicoAuth, EvolucionUpdateDTO dto);
    EvolucionResponseDTO anularEvolucion(Integer idEvolucion, Integer idUsuarioAuth, EvolucionAnularDTO dto);
}
