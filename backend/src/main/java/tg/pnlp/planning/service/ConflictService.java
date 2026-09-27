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
     * Vérifie si un agent est disponible pour une période donnée.
     *
     * @param agentId          ID de l'agent
     * @param debut            Date de début de la période
     * @param fin              Date de fin de la période
     * @param activiteIdExclue ID de l'activité à exclure de la vérification (null si aucune)
     * @return true si l'agent est disponible, false sinon
     */
    public boolean estDisponible(UUID agentId, LocalDate debut, LocalDate fin, UUID activiteIdExclue) {
        // 1. Vérifier les indisponibilités
        List<Indisponibilite> indisponibilites = indisponibiliteRepository
                .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        agentId, fin, debut);

        if (!indisponibilites.isEmpty()) {
            log.warn("Agent {} indisponible du {} au {} ({} indisponibilité(s))",
                    agentId, debut, fin, indisponibilites.size());
            return false;
        }

        // 2. Vérifier les affectations existantes EN EXCLUANT l'activité courante
        List<Affectation> affectations;
        if (activiteIdExclue != null) {
            // ✅ Utilise la requête avec exclusion pour éviter les faux positifs
            affectations = affectationRepository.findByAgentIdExcludingActivite(
                    agentId, activiteIdExclue, fin, debut);

            log.debug("Vérification conflit agent {} (exclusion activité {}): {} affectation(s)",
                    agentId, activiteIdExclue, affectations.size());
        } else {
            affectations = affectationRepository
                    .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
                            agentId, fin, debut);

            log.debug("Vérification conflit agent {} (sans exclusion): {} affectation(s)",
                    agentId, affectations.size());
        }

        if (!affectations.isEmpty()) {
            log.warn("Agent {} a déjà {} affectation(s) sur la période du {} au {}",
                    agentId, affectations.size(), debut, fin);
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

            // Vérifier les autres affectations (exclure l'activité courante)
            List<Affectation> autresAffectations = affectationRepository
                    .findByAgentIdExcludingActivite(agent.getId(), activiteId, fin, debut);

            for (Affectation autre : autresAffectations) {
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

        return conflits;
    }
}