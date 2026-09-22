package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tg.pnlp.planning.entity.Indisponibilite;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface IndisponibiliteRepository extends JpaRepository<Indisponibilite, UUID> {

    List<Indisponibilite> findByAgentId(UUID agentId);

    List<Indisponibilite> findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
            UUID agentId, LocalDate fin, LocalDate debut);
}