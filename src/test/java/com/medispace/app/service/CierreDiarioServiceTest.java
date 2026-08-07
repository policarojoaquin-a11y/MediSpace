package com.medispace.app.service;

import com.medispace.app.dto.arrendamiento.CierreDiarioRequestDTO;
import com.medispace.app.dto.arrendamiento.CierreDiarioResponseDTO;
import com.medispace.app.model.ArrendamientoModulo;
import com.medispace.app.model.CierreDiario;
import com.medispace.app.model.Consultorio;
import com.medispace.app.model.Facturacion;
import com.medispace.app.model.Medico;
import com.medispace.app.repository.ArrendamientoModuloRepository;
import com.medispace.app.repository.CierreDiarioRepository;
import com.medispace.app.repository.ConsultorioRepository;
import com.medispace.app.repository.FacturacionRepository;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.service.impl.CierreDiarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CierreDiarioServiceTest {

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private ConsultorioRepository consultorioRepository;

    @Mock
    private FacturacionRepository facturacionRepository;

    @Mock
    private CierreDiarioRepository cierreDiarioRepository;

    @Mock
    private ArrendamientoModuloRepository arrendamientoModuloRepository;

    @InjectMocks
    private CierreDiarioServiceImpl cierreDiarioService;

    private Medico medico;
    private Consultorio consultorio;

    @BeforeEach
    void setUp() {
        medico = Medico.builder().idMedico(1).nombre("Dr. Lopez").apellido("Perez").build();
        consultorio = Consultorio.builder().idConsultorio(1).numeroConsultorio("101").build();
    }

    @Test
    void testGenerarCierreDiario_CalculaSplitConSplitFinancieroCalculator() {
        Facturacion f1 = Facturacion.builder()
                .importeTotal(new BigDecimal("1000.00"))
                .porcentajeMedico(new BigDecimal("70.00"))
                .porcentajeConsultorio(new BigDecimal("30.00"))
                .build();
        Facturacion f2 = Facturacion.builder()
                .importeTotal(new BigDecimal("500.00"))
                .porcentajeMedico(new BigDecimal("70.00"))
                .porcentajeConsultorio(new BigDecimal("30.00"))
                .build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(eq(1), any(), any()))
                .thenReturn(List.of(f1, f2));
        when(arrendamientoModuloRepository.findContratoVigentePorFecha(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(cierreDiarioRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        CierreDiarioRequestDTO dto = CierreDiarioRequestDTO.builder()
                .idMedico(1).idConsultorio(1).fecha(LocalDate.now()).build();

        CierreDiarioResponseDTO response = cierreDiarioService.generarCierreDiario(dto);

        assertEquals(new BigDecimal("1500.00"), response.getTotalFacturadoDia());
        assertEquals(new BigDecimal("1050.00"), response.getImporteMedico());
        assertEquals(new BigDecimal("450.00"), response.getImporteConsultorio());
        assertEquals(2, response.getCantidadTurnos());
    }

    @Test
    void testGenerarCierreDiario_PueblaIdArrendamientoCuandoHayContratoVigente() {
        ArrendamientoModulo contrato = ArrendamientoModulo.builder().idArrendamiento(77).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(eq(1), any(), any()))
                .thenReturn(List.of());
        when(arrendamientoModuloRepository.findContratoVigentePorFecha(any(), any(), any(), any()))
                .thenReturn(List.of(contrato));
        when(cierreDiarioRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        CierreDiarioRequestDTO dto = CierreDiarioRequestDTO.builder()
                .idMedico(1).idConsultorio(1).fecha(LocalDate.now()).build();

        cierreDiarioService.generarCierreDiario(dto);

        ArgumentCaptor<CierreDiario> captor = ArgumentCaptor.forClass(CierreDiario.class);
        verify(cierreDiarioRepository).save(captor.capture());
        assertEquals(77, captor.getValue().getArrendamiento().getIdArrendamiento());
    }

    @Test
    void testGenerarCierreDiario_SinContratoVigenteArrendamientoQuedaNull() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medico));
        when(consultorioRepository.findById(1)).thenReturn(Optional.of(consultorio));
        when(facturacionRepository.findByMedicoIdMedicoAndFechaFacturacionBetween(eq(1), any(), any()))
                .thenReturn(List.of());
        when(arrendamientoModuloRepository.findContratoVigentePorFecha(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(cierreDiarioRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        CierreDiarioRequestDTO dto = CierreDiarioRequestDTO.builder()
                .idMedico(1).idConsultorio(1).fecha(LocalDate.now()).build();

        cierreDiarioService.generarCierreDiario(dto);

        ArgumentCaptor<CierreDiario> captor = ArgumentCaptor.forClass(CierreDiario.class);
        verify(cierreDiarioRepository).save(captor.capture());
        assertNull(captor.getValue().getArrendamiento());
    }
}
