package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.*;
import tg.pnlp.planning.entity.*;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.exception.ResourceNotFoundException;
import tg.pnlp.planning.repository.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningService {

    private final ActiviteRepository activiteRepository;
    private final AgentRepository agentRepository;
    private final AffectationRepository affectationRepository;
    private final IndisponibiliteRepository indisponibiliteRepository;
    private final ConflictService conflictService;

    /**
     * Crée une activité avec ses affectations
     */
    @Transactional
    public ActiviteDTO creerActivite(ActiviteDTO dto) {
        if (dto.getDateFin().isBefore(dto.getDateDebut())) {
            throw new BusinessException("La date de fin doit être après la date de début");
        }

        Activite activite = Activite.builder()
                .titre(dto.getTitre())
                .description(dto.getDescription())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .lieu(dto.getLieu())
                .sourceFinancement(dto.getSourceFinancement())
                .commentaires(dto.getCommentaires())
                .build();

        activite = activiteRepository.save(activite);

        if (dto.getAgentIds() != null && !dto.getAgentIds().isEmpty()) {
            affecterAgents(activite, dto.getAgentIds());
        }

        return convertToDTO(activite);
    }

    @Transactional
    public void affecterAgents(Activite activite, List<UUID> agentIds) {
        List<String> conflits = new ArrayList<>();

        for (UUID agentId : agentIds) {
            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

            if (!conflictService.estDisponible(agentId, activite.getDateDebut(),
                    activite.getDateFin(), activite.getId())) {
                conflits.add(agent.getNomComplet());
                continue;
            }

            Affectation affectation = Affectation.builder()
                    .agent(agent)
                    .activite(activite)
                    .build();

            affectationRepository.save(affectation);
        }

        if (!conflits.isEmpty()) {
            throw new BusinessException("Conflits détectés pour les agents: " +
                    String.join(", ", conflits));
        }
    }

    public int calculerJoursMission(UUID agentId, LocalDate debut, LocalDate fin) {
        List<Affectation> affectations = affectationRepository
                .findByAgentIdAndActiviteDateDebutBetween(agentId, debut, fin);

        return affectations.stream()
                .mapToInt(a -> a.getActivite().getNombreJours())
                .sum();
    }

    public Map<String, Integer> calculerJoursMissionMensuel(YearMonth mois) {
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();

        List<Agent> agents = agentRepository.findByActifTrue();
        Map<String, Integer> result = new HashMap<>();

        for (Agent agent : agents) {
            int jours = calculerJoursMission(agent.getId(), debut, fin);
            result.put(agent.getNomComplet(), jours);
        }

        return result;
    }

    public Map<String, Integer> calculerJoursMissionAnnuel(int annee) {
        LocalDate debut = LocalDate.of(annee, 1, 1);
        LocalDate fin = LocalDate.of(annee, 12, 31);

        List<Agent> agents = agentRepository.findByActifTrue();
        Map<String, Integer> result = new HashMap<>();

        for (Agent agent : agents) {
            int jours = calculerJoursMission(agent.getId(), debut, fin);
            result.put(agent.getNomComplet(), jours);
        }

        return result;
    }

    public DisponibiliteDTO verifierDisponibilite(UUID agentId, LocalDate debut, LocalDate fin) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé"));

        boolean disponible = conflictService.estDisponible(agentId, debut, fin, null);

        List<Indisponibilite> indisponibilites = indisponibiliteRepository
                .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        agentId, fin, debut);

        List<Affectation> affectations = affectationRepository
                .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
                        agentId, fin, debut);

        return DisponibiliteDTO.builder()
                .agentId(agentId)
                .agentNom(agent.getNomComplet())
                .disponible(disponible)
                .indisponibilites(indisponibilites.stream()
                        .map(this::convertIndisponibiliteToDTO)
                        .toList())
                .affectations(affectations.stream()
                        .map(this::convertAffectationToDTO)
                        .toList())
                .build();
    }

    /**
     * Génère le planning mensuel - avec FETCH JOIN pour éviter les problèmes de lazy loading
     */
    public PlanningMensuelDTO getPlanningMensuel(YearMonth mois) {
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();

        // ⚠️ IMPORTANT : Utiliser la méthode avec FETCH JOIN
        List<Activite> activites = activiteRepository.findByPeriodeWithAffectations(debut, fin);

        Map<UUID, List<ActiviteDTO>> planningParAgent = new HashMap<>();

        for (Activite activite : activites) {
            for (Affectation affectation : activite.getAffectations()) {
                if (affectation.getAgent() != null) {
                    UUID agentId = affectation.getAgent().getId();
                    planningParAgent.computeIfAbsent(agentId, k -> new ArrayList<>())
                            .add(convertToDTO(activite));
                }
            }
        }

        return PlanningMensuelDTO.builder()
                .mois(mois.toString())
                .activites(activites.stream().map(this::convertToDTO).toList())
                .planningParAgent(planningParAgent)
                .build();
    }

    private ActiviteDTO convertToDTO(Activite activite) {
        List<UUID> agentIds = new ArrayList<>();
        List<String> agentNoms = new ArrayList<>();

        if (activite.getAffectations() != null) {
            for (Affectation aff : activite.getAffectations()) {
                if (aff.getAgent() != null) {
                    agentIds.add(aff.getAgent().getId());
                    agentNoms.add(aff.getAgent().getNomComplet());
                }
            }
        }

        return ActiviteDTO.builder()
                .id(activite.getId())
                .titre(activite.getTitre())
                .description(activite.getDescription())
                .dateDebut(activite.getDateDebut())
                .dateFin(activite.getDateFin())
                .nombreJours(activite.getNombreJours())
                .lieu(activite.getLieu())
                .sourceFinancement(activite.getSourceFinancement())
                .statut(activite.getStatut().name())
                .commentaires(activite.getCommentaires())
                .agentIds(agentIds)
                .agentNoms(agentNoms)
                .build();
    }

    private IndisponibiliteDTO convertIndisponibiliteToDTO(Indisponibilite ind) {
        return IndisponibiliteDTO.builder()
                .id(ind.getId())
                .agentId(ind.getAgent().getId())
                .dateDebut(ind.getDateDebut())
                .dateFin(ind.getDateFin())
                .type(ind.getType().name())
                .motif(ind.getMotif())
                .build();
    }

    private AffectationDTO convertAffectationToDTO(Affectation aff) {
        return AffectationDTO.builder()
                .id(aff.getId())
                .agentId(aff.getAgent().getId())
                .agentNom(aff.getAgent().getNomComplet())
                .activiteId(aff.getActivite().getId())
                .activiteTitre(aff.getActivite().getTitre())
                .role(aff.getRole())
                .build();
    }

    @Transactional
    public ActiviteDTO updateActivite(UUID id, ActiviteDTO dto) {
        Activite activite = activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));

        activite.setTitre(dto.getTitre());
        activite.setDescription(dto.getDescription());
        activite.setDateDebut(dto.getDateDebut());
        activite.setDateFin(dto.getDateFin());
        activite.setLieu(dto.getLieu());
        activite.setSourceFinancement(dto.getSourceFinancement());

        activite = activiteRepository.save(activite);

        if (dto.getAgentIds() != null) {
            affectationRepository.deleteByActiviteId(id);
            affectationRepository.flush();

            for (UUID agentId : dto.getAgentIds()) {
                Agent agent = agentRepository.findById(agentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

                Affectation affectation = Affectation.builder()
                        .agent(agent)
                        .activite(activite)
                        .build();
                affectationRepository.save(affectation);
            }
        }

        return convertToDTO(activite);
    }

    @Transactional
    public ActiviteDTO changerStatut(UUID id, String statut) {
        Activite activite = activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));

        try {
            Activite.StatutActivite newStatut = Activite.StatutActivite.valueOf(statut.toUpperCase());
            activite.setStatut(newStatut);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Statut invalide: " + statut);
        }

        activite = activiteRepository.save(activite);
        return convertToDTO(activite);
    }

    @Transactional
    public void supprimerActivite(UUID id) {
        if (!activiteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Activité non trouvée: " + id);
        }
        affectationRepository.deleteByActiviteId(id);
        activiteRepository.deleteById(id);
    }

    public ActiviteDTO getActiviteById(UUID id) {
        Activite activite = activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));
        return convertToDTO(activite);
    }
}