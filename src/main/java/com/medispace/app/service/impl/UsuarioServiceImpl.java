package com.medispace.app.service.impl;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.UsuarioResponseDTO;
import com.medispace.app.dto.UsuarioUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Usuario;
import com.medispace.app.model.enums.RolEnum;
import com.medispace.app.repository.MedicoRepository;
import com.medispace.app.repository.UsuarioRepository;
import com.medispace.app.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final MedicoRepository medicoRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
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

    @Override
    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Integer id, UsuarioUpdateDTO dto, String rolActor) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado."));

        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(usuario.getEmail())) {
            if (usuarioRepository.countByEmailIncludingInactive(dto.getEmail()) > 0) {
                throw new BusinessRuleException("El email ya se encuentra registrado.");
            }
            usuario.setEmail(dto.getEmail());
        }

        // "El rol Gerente es el único que puede modificar roles" — los usuarios con rol Médico
        // o Administrativo no pueden modificar su propio rol ni el de otros.
        if (dto.getRol() != null && !dto.getRol().equalsIgnoreCase(usuario.getRol())) {
            if (!RolEnum.GERENTE.name().equalsIgnoreCase(rolActor)) {
                throw new BusinessRuleException("Solo un usuario con rol Gerente puede modificar el rol de un usuario.");
            }
            validarUnicoRol(dto.getRol());
            usuario.setRol(dto.getRol().toUpperCase());
        }

        usuario = usuarioRepository.save(usuario);
        return mapToDTO(usuario);
    }

    @Override
    @Transactional
    public void eliminarUsuario(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado."));

        // RN-020: no se da de baja un usuario que todavía pertenece a un médico activo. Sin
        // este chequeo, Medico.ID_Usuario queda apuntando a una fila invisible para el
        // @SQLRestriction de Usuario, y Hibernate revienta con EntityNotFoundException al
        // resolver esa referencia — no solo para ese médico, sino para CUALQUIER listado que
        // lo incluya (GET /api/medicos entero). La baja de ese médico (RN-012) es el camino
        // correcto antes de poder dar de baja su usuario.
        if (medicoRepository.findByUsuario_IdUsuario(id).isPresent()) {
            throw new BusinessRuleException("RN-020: No se puede dar de baja: este usuario pertenece a un médico activo. Dá de baja al médico primero.");
        }

        // El @SQLDelete y @SQLRestriction de la entidad hacen el soft delete automático.
        usuarioRepository.delete(usuario);
    }

    @Override
    @Transactional
    public void reactivarUsuario(Integer id) {
        usuarioRepository.findByIdIncludingInactive(id)
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado."));
        usuarioRepository.reactivar(id);
    }

    @Override
    public UsuarioResponseDTO obtenerUsuario(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado."));
        return mapToDTO(usuario);
    }

    @Override
    public List<UsuarioResponseDTO> buscarUsuarios(String email, String rol, boolean incluirInactivos) {
        String emailFiltro = (email == null || email.isBlank()) ? null : email.trim();
        String rolFiltro = (rol == null || rol.isBlank()) ? null : rol.trim().toUpperCase();

        List<Usuario> usuarios = incluirInactivos
                ? usuarioRepository.buscarIncludingInactive(emailFiltro, rolFiltro)
                : usuarioRepository.buscar(emailFiltro, rolFiltro);

        return usuarios.stream().map(this::mapToDTO).collect(Collectors.toList());
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

    private UsuarioResponseDTO mapToDTO(Usuario u) {
        return UsuarioResponseDTO.builder()
                .idUsuario(u.getIdUsuario())
                .email(u.getEmail())
                .rol(u.getRol())
                .visible(u.getVisible())
                .fechaCreacion(u.getFechaCreacion())
                .build();
    }
}
