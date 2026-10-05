package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.entity.Affectation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AffectationRepository extends JpaRepository<Affectation, UUID> {

    List<Affectation> findByAgentId(UUID agentId);

    List<Affectation> findByActiviteId(UUID activiteId);

    @Query("SELECT af FROM Affectation af " +
            "JOIN FETCH af.agent " +
            "WHERE af.activite.id = :activiteId")
    List<Affectation> findByActiviteIdWithAgent(@Param("activiteId") UUID activiteId);

    @Query("SELECT af FROM Affectation af " +
            "JOIN FETCH af.agent " +
            "WHERE af.activite.id IN :activiteIds")
    List<Affectation> findByActiviteIdInWithAgent(@Param("activiteIds") List<UUID> activiteIds);

    @Query("SELECT a FROM Affectation a " +
            "WHERE a.agent.id = :agentId " +
            "AND a.activite.dateDebut <= :fin " +
            "AND a.activite.dateFin >= :debut")
    List<Affectation> findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
            @Param("agentId") UUID agentId,
            @Param("fin") LocalDate fin,
            @Param("debut") LocalDate debut);

    @Query("SELECT a FROM Affectation a " +
            "WHERE a.agent.id = :agentId " +
            "AND a.activite.id <> :activiteIdExclure " +
            "AND a.activite.dateDebut <= :fin " +
            "AND a.activite.dateFin >= :debut")
    List<Affectation> findByAgentIdExcludingActivite(
            @Param("agentId") UUID agentId,
            @Param("activiteIdExclure") UUID activiteIdExclure,
            @Param("fin") LocalDate fin,
            @Param("debut") LocalDate debut);

    List<Affectation> findByAgentIdAndActiviteDateDebutBetween(
            UUID agentId, LocalDate debut, LocalDate fin);

    @Modifying
    @Transactional
    void deleteByActiviteId(UUID activiteId);

    @Query("""
    SELECT a FROM Affectation a
    JOIN FETCH a.agent ag
    WHERE a.activite.id = :activiteId
      AND ag.actif = true
""")
    List<Affectation> findActivesByActiviteId(@Param("activiteId") UUID activiteId);

    // ✅ NOUVEAU : compte les affectations d'une activité (utilisé pour vérifier s'il reste des agents)
    long countByActiviteId(UUID activiteId);

    // ✅ NOUVEAU : supprime les affectations d'une activité pour une liste d'agents
    @Modifying
    @Transactional
    @Query("DELETE FROM Affectation a WHERE a.activite.id = :activiteId AND a.agent.id IN :agentIds")
    void deleteByActiviteIdAndAgentIdIn(@Param("activiteId") UUID activiteId,
                                        @Param("agentIds") List<UUID> agentIds);
}