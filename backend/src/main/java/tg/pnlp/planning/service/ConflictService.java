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
import java.util.*;

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
            // ✅ Règle R1 : une activité au programme n'est jamais source de conflit
            if (Boolean.TRUE.equals(activite.getAuProgramme())) {
                continue;
            }
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

        // ✅ Dédoublonner les affectations (au cas où la requête renvoie des doublons)
        Map<UUID, Affectation> uniquesParAgent = new LinkedHashMap<>();
        for (Affectation aff : affectations) {
            if (aff.getAgent() != null) {
                uniquesParAgent.putIfAbsent(aff.getAgent().getId(), aff);
            }
        }

        for (Affectation affectation : uniquesParAgent.values()) {
            Agent agent = affectation.getAgent();
            // ... reste inchangé
        }

        // ✅ Dédoublonnage final
        Map<String, ConflitDTO> uniques = new LinkedHashMap<>();
        for (ConflitDTO c : conflits) {
            String cle = c.getAgentId() + "|" + c.getTypeConflit() + "|"
                    + (c.getActiviteConflit() != null ? c.getActiviteConflit() : "")
                    + "|" + (c.getMotif() != null ? c.getMotif() : "");
            uniques.putIfAbsent(cle, c);
        }

        return new ArrayList<>(uniques.values());
    }
}