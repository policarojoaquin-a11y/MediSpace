package com.medispace.app.config;

import com.medispace.app.model.Usuario;
import com.medispace.app.model.enums.RolEnum;
import com.medispace.app.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class DataSeedConfig {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (usuarioRepository.count() == 0) {
                Usuario admin = Usuario.builder()
                        .email("admin@medispace.com")
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .rol(RolEnum.GERENTE.name())
                        .visible(true)
                        .fechaCreacion(LocalDate.now())
                        .build();

                usuarioRepository.save(admin);
                System.out.println("SEED: Usuario administrador creado (admin@medispace.com / admin123)");
            }
        };
    }
}
