package com.medispace.app.service;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Usuario;
import com.medispace.app.repository.UsuarioRepository;
import com.medispace.app.service.impl.UsuarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testRN015_RolInvalidoDebeFallar() {
        UsuarioCreateDTO dto = UsuarioCreateDTO.builder()
                .email("test@medispace.com")
                .password("123456")
                .rol("INVALID_ROLE")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            usuarioService.crearUsuario(dto);
        });

        assertTrue(ex.getMessage().contains("RN-015"));
        assertTrue(ex.getMessage().contains("El rol asignado no es válido"));
    }

    @Test
    void testRN015_MultiplesRolesDebenFallar() {
        UsuarioCreateDTO dto = UsuarioCreateDTO.builder()
                .email("test@medispace.com")
                .password("123456")
                .rol("MEDICO,ADMINISTRATIVO")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            usuarioService.crearUsuario(dto);
        });

        assertTrue(ex.getMessage().contains("RN-015"));
        assertTrue(ex.getMessage().contains("Un usuario tiene un único rol activo simultáneo"));
    }

    @Test
    void testRN015_RolUnicoValidoDebePasar() {
        UsuarioCreateDTO dto = UsuarioCreateDTO.builder()
                .email("test@medispace.com")
                .password("123456")
                .rol("MEDICO")
                .build();

        when(usuarioRepository.findByEmail(dto.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        
        Usuario mockSaved = Usuario.builder().email(dto.getEmail()).rol(dto.getRol()).build();
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(mockSaved);

        Usuario resultado = usuarioService.crearUsuario(dto);

        assertNotNull(resultado);
        assertEquals("MEDICO", resultado.getRol());
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }
}
