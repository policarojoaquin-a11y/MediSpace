package com.medispace.app.repository;

import com.medispace.app.model.ObraSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObraSocialRepository extends JpaRepository<ObraSocial, Integer> {
    Optional<ObraSocial> findByNombre(String nombre);
    boolean existsByNombre(String nombre);

    // Unicidad de nombre en la edición: excluye la propia obra social que se está modificando.
    // Query nativa: el @SQLRestriction("Visible = 1") de la entidad no aplica acá, así que
    // también ve nombres usados por obras sociales inactivas (RF-OS3: el nombre debe seguir
    // siendo único incluso contra registros dados de baja).
    @Query(value = "SELECT COUNT(*) FROM ObraSocial WHERE LOWER(Nombre) = LOWER(:nombre) AND ID_ObraSocial <> :id", nativeQuery = true)
    long countByNombreIncludingInactiveExcludingId(@Param("nombre") String nombre, @Param("id") Integer id);

    // Bypassa @SQLRestriction para poder encontrar (y luego reactivar) una obra social inactiva.
    @Query(value = "SELECT * FROM ObraSocial WHERE ID_ObraSocial = :id", nativeQuery = true)
    Optional<ObraSocial> findByIdIncludingInactive(@Param("id") Integer id);

    // Búsqueda con filtros opcionales, respeta el soft delete (solo obras sociales activas).
    @Query("SELECT o FROM ObraSocial o " +
            "WHERE (:nombre IS NULL OR LOWER(o.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))) " +
            "AND (:requiereBono IS NULL OR o.requiereBono = :requiereBono) " +
            "ORDER BY o.nombre")
    List<ObraSocial> buscar(@Param("nombre") String nombre, @Param("requiereBono") Boolean requiereBono);

    // Misma búsqueda pero incluyendo inactivas (query nativa: bypassa @SQLRestriction).
    @Query(value = "SELECT * FROM ObraSocial " +
            "WHERE (:nombre IS NULL OR LOWER(Nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))) " +
            "AND (:requiereBono IS NULL OR Requiere_Bono = :requiereBono) " +
            "ORDER BY Nombre", nativeQuery = true)
    List<ObraSocial> buscarIncludingInactive(@Param("nombre") String nombre, @Param("requiereBono") Boolean requiereBono);

    // RN-016: no se da de baja una obra social con médicos activos asociados. Filtra tanto
    // médicos dados de baja (m.Visible) como relaciones médico-obra social dadas de baja
    // (mos.Visible, desde que esa tabla tiene baja lógica propia con Importe_Coseguro).
    @Query(value = "SELECT COUNT(*) FROM Medico_ObraSocial mos " +
            "INNER JOIN Medicos m ON m.ID_Medico = mos.ID_Medico " +
            "WHERE mos.ID_ObraSocial = :idObraSocial AND mos.Visible = 1 AND m.Visible = 1", nativeQuery = true)
    long countMedicosActivosAsociados(@Param("idObraSocial") Integer idObraSocial);

    // RF-OS4: cantidad de médicos asociados a mostrar en el listado (misma lógica que arriba,
    // sin duplicar la condición en dos lugares distintos del código).
    @Query(value = "SELECT COUNT(*) FROM Medico_ObraSocial mos " +
            "INNER JOIN Medicos m ON m.ID_Medico = mos.ID_Medico " +
            "WHERE mos.ID_ObraSocial = :idObraSocial AND mos.Visible = 1 AND m.Visible = 1", nativeQuery = true)
    long countMedicosAsociados(@Param("idObraSocial") Integer idObraSocial);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE ObraSocial SET Visible = 1 WHERE ID_ObraSocial = :id", nativeQuery = true)
    void reactivar(@Param("id") Integer id);
}
