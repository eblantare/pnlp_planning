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

    /**
     * ✅ Récupère les activités d'une période DONNÉE.
     * Inclut :
     *  - Les activités dont la période chevauche le mois
     *  - Les BROUILLONS sans dates (pour qu'ils restent visibles)
     */
    @Query("""
        SELECT DISTINCT a FROM Activite a
        WHERE
            (a.dateDebut IS NOT NULL AND a.dateFin IS NOT NULL AND (
                (a.dateDebut BETWEEN :debut AND :fin)
                OR (a.dateFin BETWEEN :debut AND :fin)
                OR (a.dateDebut <= :debut AND a.dateFin >= :fin)
            ))
            OR (a.dateDebut IS NULL)
        ORDER BY a.dateDebut ASC NULLS LAST
    """)
    List<Activite> findByPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /**
     * ✅ Récupère les activités d'une période AVEC les affectations (FETCH JOIN).
     * Utilisé par StatistiquesService pour éviter les N+1.
     */
    @Query("""
        SELECT DISTINCT a FROM Activite a
        LEFT JOIN FETCH a.affectations aff
        LEFT JOIN FETCH aff.agent
        WHERE
            (a.dateDebut IS NOT NULL AND a.dateFin IS NOT NULL AND (
                (a.dateDebut BETWEEN :debut AND :fin)
                OR (a.dateFin BETWEEN :debut AND :fin)
                OR (a.dateDebut <= :debut AND a.dateFin >= :fin)
            ))
            OR (a.dateDebut IS NULL)
        ORDER BY a.dateDebut ASC NULLS LAST
    """)
    List<Activite> findByPeriodeWithAffectations(@Param("debut") LocalDate debut,
                                                 @Param("fin") LocalDate fin);

    @Query("SELECT a FROM Activite a ORDER BY a.dateDebut ASC NULLS LAST, a.createdAt DESC")
    List<Activite> findAllOrdered();

    @Query("SELECT a FROM Activite a WHERE a.dateDebut IS NULL")
    List<Activite> findBrouillonsSansDates();
}