package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.dto.ConflitDTO;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.entity.Affectation;
import tg.pnlp.planning.entity.Indisponibilite;
import tg.pnlp.planning.repository.AffectationRepository;
import tg.pnlp.planning.repository.IndisponibiliteRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictService {

    private final AffectationRepository affectationRepository;
    private final IndisponibiliteRepository indisponibiliteRepository;

    /**
     * Vérifie si un agent est disponible pour une période donnée
     */
    public boolean estDisponible(UUID agentId, LocalDate debut, LocalDate fin, UUID activiteIdExclue) {
        // Vérifier les indisponibilités
        List<Indisponibilite> indisponibilites = indisponibiliteRepository
                .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        agentId, fin, debut);

        if (!indisponibilites.isEmpty()) {
            log.warn("Agent {} indisponible du {} au {} (indisponibilité)",
                    agentId, debut, fin);
            return false;
        }

        // Vérifier les affectations existantes
        List<Affectation> affectations = affectationRepository
                .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
                        agentId, fin, debut);

        // Exclure l'activité en cours de modification
        if (activiteIdExclue != null) {
            affectations = affectations.stream()
                    .filter(a -> !a.getActivite().getId().equals(activiteIdExclue))
                    .toList();
        }

        if (!affectations.isEmpty()) {
            log.warn("Agent {} a déjà {} affectation(s) sur la période",
                    agentId, affectations.size());
            return false;
        }

        return true;
    }

    /**
     * Détecte tous les conflits pour une activité donnée
     */
    public List<ConflitDTO> detecterConflits(UUID activiteId, LocalDate debut, LocalDate fin) {
        List<ConflitDTO> conflits = new ArrayList<>();

        List<Affectation> affectations = affectationRepository.findByActiviteId(activiteId);

        for (Affectation affectation : affectations) {
            Agent agent = affectation.getAgent();

            // Vérifier les indisponibilités
            List<Indisponibilite> indisponibilites = indisponibiliteRepository
                    .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                            agent.getId(), fin, debut);

            for (Indisponibilite ind : indisponibilites) {
                conflits.add(ConflitDTO.builder()
                        .agentId(agent.getId())
                        .agentNom(agent.getNomComplet())
                        .typeConflit("INDISPONIBILITE")
                        .dateDebut(ind.getDateDebut())
                        .dateFin(ind.getDateFin())
                        .motif(ind.getMotif())
                        .build());
            }

            // Vérifier les autres affectations
            List<Affectation> autresAffectations = affectationRepository
                    .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
                            agent.getId(), fin, debut);

            for (Affectation autre : autresAffectations) {
                if (!autre.getActivite().getId().equals(activiteId)) {
                    conflits.add(ConflitDTO.builder()
                            .agentId(agent.getId())
                            .agentNom(agent.getNomComplet())
                            .typeConflit("AFFECTATION_SIMULTANEE")
                            .activiteConflit(autre.getActivite().getTitre())
                            .dateDebut(autre.getActivite().getDateDebut())
                            .dateFin(autre.getActivite().getDateFin())
                            .build());
                }
            }
        }

        return conflits;
    }
}