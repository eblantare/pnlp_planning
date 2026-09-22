package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tg.pnlp.planning.entity.Activite;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActiviteRepository extends JpaRepository<Activite, UUID> {

    @Query("SELECT a FROM Activite a WHERE " +
            "(a.dateDebut BETWEEN :debut AND :fin) OR " +
            "(a.dateFin BETWEEN :debut AND :fin) OR " +
            "(a.dateDebut <= :debut AND a.dateFin >= :fin)")
    List<Activite> findByPeriode(@Param("debut") LocalDate debut,
                                 @Param("fin") LocalDate fin);

    /**
     * Charge les activités avec leurs affectations et agents (FETCH JOIN)
     * Évite les problèmes de LazyInitializationException
     */
    @Query("SELECT DISTINCT a FROM Activite a " +
            "LEFT JOIN FETCH a.affectations af " +
            "LEFT JOIN FETCH af.agent " +
            "WHERE (a.dateDebut <= :fin AND a.dateFin >= :debut)")
    List<Activite> findByPeriodeWithAffectations(@Param("debut") LocalDate debut,
                                                 @Param("fin") LocalDate fin);

    List<Activite> findByStatut(String statut);
}