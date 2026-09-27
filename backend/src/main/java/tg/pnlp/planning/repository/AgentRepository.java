package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tg.pnlp.planning.entity.Agent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByActifTrue();

    List<Agent> findByDirectionId(UUID directionId);

    List<Agent> findByDistrictId(UUID districtId);

    @Query("SELECT a FROM Agent a WHERE a.actif = true " +
            "AND NOT EXISTS (SELECT i FROM Indisponibilite i WHERE i.agent = a " +
            "AND i.dateDebut <= :fin AND i.dateFin >= :debut)")
    List<Agent> findDisponibles(@Param("debut") LocalDate debut,
                                @Param("fin") LocalDate fin);
    boolean existsByEmail(String email);
}