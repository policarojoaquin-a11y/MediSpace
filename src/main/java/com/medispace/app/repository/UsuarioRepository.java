package com.medispace.app.repository;

import com.medispace.app.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByEmail(String email);

    // Usado para validar unicidad de email (alta/edición): el @SQLRestriction("Visible = 1")
    // de la entidad no aplica a queries nativas, así que esto sí ve usuarios inactivos.
    @Query(value = "SELECT COUNT(*) FROM Usuarios WHERE LOWER(Email) = LOWER(:email)", nativeQuery = true)
    long countByEmailIncludingInactive(@Param("email") String email);

    // Búsqueda con filtros opcionales, respeta el soft delete (solo usuarios activos).
    @Query("SELECT u FROM Usuario u " +
            "WHERE (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) " +
            "AND (:rol IS NULL OR u.rol = :rol) " +
            "ORDER BY u.email")
    List<Usuario> buscar(@Param("email") String email, @Param("rol") String rol);

    // Misma búsqueda pero incluyendo usuarios inactivos (query nativa: bypassa @SQLRestriction).
    @Query(value = "SELECT * FROM Usuarios " +
            "WHERE (:email IS NULL OR LOWER(Email) LIKE LOWER(CONCAT('%', :email, '%'))) " +
            "AND (:rol IS NULL OR Rol = :rol) " +
            "ORDER BY Email", nativeQuery = true)
    List<Usuario> buscarIncludingInactive(@Param("email") String email, @Param("rol") String rol);

    // Bypassa @SQLRestriction para poder encontrar (y luego reactivar) un usuario inactivo por ID.
    @Query(value = "SELECT * FROM Usuarios WHERE ID_Usuario = :id", nativeQuery = true)
    Optional<Usuario> findByIdIncludingInactive(@Param("id") Integer id);

    // clearAutomatically=true es necesario: con spring.jpa.open-in-view (default), el
    // EntityManager vive durante todo el request HTTP. findByIdIncludingInactive() (arriba)
    // puede haber dejado el Usuario cacheado en el contexto de persistencia con Visible=0;
    // como este UPDATE es nativo, Hibernate no se entera y devolvería ese objeto stale en
    // cualquier findById() posterior dentro del mismo request si no se limpia la caché acá.
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE Usuarios SET Visible = 1 WHERE ID_Usuario = :id", nativeQuery = true)
    void reactivar(@Param("id") Integer id);
}
