package com.medispace.app.dto.historiaclinica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvolucionAnularDTO {
    private String motivoAnulacion;
}
