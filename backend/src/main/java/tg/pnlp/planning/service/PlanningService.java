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

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningService {

    private final ActiviteRepository activiteRepository;
    private final AgentRepository agentRepository;
    private final AffectationRepository affectationRepository;
    private final IndisponibiliteRepository indisponibiliteRepository;
    private final ConflictService conflictService;

    // ============================================================
    // CRÉATION D'UNE ACTIVITÉ
    // ============================================================

    @Transactional
    public ReponseCreationActiviteDTO creerActivite(ActiviteDTO dto) {
        Activite.StatutActivite statutInitial = Activite.StatutActivite.BROUILLON;
        if (dto.getStatut() != null && !dto.getStatut().isBlank()) {
            try {
                statutInitial = Activite.StatutActivite.valueOf(dto.getStatut().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Statut invalide: " + dto.getStatut());
            }
        }

        validerActivite(dto, statutInitial);

        if (dto.getDateDebut() != null && dto.getDateFin() != null
                && dto.getDateFin().isBefore(dto.getDateDebut())) {
            throw new BusinessException("La date de fin doit être après la date de début");
        }

        Activite activite = Activite.builder()
                .titre(dto.getTitre())
                .description(dto.getCommentaires())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .lieu(dto.getLieu())
                .sourceFinancement(dto.getSourceFinancement())
                .commentaires(dto.getCommentaires())
                .statut(statutInitial)
                .build();

        List<UUID> agentIds = new ArrayList<>();
        List<UUID> agentIdsForces = new ArrayList<>();
        Map<UUID, String> agentZones = new HashMap<>();

        if (statutInitial != Activite.StatutActivite.BROUILLON) {
            agentIds = dto.getAgentIds() != null ? dto.getAgentIds() : List.of();
            agentIdsForces = dto.getAgentIdsForces() != null ? dto.getAgentIdsForces() : List.of();
            agentZones = dto.getAgentZones() != null ? dto.getAgentZones() : Map.of();
        } else {
            log.info("Création d'un BROUILLON sans agents");
        }

        List<ConflitAgentDTO> conflits = new ArrayList<>();
        if (!agentIds.isEmpty() && statutInitial != Activite.StatutActivite.BROUILLON) {
            conflits = detecterConflits(activite, agentIds, agentIdsForces);
        }

        if (!conflits.isEmpty()) {
            return ReponseCreationActiviteDTO.builder()
                    .succes(false)
                    .conflits(conflits)
                    .message("Conflits détectés. Veuillez confirmer ou retirer les agents concernés.")
                    .build();
        }

        activite = activiteRepository.save(activite);

        if (!agentIds.isEmpty()) {
            creerAffectations(activite, agentIds, agentZones);
        }

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(activite.getId());

        return ReponseCreationActiviteDTO.builder()
                .succes(true)
                .activite(convertToDTOWithAffectations(activite, affectations))
                .message("Activité créée avec succès")
                .build();
    }

    private void validerActivite(ActiviteDTO dto, Activite.StatutActivite statut) {
        if (dto.getTitre() == null || dto.getTitre().isBlank()) {
            throw new BusinessException("Le titre est obligatoire");
        }

        if (statut == Activite.StatutActivite.BROUILLON) {
            return;
        }

        if (dto.getDateDebut() == null) {
            throw new BusinessException("La date de début est obligatoire pour une activité planifiée");
        }
        if (dto.getDateFin() == null) {
            throw new BusinessException("La date de fin est obligatoire pour une activité planifiée");
        }
        if (dto.getLieu() == null || dto.getLieu().isBlank()) {
            throw new BusinessException("Le lieu est obligatoire pour une activité planifiée");
        }
        if (dto.getAgentIds() == null || dto.getAgentIds().isEmpty()) {
            throw new BusinessException("Au moins un participant est obligatoire pour une activité planifiée");
        }
    }

    private List<ConflitAgentDTO> detecterConflits(Activite activite,
                                                   List<UUID> agentIds,
                                                   List<UUID> agentIdsForces) {
        List<ConflitAgentDTO> conflits = new ArrayList<>();

        if (activite.getDateDebut() == null || activite.getDateFin() == null) {
            return conflits;
        }

        for (UUID agentId : agentIds) {
            if (agentIdsForces.contains(agentId)) continue;

            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

            List<Indisponibilite> indispos = indisponibiliteRepository
                    .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                            agentId, activite.getDateFin(), activite.getDateDebut());

            for (Indisponibilite ind : indispos) {
                conflits.add(ConflitAgentDTO.builder()
                        .agentId(agent.getId())
                        .agentNom(agent.getNom())
                        .agentPrenom(agent.getPrenom())
                        .agentPoste(agent.getPoste())
                        .typeConflit("INDISPONIBILITE")
                        .motif(ind.getMotif())
                        .dateDebut(ind.getDateDebut())
                        .dateFin(ind.getDateFin())
                        .build());
            }

            List<Affectation> affectations = affectationRepository
                    .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
                            agentId, activite.getDateFin(), activite.getDateDebut());

            for (Affectation aff : affectations) {
                if (activite.getId() != null &&
                        aff.getActivite().getId().equals(activite.getId())) {
                    continue;
                }
                if (aff.getActivite().getStatut() == Activite.StatutActivite.BROUILLON) {
                    continue;
                }

                conflits.add(ConflitAgentDTO.builder()
                        .agentId(agent.getId())
                        .agentNom(agent.getNom())
                        .agentPrenom(agent.getPrenom())
                        .agentPoste(agent.getPoste())
                        .typeConflit("AFFECTATION_SIMULTANEE")
                        .activiteConflit(aff.getActivite().getTitre())
                        .dateDebut(aff.getActivite().getDateDebut())
                        .dateFin(aff.getActivite().getDateFin())
                        .build());
            }
        }

        return conflits;
    }

    public List<AgentDTO> trouverRemplacantsPossibles(LocalDate debut, LocalDate fin) {
        List<Agent> tousAgents = agentRepository.findByActifTrue();
        List<AgentDTO> remplacants = new ArrayList<>();

        for (Agent agent : tousAgents) {
            boolean disponible = conflictService.estDisponible(agent.getId(), debut, fin, null);
            if (disponible) {
                remplacants.add(convertAgentToDTO(agent));
            }
        }

        return remplacants;
    }

    private AgentDTO convertAgentToDTO(Agent agent) {
        return AgentDTO.builder()
                .id(agent.getId())
                .nom(agent.getNom())
                .prenom(agent.getPrenom())
                .poste(agent.getPoste())
                .actif(agent.getActif())
                .build();
    }

    private void creerAffectations(Activite activite,
                                   List<UUID> agentIds,
                                   Map<UUID, String> agentZones) {
        for (UUID agentId : agentIds) {
            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

            String zone = agentZones != null ? agentZones.get(agentId) : null;

            Affectation affectation = Affectation.builder()
                    .agent(agent)
                    .activite(activite)
                    .zoneAffectation(zone)
                    .build();
            affectationRepository.save(affectation);
        }
        log.info("{} agent(s) affecté(s) à l'activité '{}'", agentIds.size(), activite.getTitre());
    }

    // ============================================================
    // MISE À JOUR D'UNE ACTIVITÉ
    // ============================================================

    @Transactional
    public ReponseCreationActiviteDTO updateActivite(UUID id, ActiviteDTO dto) {
        Activite activite = activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));

        Activite.StatutActivite nouveauStatut = activite.getStatut();
        if (dto.getStatut() != null && !dto.getStatut().isBlank()) {
            try {
                nouveauStatut = Activite.StatutActivite.valueOf(dto.getStatut().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Statut invalide: " + dto.getStatut());
            }
        }

        validerActivite(dto, nouveauStatut);

        if (dto.getDateDebut() != null && dto.getDateFin() != null
                && dto.getDateFin().isBefore(dto.getDateDebut())) {
            throw new BusinessException("La date de fin doit être après la date de début");
        }

        activite.setTitre(dto.getTitre());
        activite.setDescription(dto.getCommentaires());
        activite.setDateDebut(dto.getDateDebut());
        activite.setDateFin(dto.getDateFin());
        activite.setLieu(dto.getLieu());
        activite.setSourceFinancement(dto.getSourceFinancement());
        activite.setCommentaires(dto.getCommentaires());
        activite.setStatut(nouveauStatut);

        List<UUID> agentIds = new ArrayList<>();
        List<UUID> agentIdsForces = new ArrayList<>();
        Map<UUID, String> agentZones = new HashMap<>();

        if (nouveauStatut != Activite.StatutActivite.BROUILLON) {
            agentIds = dto.getAgentIds() != null ? dto.getAgentIds() : List.of();
            agentIdsForces = dto.getAgentIdsForces() != null ? dto.getAgentIdsForces() : List.of();
            agentZones = dto.getAgentZones() != null ? dto.getAgentZones() : Map.of();
        } else {
            log.info("Passage en BROUILLON - suppression des affectations existantes");
            affectationRepository.deleteByActiviteId(id);
            affectationRepository.flush();
        }

        List<ConflitAgentDTO> conflits = new ArrayList<>();
        if (!agentIds.isEmpty() && nouveauStatut != Activite.StatutActivite.BROUILLON) {
            conflits = detecterConflits(activite, agentIds, agentIdsForces);
        }

        if (!conflits.isEmpty()) {
            return ReponseCreationActiviteDTO.builder()
                    .succes(false)
                    .conflits(conflits)
                    .message("Conflits détectés. Veuillez confirmer ou retirer les agents concernés.")
                    .build();
        }

        activite = activiteRepository.save(activite);

        if (nouveauStatut != Activite.StatutActivite.BROUILLON) {
            if (dto.getAgentIds() != null) {
                affectationRepository.deleteByActiviteId(id);
                affectationRepository.flush();

                if (!agentIds.isEmpty()) {
                    creerAffectations(activite, agentIds, agentZones);
                }
            }
        }

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(id);

        return ReponseCreationActiviteDTO.builder()
                .succes(true)
                .activite(convertToDTOWithAffectations(activite, affectations))
                .message("Activité modifiée avec succès")
                .build();
    }

    // ============================================================
    // ✅ MÉTHODES MANQUANTES (utilisées par PlanningController)
    // ============================================================

    public DisponibiliteDTO verifierDisponibilite(UUID agentId, LocalDate debut, LocalDate fin) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

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

    public int calculerJoursMission(UUID agentId, LocalDate debut, LocalDate fin) {
        List<Affectation> affectations = affectationRepository
                .findByAgentIdAndActiviteDateDebutBetween(agentId, debut, fin);

        return affectations.stream()
                .mapToInt(a -> a.getActivite().getNombreJours() != null
                        ? a.getActivite().getNombreJours() : 0)
                .sum();
    }

    // ============================================================
    // PLANNING MENSUEL
    // ============================================================

    public PlanningMensuelDTO getPlanningMensuel(YearMonth mois) {
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();

        List<Activite> activites = activiteRepository.findByPeriode(debut, fin);

        List<UUID> activiteIds = activites.stream()
                .map(Activite::getId)
                .toList();

        Map<UUID, List<Affectation>> affectationsParActivite = new HashMap<>();
        if (!activiteIds.isEmpty()) {
            List<Affectation> toutesAffectations = affectationRepository
                    .findByActiviteIdInWithAgent(activiteIds);

            for (Affectation aff : toutesAffectations) {
                UUID activiteId = aff.getActivite().getId();
                affectationsParActivite
                        .computeIfAbsent(activiteId, k -> new ArrayList<>())
                        .add(aff);
            }
        }

        List<ActiviteDTO> activiteDTOs = activites.stream()
                .map(a -> convertToDTOWithAffectations(a,
                        affectationsParActivite.getOrDefault(a.getId(), List.of())))
                .toList();

        Map<UUID, List<ActiviteDTO>> planningParAgent = new HashMap<>();
        for (ActiviteDTO dto : activiteDTOs) {
            if (dto.getAgentIds() != null) {
                for (UUID agentId : dto.getAgentIds()) {
                    planningParAgent.computeIfAbsent(agentId, k -> new ArrayList<>())
                            .add(dto);
                }
            }
        }

        return PlanningMensuelDTO.builder()
                .mois(mois.toString())
                .activites(activiteDTOs)
                .planningParAgent(planningParAgent)
                .build();
    }

    // ============================================================
    // CONVERSIONS
    // ============================================================

    private ActiviteDTO convertToDTOWithAffectations(Activite activite,
                                                     List<Affectation> affectations) {
        List<UUID> agentIds = new ArrayList<>();
        List<String> agentNoms = new ArrayList<>();
        List<String> agentZonesList = new ArrayList<>();

        if (affectations != null) {
            for (Affectation aff : affectations) {
                if (aff.getAgent() != null) {
                    agentIds.add(aff.getAgent().getId());
                    agentNoms.add(aff.getAgent().getNomComplet());
                    agentZonesList.add(aff.getZoneAffectation());
                }
            }
        }

        return ActiviteDTO.builder()
                .id(activite.getId())
                .titre(activite.getTitre())
                .commentaires(activite.getCommentaires())
                .dateDebut(activite.getDateDebut())
                .dateFin(activite.getDateFin())
                .nombreJours(activite.getNombreJours())
                .lieu(activite.getLieu())
                .sourceFinancement(activite.getSourceFinancement())
                .statut(activite.getStatut().name())
                .agentIds(agentIds)
                .agentNoms(agentNoms)
                .agentZonesList(agentZonesList)
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

    // ============================================================
    // CHANGER STATUT / SUPPRIMER / GET BY ID
    // ============================================================

    @Transactional
    public ActiviteDTO changerStatut(UUID id, String statut) {
        Activite activite = activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));

        Activite.StatutActivite ancienStatut = activite.getStatut();
        Activite.StatutActivite newStatut;

        try {
            newStatut = Activite.StatutActivite.valueOf(statut.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Statut invalide: " + statut);
        }

        if (ancienStatut == Activite.StatutActivite.BROUILLON
                && newStatut == Activite.StatutActivite.PLANIFIEE) {
            validerPassageEnPlanifiee(activite);
        }

        if (ancienStatut == Activite.StatutActivite.BROUILLON
                && (newStatut == Activite.StatutActivite.EN_COURS
                || newStatut == Activite.StatutActivite.TERMINEE)) {
            throw new BusinessException(
                    "Une activité en Brouillon doit d'abord être Planifiée avant de changer d'état."
            );
        }

        if (newStatut == Activite.StatutActivite.BROUILLON
                && ancienStatut != Activite.StatutActivite.BROUILLON) {
            log.info("Passage en BROUILLON - suppression des affectations");
            affectationRepository.deleteByActiviteId(id);
            affectationRepository.flush();
        }

        activite.setStatut(newStatut);
        activite = activiteRepository.save(activite);

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(id);
        return convertToDTOWithAffectations(activite, affectations);
    }

    private void validerPassageEnPlanifiee(Activite activite) {
        List<String> erreurs = new ArrayList<>();

        if (activite.getDateDebut() == null) erreurs.add("la date de début");
        if (activite.getDateFin() == null) erreurs.add("la date de fin");
        if (activite.getLieu() == null || activite.getLieu().isBlank()) erreurs.add("le lieu");

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(activite.getId());
        if (affectations.isEmpty()) erreurs.add("au moins un participant");

        if (!erreurs.isEmpty()) {
            throw new BusinessException(
                    "Impossible de planifier : veuillez renseigner " + String.join(", ", erreurs) + "."
            );
        }
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

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(id);

        return convertToDTOWithAffectations(activite, affectations);
    }
}