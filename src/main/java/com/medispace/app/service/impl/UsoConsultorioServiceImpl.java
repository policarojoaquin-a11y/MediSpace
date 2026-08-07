package com.medispace.app.service.impl;

import com.medispace.app.dto.arrendamiento.UsoConsultorioDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Medico;
import com.medispace.app.model.UsoConsultorio;
import com.medispace.app.repository.ConsultorioRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.UsoConsultorioRepository;
import com.medispace.app.service.UsoConsultorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsoConsultorioServiceImpl implements UsoConsultorioService {

    private final ConsultorioRepository consultorioRepository;
    private final MedicoRepository medicoRepository;
    private final UsoConsultorioRepository usoConsultorioRepository;

    @Override
    @Transactional
    public UsoConsultorioDTO registrarUso(UsoConsultorioDTO dto) {
        Consultorio consultorio = consultorioRepository.findById(dto.getIdConsultorio())
                .orElseThrow(() -> new BusinessRuleException("Consultorio no encontrado."));
        Medico medico = medicoRepository.findById(dto.getIdMedico())
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));

        UsoConsultorio uso = UsoConsultorio.builder()
                .consultorio(consultorio)
                .medico(medico)
                .fecha(dto.getFecha())
                .horaInicio(dto.getHoraInicio())
                .horaFin(dto.getHoraFin())
                .cantidadPacientes(dto.getCantidadPacientes())
                .facturacionGenerada(dto.getFacturacionGenerada())
                .visible(true)
                .build();

        usoConsultorioRepository.save(uso);
        return dto;
    }
}
