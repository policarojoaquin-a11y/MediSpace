package com.medispace.app.service.impl;

import com.medispace.app.dto.facturacion.GenerarLiquidacionDTO;
import com.medispace.app.dto.facturacion.LiquidacionResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.LiquidacionMedica;
import com.medispace.app.model.Medico;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.LiquidacionMedicaRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.TurnoRepository;
import com.medispace.app.service.LiquidacionService;
import com.medispace.app.util.SplitFinancieroCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LiquidacionServiceImpl implements LiquidacionService {

    private final LiquidacionMedicaRepository liquidacionMedicaRepository;
    private final FacturacionRepository facturacionRepository;
    private final MedicoRepository medicoRepository;
    private final TurnoRepository turnoRepository;

    @Override
    @Transactional
    public LiquidacionResponseDTO generarLiquidacion(GenerarLiquidacionDTO dto) {
        Medico medico = medicoRepository.findById(dto.getIdMedico())
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));

        LocalDateTime desde = dto.getFechaDesde().atStartOfDay();
        LocalDateTime hasta = dto.getFechaHasta().atTime(LocalTime.MAX);

        // RN-005: No se liquida si hay turnos atendidos / facturas sin cobro registrado (estado PENDIENTE)
        long impagas = facturacionRepository.countByMedicoIdMedicoAndFechaFacturacionBetweenAndEstadoPagoIn(
                medico.getIdMedico(), desde, hasta, List.of("PENDIENTE"));

        if (impagas > 0) {
            throw new BusinessRuleException("RN-005: No se puede liquidar si existen turnos atendidos sin cobro registrado en el período (" + impagas + " factura(s) pendiente(s)).");
        }

        // RN-018: Turnos "Atendido" sin ningún Cobro registrado para este médico en el período
        long atencionesSinCobro = turnoRepository.countAtendidosSinCobro(medico.getIdMedico(), desde, hasta);
        if (atencionesSinCobro > 0) {
            throw new BusinessRuleException("RN-018: Existen " + atencionesSinCobro + " atenciones sin cobro registrado para este médico. Completar antes de liquidar.");
        }

        // Igual criterio que ReporteServiceImpl.recalcularDashboard: las facturas ANULADO (el
        // turno se canceló) o REINTEGRADO conservan su Importe_Total pero no representan plata
        // real facturada — sumarlas infla la liquidación con dinero que nunca se cobró.
        List<Facturacion> facturas = facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(
                medico.getIdMedico(), desde, hasta).stream()
                .filter(f -> !"ANULADO".equalsIgnoreCase(f.getEstadoPago()) && !"REINTEGRADO".equalsIgnoreCase(f.getEstadoPago()))
                .toList();

        if (facturas.isEmpty()) {
            throw new BusinessRuleException("No existen facturas cobradas para liquidar en el período especificado.");
        }

        // RN-024: no se puede liquidar un período que se superponga con una liquidación EMITIDA
        // existente del mismo médico — sin este chequeo, la misma facturación se podía liquidar
        // dos veces (p. ej. una liquidación diaria y otra semanal que la incluye), duplicando lo
        // que se le pagaría al médico.
        List<LiquidacionMedica> superpuestas = liquidacionMedicaRepository.findSuperpuestas(
                medico.getIdMedico(), dto.getFechaDesde(), dto.getFechaHasta());
        if (!superpuestas.isEmpty()) {
            throw new BusinessRuleException(
                    "RN-024: Ya existe una liquidación EMITIDA para este médico que se superpone con el período solicitado (id " +
                            superpuestas.get(0).getIdLiquidacion() + ", " + superpuestas.get(0).getFechaDesde() + " a " + superpuestas.get(0).getFechaHasta() + ").");
        }

        BigDecimal totalFacturado = BigDecimal.ZERO;
        BigDecimal totalMedico = BigDecimal.ZERO;
        BigDecimal totalConsultorio = BigDecimal.ZERO;

        for (Facturacion f : facturas) {
            // RN-025: el split se calcula sobre lo efectivamente cobrado en mano en el
            // consultorio, no sobre el precio de lista — el coseguro que la obra social le paga
            // al médico directo nunca entra a esta caja.
            BigDecimal base = SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago());
            totalFacturado = totalFacturado.add(base);

            // RN-006: split médico/consultorio — cálculo centralizado en SplitFinancieroCalculator
            // para que Facturación, Liquidación y Cierre Diario no puedan volver a divergir.
            SplitFinancieroCalculator.Split split = SplitFinancieroCalculator.calcular(
                    base, f.getPorcentajeMedico(), f.getPorcentajeConsultorio());

            totalMedico = totalMedico.add(split.parteMedico());
            totalConsultorio = totalConsultorio.add(split.parteConsultorio());
        }

        LiquidacionMedica liq = LiquidacionMedica.builder()
                .medico(medico)
                .fechaDesde(dto.getFechaDesde())
                .fechaHasta(dto.getFechaHasta())
                .totalFacturado(totalFacturado)
                .totalConsultorio(totalConsultorio)
                .totalMedico(totalMedico)
                .fechaGeneracion(LocalDateTime.now())
                .estado("EMITIDA")
                .visible(true)
                .build();

        liq = liquidacionMedicaRepository.save(liq);
        return mapToDTO(liq);
    }

    @Override
    @Transactional
    public LiquidacionResponseDTO anularLiquidacion(Integer idLiquidacion, String motivo) {
        LiquidacionMedica liq = liquidacionMedicaRepository.findById(idLiquidacion)
                .orElseThrow(() -> new BusinessRuleException("Liquidación no encontrada."));

        // RN-007: Liquidación "Emitida" es inmutable — no existe edición directa (PUT/PATCH),
        // solo anulación. Esta liquidación ya fue anulada: no permitir anularla de nuevo.
        if ("ANULADA".equals(liq.getEstado())) {
            throw new BusinessRuleException("RN-007: La liquidación ya se encuentra anulada.");
        }
        // constitution.md §4: las liquidaciones emitidas se anulan con motivo obligatorio.
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new BusinessRuleException("El motivo de la anulación es obligatorio.");
        }
        String obsPrevias = liq.getObservaciones() != null ? liq.getObservaciones() : "";
        liq.setObservaciones(obsPrevias + "\n[ANULADA - Motivo: " + motivo.trim() + "]");
        liq.setEstado("ANULADA");
        liq = liquidacionMedicaRepository.save(liq);
        return mapToDTO(liq);
    }

    @Override
    public LiquidacionResponseDTO obtenerLiquidacion(Integer id) {
        LiquidacionMedica liq = liquidacionMedicaRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Liquidación no encontrada."));
        return mapToDTO(liq);
    }

    @Override
    public List<LiquidacionResponseDTO> listarLiquidacionesPorMedico(Integer idMedico) {
        return liquidacionMedicaRepository.findByMedicoIdMedico(idMedico).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<LiquidacionResponseDTO> buscarLiquidaciones(Integer idMedico, LocalDate desde, LocalDate hasta) {
        return liquidacionMedicaRepository.buscar(idMedico, desde, hasta).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private LiquidacionResponseDTO mapToDTO(LiquidacionMedica l) {
        return LiquidacionResponseDTO.builder()
                .idLiquidacion(l.getIdLiquidacion())
                .idMedico(l.getMedico().getIdMedico())
                .nombreMedico(l.getMedico().getNombre() + " " + l.getMedico().getApellido())
                .fechaDesde(l.getFechaDesde())
                .fechaHasta(l.getFechaHasta())
                .totalFacturado(l.getTotalFacturado())
                .totalConsultorio(l.getTotalConsultorio())
                .totalMedico(l.getTotalMedico())
                .fechaGeneracion(l.getFechaGeneracion())
                .estado(l.getEstado())
                .observaciones(l.getObservaciones())
                .build();
    }
}
