package com.medispace.app.service.impl;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Usuario;
import com.medispace.app.model.enums.RolEnum;
import com.medispace.app.repository.UsuarioRepository;
import com.medispace.app.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Usuario crearUsuario(UsuarioCreateDTO dto) {
        // RN-015: Un usuario tiene un único rol activo simultáneo.
        // Validamos que el rol provisto sea exactamente uno de los valores permitidos del Enum,
        // garantizando que no pueda asignarse más de un rol.
        validarUnicoRol(dto.getRol());

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new BusinessRuleException("El email ya se encuentra registrado.");
        }

        Usuario usuario = Usuario.builder()
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .rol(dto.getRol())
                .visible(true)
                .fechaCreacion(LocalDate.now())
                .build();

        return usuarioRepository.save(usuario);
    }

    private void validarUnicoRol(String rolStr) {
        if (rolStr == null || rolStr.trim().isEmpty()) {
            throw new BusinessRuleException("RN-015: El usuario debe tener asignado un rol.");
        }
        
        // Verificamos si contiene comas o espacios, indicando intento de múltiples roles
        if (rolStr.contains(",") || rolStr.contains(" ")) {
            throw new BusinessRuleException("RN-015: Un usuario tiene un único rol activo simultáneo.");
        }

        try {
            RolEnum.valueOf(rolStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("RN-015: El rol asignado no es válido. Roles permitidos: GERENTE, ADMINISTRATIVO, MEDICO.");
        }
    }
}
