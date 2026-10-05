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

    @Query("""
        SELECT DISTINCT a FROM Activite a
        WHERE
            (a.dateDebut IS NOT NULL AND a.dateFin IS NOT NULL AND (
                (a.dateDebut BETWEEN :debut AND :fin)
                OR (a.dateFin BETWEEN :debut AND :fin)
                OR (a.dateDebut <= :debut AND a.dateFin >= :fin)
            ))
            OR (a.dateDebut IS NULL)
            OR (a.statut = tg.pnlp.planning.entity.Activite$StatutActivite.EN_COURS)
            OR (a.statut = tg.pnlp.planning.entity.Activite$StatutActivite.EN_ATTENTE_VALIDATION)
        ORDER BY a.dateDebut ASC NULLS LAST
    """)
    List<Activite> findByPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

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
            OR (a.statut = tg.pnlp.planning.entity.Activite$StatutActivite.EN_COURS)
            OR (a.statut = tg.pnlp.planning.entity.Activite$StatutActivite.EN_ATTENTE_VALIDATION)
        ORDER BY a.dateDebut ASC NULLS LAST
    """)
    List<Activite> findByPeriodeWithAffectations(@Param("debut") LocalDate debut,
                                                 @Param("fin") LocalDate fin);

    @Query("SELECT a FROM Activite a ORDER BY a.dateDebut ASC NULLS LAST, a.createdAt DESC")
    List<Activite> findAllOrdered();

    @Query("SELECT a FROM Activite a WHERE a.dateDebut IS NULL")
    List<Activite> findBrouillonsSansDates();

    @Query("""
        SELECT DISTINCT a FROM Activite a
        LEFT JOIN FETCH a.affectations aff
        LEFT JOIN FETCH aff.agent
        WHERE a.dateDebut <= :fin AND a.dateFin >= :debut
          AND aff.agent.id = :agentId
    """)
    List<Activite> findByPeriodeWithAffectationsAndAgent(
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin,
            @Param("agentId") UUID agentId);

    /**
     * ✅ Récupère toutes les activités "au programme" qui :
     *   - ne sont PAS encore TERMINEE ni ANNULEE
     *   - chevauchent la période [debut, fin]
     *   - contiennent au moins un agent de la liste `agentIds`
     */
    @Query("""
        SELECT DISTINCT a FROM Activite a
        JOIN FETCH a.affectations aff
        WHERE a.auProgramme = true
          AND a.statut <> tg.pnlp.planning.entity.Activite$StatutActivite.TERMINEE
          AND a.statut <> tg.pnlp.planning.entity.Activite$StatutActivite.ANNULEE
          AND a.dateDebut IS NOT NULL
          AND a.dateFin IS NOT NULL
          AND a.dateDebut <= :fin
          AND a.dateFin >= :debut
          AND aff.agent.id IN :agentIds
    """)
    List<Activite> findActivitesProgrammeChevauchantesPourAgents(
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin,
            @Param("agentIds") List<UUID> agentIds);

    // ============================================================
    // ✅ NOUVEAU : Validation
    // ============================================================

    /**
     * Liste les activités EN_ATTENTE_VALIDATION pour un niveau donné (1, 2, 3).
     * Utilisé par le validateur pour afficher sa file d'attente.
     */
    @Query("""
        SELECT DISTINCT a FROM Activite a
        LEFT JOIN FETCH a.affectations aff
        LEFT JOIN FETCH aff.agent
        WHERE a.statut = tg.pnlp.planning.entity.Activite$StatutActivite.EN_ATTENTE_VALIDATION
          AND a.niveauValidationActuel = :niveau
        ORDER BY a.createdAt ASC
    """)
    List<Activite> findByStatutEnAttenteAndNiveau(@Param("niveau") Integer niveau);

    /**
     * Compteur pour le badge : nombre d'activités en attente pour un niveau.
     */
    long countByStatutAndNiveauValidationActuel(Activite.StatutActivite statut, Integer niveau);
}