package com.medispace.app.service.impl;

import com.medispace.app.dto.facturacion.FacturacionResponseDTO;
import com.medispace.app.dto.facturacion.RegistrarCobroDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.ArrendamientoModulo;
import com.medispace.app.model.Cobro;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.Turno;
import com.medispace.app.repository.ArrendamientoModuloRepository;
import com.medispace.app.repository.CobroRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.service.FacturacionService;
import com.medispace.app.util.DiaSemanaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacturacionServiceImpl implements FacturacionService {

    private final FacturacionRepository facturacionRepository;
    private final CobroRepository cobroRepository;
    private final ArrendamientoModuloRepository arrendamientoModuloRepository;

    // RN-006: split de fallback cuando el turno no tiene un contrato de arrendamiento vigente
    // que lo respalde (p. ej. datos de test/seed sin consultorio asociado).
    private static final BigDecimal PORCENTAJE_MEDICO_DEFAULT = new BigDecimal("70.00");
    private static final BigDecimal PORCENTAJE_CONSULTORIO_DEFAULT = new BigDecimal("30.00");

    @Override
    @Transactional
    public FacturacionResponseDTO crearFacturacionAutomatica(Turno turno) {
        if (turno.getPaciente() == null) {
            throw new BusinessRuleException("No se puede facturar un turno sin paciente asignado.");
        }

        BigDecimal importeConsulta = turno.getMedico().getImporteConsulta() != null ?
                turno.getMedico().getImporteConsulta() : BigDecimal.ZERO;

        // RN-006: Split configurado a nivel del contrato de arrendamiento vigente (médico +
        // consultorio + día/horario del turno), no un valor fijo por turno.
        ArrendamientoModulo arrendamiento = resolverContratoVigente(turno);
        BigDecimal porcentajeMedico = arrendamiento != null ? arrendamiento.getPorcentajeMedico() : PORCENTAJE_MEDICO_DEFAULT;
        BigDecimal porcentajeConsultorio = arrendamiento != null ? arrendamiento.getPorcentajeConsultorio() : PORCENTAJE_CONSULTORIO_DEFAULT;

        // Sección 4.4/4.6: preferir lo cargado al reservar el turno (tipo de consulta, obra
        // social, copago) sobre los valores por defecto — antes se perdían silenciosamente
        // porque ReservarTurnoDTO no los declaraba.
        String tipoConsulta = (turno.getTipoConsulta() != null && !turno.getTipoConsulta().isBlank())
                ? turno.getTipoConsulta()
                : (turno.getPrestacion() != null ? turno.getPrestacion().getNombre() : "Consulta Médica");

        String obraSocialNombre = (turno.getObraSocialPlanificada() != null && !turno.getObraSocialPlanificada().isBlank())
                ? turno.getObraSocialPlanificada()
                : (turno.getPaciente().getObraSocial() != null ? turno.getPaciente().getObraSocial().getNombre() : "Particular");

        BigDecimal importeCopago = turno.getImporteCopagoPlanificado() != null
                ? turno.getImporteCopagoPlanificado()
                : BigDecimal.ZERO;

        Facturacion facturacion = Facturacion.builder()
                .turno(turno)
                .paciente(turno.getPaciente())
                .medico(turno.getMedico())
                .idArrendamiento(arrendamiento != null ? arrendamiento.getIdArrendamiento() : null)
                .fechaFacturacion(LocalDateTime.now())
                .tipoConsulta(tipoConsulta)
                .metodoPago("PENDIENTE")
                .obraSocial(obraSocialNombre)
                .importeTotal(importeConsulta)
                .importeCopago(importeCopago)
                .porcentajeMedico(porcentajeMedico)
                .porcentajeConsultorio(porcentajeConsultorio)
                .estadoPago("PENDIENTE")
                .visible(true)
                .build();

        facturacion = facturacionRepository.save(facturacion);
        return mapToDTO(facturacion);
    }

    @Override
    @Transactional
    public FacturacionResponseDTO registrarCobro(Integer idFacturacion, RegistrarCobroDTO dto) {
        Facturacion facturacion = facturacionRepository.findById(idFacturacion)
                .orElseThrow(() -> new BusinessRuleException("Facturación no encontrada."));

        Cobro cobro = Cobro.builder()
                .turno(facturacion.getTurno())
                .metodoPago(dto.getMetodoPago())
                .importeTotal(dto.getImporteTotal())
                .importeCubiertoOs(dto.getImporteCubiertoOs())
                .importeCopago(dto.getImporteCopago())
                .fechaCobro(LocalDateTime.now())
                .visible(true)
                .build();

        cobroRepository.save(cobro);

        facturacion.setMetodoPago(dto.getMetodoPago());
        facturacion.setImporteTotal(dto.getImporteTotal());
        if (dto.getImporteCopago() != null) {
            facturacion.setImporteCopago(dto.getImporteCopago());
        }
        // Sección 4.6: "Transferencias pendientes quedan en estado 'Pendiente' hasta validación."
        // El resto de los métodos (efectivo, tarjeta, obra social) se dan por confirmados al
        // momento del registro del cobro.
        facturacion.setEstadoPago("TRANSFERENCIA".equalsIgnoreCase(dto.getMetodoPago()) ? "PENDIENTE" : "PAGADO");

        facturacion = facturacionRepository.save(facturacion);
        return mapToDTO(facturacion);
    }

    @Override
    public FacturacionResponseDTO obtenerFacturacion(Integer id) {
        Facturacion facturacion = facturacionRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Facturación no encontrada."));
        return mapToDTO(facturacion);
    }

    @Override
    public List<FacturacionResponseDTO> listarFacturaciones() {
        return facturacionRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Mismo criterio de matching (médico + consultorio + día/horario) que usa
    // TurnoServiceImpl.generarTurnosParaContrato() para generar los turnos de un contrato,
    // en sentido inverso: dado un turno ya atendido, encontrar el contrato que lo respalda.
    private ArrendamientoModulo resolverContratoVigente(Turno turno) {
        if (turno.getConsultorio() == null) {
            return null;
        }
        String diaSemana = DiaSemanaUtil.traducir(turno.getFechaHora().getDayOfWeek());
        List<ArrendamientoModulo> contratos = arrendamientoModuloRepository.findContratoVigente(
                turno.getMedico().getIdMedico(),
                turno.getConsultorio().getIdConsultorio(),
                diaSemana,
                turno.getFechaHora().toLocalTime(),
                turno.getFechaHora().toLocalDate());
        return contratos.isEmpty() ? null : contratos.get(0);
    }

    private FacturacionResponseDTO mapToDTO(Facturacion f) {
        return FacturacionResponseDTO.builder()
                .idFacturacion(f.getIdFacturacion())
                .idTurno(f.getTurno().getIdTurno())
                .idPaciente(f.getPaciente().getIdPaciente())
                .nombrePaciente(f.getPaciente().getNombre() + " " + f.getPaciente().getApellido())
                .idMedico(f.getMedico().getIdMedico())
                .nombreMedico(f.getMedico().getNombre() + " " + f.getMedico().getApellido())
                .fechaFacturacion(f.getFechaFacturacion())
                .tipoConsulta(f.getTipoConsulta())
                .metodoPago(f.getMetodoPago())
                .obraSocial(f.getObraSocial())
                .importeTotal(f.getImporteTotal())
                .importeCopago(f.getImporteCopago())
                .porcentajeConsultorio(f.getPorcentajeConsultorio())
                .porcentajeMedico(f.getPorcentajeMedico())
                .estadoPago(f.getEstadoPago())
                .observaciones(f.getObservaciones())
                .metodoPagoPlanificado(f.getTurno() != null ? f.getTurno().getMetodoPagoPlanificado() : null)
                .build();
    }
}
