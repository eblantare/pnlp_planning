package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tg.pnlp.planning.entity.Affectation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AffectationRepository extends JpaRepository<Affectation, UUID> {

    List<Affectation> findByAgentId(UUID agentId);

    List<Affectation> findByActiviteId(UUID activiteId);

    List<Affectation> findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
            UUID agentId, LocalDate fin, LocalDate debut);
    List<Affectation> findByAgentIdAndActiviteDateDebutBetween(
            UUID agentId, LocalDate debut, LocalDate fin);
    @Modifying
    @Transactional
    void deleteByActiviteId(UUID activiteId);

}