package com.medispace.app.service.impl;

import com.medispace.app.dto.ObraSocialResponseDTO;
import com.medispace.app.repository.ObraSocialRepository;
import com.medispace.app.service.ObraSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObraSocialServiceImpl implements ObraSocialService {

    private final ObraSocialRepository obraSocialRepository;

    @Override
    public List<ObraSocialResponseDTO> listarObrasSociales() {
        return obraSocialRepository.findAll().stream()
                .map(o -> ObraSocialResponseDTO.builder()
                        .idObraSocial(o.getIdObraSocial())
                        .nombre(o.getNombre())
                        .build())
                .collect(Collectors.toList());
    }
}
