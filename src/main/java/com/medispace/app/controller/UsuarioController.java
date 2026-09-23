package com.medispace.app.controller;

import com.medispace.app.dto.UsuarioCreateDTO;
import com.medispace.app.dto.UsuarioResponseDTO;
import com.medispace.app.dto.UsuarioUpdateDTO;
import com.medispace.app.exception.BusinessRuleException;
import com.medispace.app.model.Usuario;
import com.medispace.app.model.enums.RolEnum;
import com.medispace.app.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<UsuarioResponseDTO> crearUsuario(@RequestBody UsuarioCreateDTO dto) {
        // RF-U1: el alta de un usuario con rol Médico se hace exclusivamente vía el módulo
        // Médicos (MedicoService.crearMedico -> UsuarioService.crearUsuario), que garantiza la
        // integridad referencial con la entidad Médico. UsuarioServiceImpl.crearUsuario se
        // mantiene sin esta restricción a propósito porque ese es justamente su único caller
        // interno; la restricción se aplica acá, en la puerta de entrada directa del módulo.
        if (dto.getRol() != null && RolEnum.MEDICO.name().equalsIgnoreCase(dto.getRol().trim())) {
            throw new BusinessRuleException("El alta de un usuario con rol Médico se realiza desde el módulo Médicos, no desde Usuarios.");
        }

        // Regla de negocio (checklist 27/08): un ADMINISTRATIVO solo puede dar de alta usuarios
        // con rol Administrativo — nunca Gerente. Solo un GERENTE crea otros GERENTE.
        // Ver docs/ANEXO_CAMBIOS_POST_ENTREGA.md.
        String rolNuevo = dto.getRol() != null ? dto.getRol().trim().toUpperCase() : "";
        if (RolEnum.ADMINISTRATIVO.name().equalsIgnoreCase(rolDelActorAutenticado())
                && !RolEnum.ADMINISTRATIVO.name().equals(rolNuevo)) {
            throw new BusinessRuleException("Un usuario Administrativo solo puede dar de alta usuarios con rol Administrativo.");
        }

        Usuario creado = usuarioService.crearUsuario(dto);
        UsuarioResponseDTO response = usuarioService.obtenerUsuario(creado.getIdUsuario());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<UsuarioResponseDTO> actualizarUsuario(
            @PathVariable Integer id,
            @RequestBody UsuarioUpdateDTO dto) {
        String rolActor = rolDelActorAutenticado();
        UsuarioResponseDTO response = usuarioService.actualizarUsuario(id, dto, rolActor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuario(@PathVariable Integer id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuario(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<List<UsuarioResponseDTO>> buscarUsuarios(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos) {
        return ResponseEntity.ok(usuarioService.buscarUsuarios(email, rol, incluirInactivos));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMINISTRATIVO')")
    public ResponseEntity<UsuarioResponseDTO> reactivarUsuario(@PathVariable Integer id) {
        usuarioService.reactivarUsuario(id);
        return ResponseEntity.ok(usuarioService.obtenerUsuario(id));
    }

    private String rolDelActorAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario actor) {
            return actor.getRol();
        }
        return null;
    }
}
