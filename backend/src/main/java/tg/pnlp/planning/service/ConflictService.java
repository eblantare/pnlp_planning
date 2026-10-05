package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.dto.ConflitDTO;
import tg.pnlp.planning.entity.Activite;
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

    public boolean estDisponible(UUID agentId, LocalDate debut, LocalDate fin, UUID activiteAExclure) {

        // 1. Indisponibilités
        List<Indisponibilite> indispos = indisponibiliteRepository
                .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(agentId, fin, debut);
        if (!indispos.isEmpty()) {
            return false;
        }

        // 2. Affectations actives chevauchantes
        List<Affectation> affectations = affectationRepository.findByAgentId(agentId);
        for (Affectation aff : affectations) {
            if (activiteAExclure != null && aff.getActivite().getId().equals(activiteAExclure)) {
                continue;
            }

            Activite activite = aff.getActivite();
            Activite.StatutActivite statut = activite.getStatut();

            if (statut == Activite.StatutActivite.BROUILLON
                    || statut == Activite.StatutActivite.ANNULEE
                    || statut == Activite.StatutActivite.TERMINEE
                    || statut == Activite.StatutActivite.REPORTEE) {
                continue;
            }

            boolean chevauche = !activite.getDateFin().isBefore(debut)
                    && !activite.getDateDebut().isAfter(fin);
            if (chevauche) {
                return false;
            }
        }

        return true;
    }

    public List<ConflitDTO> detecterConflits(UUID activiteId, LocalDate debut, LocalDate fin) {
        List<ConflitDTO> conflits = new ArrayList<>();
        List<Affectation> affectations = affectationRepository.findByActiviteId(activiteId);

        for (Affectation affectation : affectations) {
            Agent agent = affectation.getAgent();

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