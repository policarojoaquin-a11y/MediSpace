package com.medispace.app.service.impl;

import com.medispace.app.dto.arrendamiento.CierreDiarioRequestDTO;
import com.medispace.app.dto.arrendamiento.CierreDiarioResponseDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.ArrendamientoModulo;
import com.medispace.app.model.CierreDiario;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Medico;
import com.medispace.app.repository.ArrendamientoModuloRepository;
import com.medispace.app.repository.CierreDiarioRepository;
import com.medispace.app.repository.ConsultorioRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.service.CierreDiarioService;
import com.medispace.app.util.DiaSemanaUtil;
import com.medispace.app.util.SplitFinancieroCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CierreDiarioServiceImpl implements CierreDiarioService {

    private final MedicoRepository medicoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final FacturacionRepository facturacionRepository;
    private final CierreDiarioRepository cierreDiarioRepository;
    private final ArrendamientoModuloRepository arrendamientoModuloRepository;

    @Override
    @Transactional
    public CierreDiarioResponseDTO generarCierreDiario(CierreDiarioRequestDTO dto) {
        Medico medico = medicoRepository.findById(dto.getIdMedico())
                .orElseThrow(() -> new BusinessRuleException("Médico no encontrado."));
        Consultorio consultorio = consultorioRepository.findById(dto.getIdConsultorio())
                .orElseThrow(() -> new BusinessRuleException("Consultorio no encontrado."));

        LocalDate fecha = dto.getFecha();
        LocalDateTime desde = fecha.atStartOfDay();
        LocalDateTime hasta = fecha.atTime(23, 59, 59);

        // Sumar facturación del día para este médico en este consultorio. Igual criterio que
        // ReporteServiceImpl.recalcularDashboard: se excluyen ANULADO/REINTEGRADO (conservan su
        // Importe_Total pero no son plata realmente facturada).
        var facturas = facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(
                medico.getIdMedico(), desde, hasta).stream()
                .filter(f -> !"ANULADO".equalsIgnoreCase(f.getEstadoPago()) && !"REINTEGRADO".equalsIgnoreCase(f.getEstadoPago()))
                .toList();

        // RN-025: el cierre de caja refleja lo efectivamente cobrado en mano, no el precio de
        // lista de cada consulta — el coseguro que la obra social le paga al médico directo
        // nunca entra a esta caja.
        BigDecimal totalFacturado = facturas.stream()
                .map(f -> SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // RN-006: split médico/consultorio — mismo cálculo centralizado que usan Facturación y
        // Liquidación (SplitFinancieroCalculator), para que los tres no puedan divergir.
        BigDecimal totalConsultorio = BigDecimal.ZERO;
        BigDecimal totalMedico = BigDecimal.ZERO;
        for (var f : facturas) {
            BigDecimal base = SplitFinancieroCalculator.montoCobradoEnMano(f.getImporteTotal(), f.getImporteCopago());
            SplitFinancieroCalculator.Split split = SplitFinancieroCalculator.calcular(
                    base, f.getPorcentajeMedico(), f.getPorcentajeConsultorio());
            totalMedico = totalMedico.add(split.parteMedico());
            totalConsultorio = totalConsultorio.add(split.parteConsultorio());
        }

        ArrendamientoModulo arrendamiento = resolverContratoVigente(medico.getIdMedico(), consultorio.getIdConsultorio(), fecha);

        CierreDiario cierre = CierreDiario.builder()
                .medico(medico)
                .consultorio(consultorio)
                .arrendamiento(arrendamiento)
                .fecha(fecha)
                .totalFacturadoDia(totalFacturado)
                .importeConsultorio(totalConsultorio)
                .importeMedico(totalMedico)
                .cantidadTurnos(facturas.size())
                .estado("CERRADO")
                .fechaRegistro(LocalDateTime.now())
                .visible(true)
                .build();

        cierre = cierreDiarioRepository.save(cierre);
        return mapCierreToDTO(cierre);
    }

    // Mismo criterio de matching (médico + consultorio + día) que FacturacionServiceImpl, a
    // nivel de día completo en vez de un horario puntual — Cierre Diario no tiene un turno
    // asociado, opera sobre "todo el día" del médico en ese consultorio.
    private ArrendamientoModulo resolverContratoVigente(Integer idMedico, Integer idConsultorio, LocalDate fecha) {
        String diaSemana = DiaSemanaUtil.traducir(fecha.getDayOfWeek());
        List<ArrendamientoModulo> contratos = arrendamientoModuloRepository.findContratoVigentePorFecha(
                idMedico, idConsultorio, diaSemana, fecha);
        return contratos.isEmpty() ? null : contratos.get(0);
    }

    private CierreDiarioResponseDTO mapCierreToDTO(CierreDiario c) {
        return CierreDiarioResponseDTO.builder()
                .idCierre(c.getIdCierre())
                .idMedico(c.getMedico().getIdMedico())
                .nombreMedico(c.getMedico().getNombre() + " " + c.getMedico().getApellido())
                .idConsultorio(c.getConsultorio().getIdConsultorio())
                .numeroConsultorio(c.getConsultorio().getNumeroConsultorio())
                .fecha(c.getFecha())
                .totalFacturadoDia(c.getTotalFacturadoDia())
                .importeConsultorio(c.getImporteConsultorio())
                .importeMedico(c.getImporteMedico())
                .cantidadTurnos(c.getCantidadTurnos())
                .estado(c.getEstado())
                .fechaRegistro(c.getFechaRegistro())
                .build();
    }
}
