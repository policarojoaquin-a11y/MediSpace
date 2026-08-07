package com.medispace.app.service;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.medico.MedicoCreateDTO;
import com.medispace.app.dto.medico.MedicoPrestacionDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Medico;
import com.medispace.app.model.MedicoPrestacion;
import com.medispace.app.model.PrestacionMedica;
import com.medispace.app.model.Usuario;
import com.medispace.app.model.Especialidad;
import com.medispace.app.repository.*;
import com.medispace.app.service.impl.MedicoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MedicoServiceTest {

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private PrestacionMedicaRepository prestacionMedicaRepository;

    @Mock
    private ObraSocialRepository obraSocialRepository;

    @Mock
    private MedicoPrestacionRepository medicoPrestacionRepository;

    @InjectMocks
    private MedicoServiceImpl medicoService;

    private Medico medicoGuardado;

    @BeforeEach
    void setUp() {
        Usuario usuario = Usuario.builder()
                .idUsuario(1)
                .email("medico@test.com")
                .rol("MEDICO")
                .build();

        Especialidad especialidad = Especialidad.builder()
                .idEspecialidad(1)
                .nombre("Cardiología")
                .build();

        medicoGuardado = Medico.builder()
                .idMedico(1)
                .usuario(usuario)
                .nombre("Dr. Juan")
                .apellido("Pérez")
                .matricula("MAT001")
                .especialidad(especialidad)
                .estado("ACTIVO")
                .obrasSociales(new HashSet<>())
                .build();
    }

    @Test
    void testRN012_BajaBloqueadaSiHayTurnosFuturos() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.countTurnosFuturosActivos(1)).thenReturn(3L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            medicoService.eliminarMedico(1);
        });

        assertTrue(ex.getMessage().contains("RN-012"));
        assertTrue(ex.getMessage().contains("turno(s) futuro(s)"));
        verify(medicoRepository, never()).delete(any());
    }

    @Test
    void testRN012_BajaExitosaSinTurnosFuturos() {
        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(medicoRepository.countTurnosFuturosActivos(1)).thenReturn(0L);

        assertDoesNotThrow(() -> medicoService.eliminarMedico(1));

        verify(medicoRepository, times(1)).delete(medicoGuardado);
    }

    private MedicoCreateDTO.MedicoCreateDTOBuilder medicoCreateDTOBase() {
        return MedicoCreateDTO.builder()
                .nombre("Juan")
                .apellido("Pérez")
                .matricula("MAT002")
                .idEspecialidad(1)
                .fechaInicioActividad(LocalDate.now())
                .email("nuevo.medico@test.com");
    }

    @Test
    void testCrearMedico_CreaUsuarioConRolMedicoYPasswordMatricula() {
        MedicoCreateDTO dto = medicoCreateDTOBase().build();

        when(medicoRepository.findByMatricula(dto.getMatricula())).thenReturn(Optional.empty());
        when(especialidadRepository.findById(1)).thenReturn(Optional.of(
                Especialidad.builder().idEspecialidad(1).nombre("Cardiología").build()));
        when(usuarioService.crearUsuario(any())).thenReturn(medicoGuardado.getUsuario());
        when(medicoRepository.save(any())).thenReturn(medicoGuardado);

        medicoService.crearMedico(dto);

        ArgumentCaptor<UsuarioCreateDTO> captor = ArgumentCaptor.forClass(UsuarioCreateDTO.class);
        verify(usuarioService, times(1)).crearUsuario(captor.capture());

        UsuarioCreateDTO usuarioCreado = captor.getValue();
        assertEquals("MEDICO", usuarioCreado.getRol());
        assertEquals(dto.getEmail(), usuarioCreado.getEmail());
        assertEquals(dto.getMatricula(), usuarioCreado.getPassword());
    }

    @Test
    void testCrearMedico_EmailDuplicadoPropagaBusinessRuleException() {
        MedicoCreateDTO dto = medicoCreateDTOBase().build();

        when(medicoRepository.findByMatricula(dto.getMatricula())).thenReturn(Optional.empty());
        when(usuarioService.crearUsuario(any()))
                .thenThrow(new BusinessRuleException("El email ya se encuentra registrado."));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            medicoService.crearMedico(dto);
        });

        assertEquals("El email ya se encuentra registrado.", ex.getMessage());
        verify(medicoRepository, never()).save(any());
    }

    @Test
    void testAgregarPrestacion_Success() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(prestacionMedicaRepository.findById(1)).thenReturn(Optional.of(prestacion));
        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 1)).thenReturn(Optional.empty());
        when(medicoPrestacionRepository.save(any())).thenAnswer(i -> {
            MedicoPrestacion mp = i.getArgument(0);
            mp.setIdMedicoPrestacion(50);
            return mp;
        });

        MedicoPrestacionDTO dto = MedicoPrestacionDTO.builder()
                .idPrestacion(1)
                .duracionEstimadaMin(30)
                .importeParticular(new java.math.BigDecimal("5000.00"))
                .tipo("Presencial")
                .build();

        MedicoPrestacionDTO response = medicoService.agregarPrestacion(1, dto);

        assertNotNull(response);
        assertEquals(50, response.getIdMedicoPrestacion());
        assertEquals("Consulta general", response.getNombrePrestacion());
        verify(medicoPrestacionRepository, times(1)).save(any());
    }

    @Test
    void testAgregarPrestacion_DuplicadaLanzaBusinessRuleException() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();
        MedicoPrestacion existente = MedicoPrestacion.builder().idMedicoPrestacion(50).medico(medicoGuardado).prestacion(prestacion).build();

        when(medicoRepository.findById(1)).thenReturn(Optional.of(medicoGuardado));
        when(prestacionMedicaRepository.findById(1)).thenReturn(Optional.of(prestacion));
        when(medicoPrestacionRepository.findByMedicoIdMedicoAndPrestacionIdPrestacion(1, 1)).thenReturn(Optional.of(existente));

        MedicoPrestacionDTO dto = MedicoPrestacionDTO.builder().idPrestacion(1).build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            medicoService.agregarPrestacion(1, dto);
        });

        assertTrue(ex.getMessage().toLowerCase().contains("ya está asociada"));
        verify(medicoPrestacionRepository, never()).save(any());
    }

    @Test
    void testEliminarPrestacion_BajaLogica() {
        PrestacionMedica prestacion = PrestacionMedica.builder().idPrestacion(1).nombre("Consulta general").build();
        MedicoPrestacion existente = MedicoPrestacion.builder().idMedicoPrestacion(50).medico(medicoGuardado).prestacion(prestacion).build();

        when(medicoPrestacionRepository.findById(50)).thenReturn(Optional.of(existente));

        medicoService.eliminarPrestacion(50);

        verify(medicoPrestacionRepository, times(1)).delete(existente);
    }
}
