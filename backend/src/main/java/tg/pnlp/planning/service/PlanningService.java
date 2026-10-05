package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.pnlp.planning.dto.*;
import tg.pnlp.planning.entity.*;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.exception.ResourceNotFoundException;
import tg.pnlp.planning.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final FileStorageService fileStorageService;
    private final EmailService emailService;

    private Integer calculerNombreJours(ActiviteDTO dto) {
        if (dto.getDateDebut() == null || dto.getDateFin() == null) {
            return null;
        }

        long jours = java.time.temporal.ChronoUnit.DAYS
                .between(dto.getDateDebut(), dto.getDateFin()) + 1;

        boolean isNonResident = "NON_RESIDENT".equalsIgnoreCase(dto.getTypeLieu());
        boolean isAuProgramme = dto.getAuProgramme() != null && dto.getAuProgramme();

        if (isNonResident && !isAuProgramme) {
            jours = jours + 1;
        }

        return (int) Math.max(0, jours);
    }

    // ============================================================
    // CRÉATION
    // ============================================================

    @Transactional
    public ReponseCreationActiviteDTO creerActivite(ActiviteDTO dto) {
        boolean estAuProgramme = dto.getAuProgramme() != null && dto.getAuProgramme();

        Activite.StatutActivite statutInitial = Activite.StatutActivite.BROUILLON;

        if (dto.getStatut() != null && !dto.getStatut().isBlank()) {
            try {
                Activite.StatutActivite requested = Activite.StatutActivite.valueOf(dto.getStatut().toUpperCase());
                if (requested != Activite.StatutActivite.BROUILLON) {
                    log.info("Création forcée en BROUILLON (le statut '{}' sera appliqué via changerStatut après upload du TDR)",
                            requested);
                }
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
                .nombreJours(calculerNombreJours(dto))
                .lieu(dto.getLieu())
                .sourceFinancement(dto.getSourceFinancement())
                .typeLieu(parserTypeLieu(dto.getTypeLieu()))
                .auProgramme(estAuProgramme)
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
        }

        List<ConflitAgentDTO> conflits = new ArrayList<>();
        if (!agentIds.isEmpty() && !estAuProgramme && statutInitial != Activite.StatutActivite.BROUILLON) {
            conflits = detecterConflits(activite, agentIds, agentIdsForces);
        }
        if (!conflits.isEmpty()) {
            return ReponseCreationActiviteDTO.builder()
                    .succes(false).conflits(conflits)
                    .message("Conflits détectés. Veuillez confirmer ou retirer les agents concernés.")
                    .build();
        }

        activite = activiteRepository.save(activite);

        if (!agentIds.isEmpty()) {
            creerAffectations(activite, agentIds, agentZones);

            // ✅ NOUVELLE RÈGLE : retirer les agents des activités au programme en chevauchement
            if (!estAuProgramme && activite.getDateDebut() != null && activite.getDateFin() != null) {
                retirerAgentsDesActivitesProgramme(agentIds, activite.getDateDebut(), activite.getDateFin());
            }
        }

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(activite.getId());

        return ReponseCreationActiviteDTO.builder()
                .succes(true)
                .activite(convertToDTOWithAffectations(activite, affectations))
                .message("Activité créée avec succès")
                .build();
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validerActivite(ActiviteDTO dto, Activite.StatutActivite statut) {
        boolean estAuProgramme = dto.getAuProgramme() != null && dto.getAuProgramme();

        if (dto.getTitre() == null || dto.getTitre().isBlank()) {
            throw new BusinessException("Le titre est obligatoire");
        }

        if (statut == Activite.StatutActivite.BROUILLON) {
            return;
        }

        if (dto.getDateDebut() == null) {
            throw new BusinessException("La date de début est obligatoire");
        }
        if (dto.getDateFin() == null) {
            throw new BusinessException("La date de fin est obligatoire");
        }

        if (dto.getAgentIds() == null || dto.getAgentIds().isEmpty()) {
            throw new BusinessException("Au moins un participant est obligatoire");
        }

        if (estAuProgramme) {
            return;
        }

        if (dto.getSourceFinancement() == null || dto.getSourceFinancement().isBlank()) {
            throw new BusinessException("La source de financement est obligatoire pour une activité planifiée");
        }
        if (dto.getLieu() == null || dto.getLieu().isBlank()) {
            throw new BusinessException("Le lieu est obligatoire pour une activité planifiée");
        }
    }

    // ============================================================
    // CONFLITS
    // ============================================================

    private List<ConflitAgentDTO> detecterConflits(Activite activite,
                                                   List<UUID> agentIds,
                                                   List<UUID> agentIdsForces) {
        List<ConflitAgentDTO> conflits = new ArrayList<>();
        if (activite.getDateDebut() == null || activite.getDateFin() == null) return conflits;

        for (UUID agentId : agentIds) {
            if (agentIdsForces.contains(agentId)) continue;

            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

            List<Indisponibilite> indispos = indisponibiliteRepository
                    .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                            agentId, activite.getDateFin(), activite.getDateDebut());

            for (Indisponibilite ind : indispos) {
                conflits.add(ConflitAgentDTO.builder()
                        .agentId(agent.getId()).agentNom(agent.getNom())
                        .agentPrenom(agent.getPrenom()).agentPoste(agent.getPoste())
                        .typeConflit("INDISPONIBILITE").motif(ind.getMotif())
                        .dateDebut(ind.getDateDebut()).dateFin(ind.getDateFin())
                        .build());
            }

            List<Affectation> affectations = affectationRepository
                    .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(
                            agentId, activite.getDateFin(), activite.getDateDebut());

            for (Affectation aff : affectations) {
                if (activite.getId() != null && aff.getActivite().getId().equals(activite.getId())) {
                    continue;
                }

                Activite autreActivite = aff.getActivite();

                // ✅ RÈGLE R1 : ignorer les activités au programme
                if (Boolean.TRUE.equals(autreActivite.getAuProgramme())) {
                    continue;
                }

                Activite.StatutActivite statutAutre = autreActivite.getStatut();

                if (statutAutre == Activite.StatutActivite.BROUILLON
                        || statutAutre == Activite.StatutActivite.ANNULEE
                        || statutAutre == Activite.StatutActivite.TERMINEE
                        || statutAutre == Activite.StatutActivite.REPORTEE) {
                    continue;
                }

                conflits.add(ConflitAgentDTO.builder()
                        .agentId(agent.getId()).agentNom(agent.getNom())
                        .agentPrenom(agent.getPrenom()).agentPoste(agent.getPoste())
                        .typeConflit("AFFECTATION_SIMULTANEE")
                        .activiteConflit(autreActivite.getTitre())
                        .dateDebut(autreActivite.getDateDebut())
                        .dateFin(autreActivite.getDateFin())
                        .build());
            }
        }
        return conflits;
    }

    /**
     * ✅ NOUVELLE MÉTHODE
     *
     * Pour chaque agent de `agentIds`, retire son affectation des activités
     * AU PROGRAMME qui chevauchent la période [debut, fin].
     *
     * Après retrait :
     *   - Si l'activité au programme n'a plus AUCUN agent → statut = TERMINEE.
     *   - Sinon → statut inchangé.
     */
    private void retirerAgentsDesActivitesProgramme(List<UUID> agentIds,
                                                    LocalDate debut,
                                                    LocalDate fin) {
        if (agentIds.isEmpty() || debut == null || fin == null) return;

        List<Activite> activitesProgrammeEnConflit =
                activiteRepository.findActivitesProgrammeChevauchantesPourAgents(debut, fin, agentIds);

        for (Activite prog : activitesProgrammeEnConflit) {
            // Retirer l'affectation des agents concernés
            affectationRepository.deleteByActiviteIdAndAgentIdIn(prog.getId(), agentIds);
            affectationRepository.flush();

            // Compter les agents restants
            long restants = affectationRepository.countByActiviteId(prog.getId());

            if (restants == 0) {
                // ✅ R3 : plus aucun agent → TERMINEE
                prog.setStatut(Activite.StatutActivite.TERMINEE);
                activiteRepository.save(prog);
                log.info("Activité au programme '{}' passée en TERMINEE (plus aucun agent).", prog.getTitre());
            } else {
                // ✅ R4 : il reste des agents → statut inchangé
                log.info("Activité au programme '{}' : {} agent(s) retiré(s), {} restant(s). Statut inchangé.",
                        prog.getTitre(), agentIds.size(), restants);
            }
        }
    }

    /**
     * ✅ NOUVELLE MÉTHODE
     *
     * Si on ajoute des agents à une activité AU PROGRAMME qui est TERMINEE,
     * on la repasse à PLANIFIEE (statut actif).
     */
    private void reactiverActiviteProgrammeSiBesoin(Activite activite) {
        if (!Boolean.TRUE.equals(activite.getAuProgramme())) return;
        if (activite.getStatut() != Activite.StatutActivite.TERMINEE) return;

        long nbAgents = affectationRepository.countByActiviteId(activite.getId());
        if (nbAgents > 0) {
            activite.setStatut(Activite.StatutActivite.PLANIFIEE);
            activiteRepository.save(activite);
            log.info("Activité au programme '{}' réactivée en PLANIFIEE ({} agent(s) ajouté(s)).",
                    activite.getTitre(), nbAgents);
        }
    }

    public List<AgentDTO> trouverRemplacantsPossibles(LocalDate debut, LocalDate fin) {
        List<Agent> tousAgents = agentRepository.findByActifTrue();
        List<AgentDTO> remplacants = new ArrayList<>();
        for (Agent agent : tousAgents) {
            if (conflictService.estDisponible(agent.getId(), debut, fin, null)) {
                remplacants.add(convertAgentToDTO(agent));
            }
        }
        return remplacants;
    }

    private AgentDTO convertAgentToDTO(Agent agent) {
        return AgentDTO.builder().id(agent.getId()).nom(agent.getNom())
                .prenom(agent.getPrenom()).poste(agent.getPoste())
                .actif(agent.getActif()).build();
    }

    private void creerAffectations(Activite activite, List<UUID> agentIds, Map<UUID, String> agentZones) {
        for (UUID agentId : agentIds) {
            Agent agent = agentRepository.findById(agentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

            if (!Boolean.TRUE.equals(agent.getActif())) {
                log.warn("Agent inactif ignoré lors de la création d'affectation: {} {} (id={})",
                        agent.getPrenom(), agent.getNom(), agentId);
                continue;
            }

            String zone = agentZones != null ? agentZones.get(agentId) : null;
            Affectation affectation = Affectation.builder()
                    .agent(agent).activite(activite).zoneAffectation(zone).build();
            affectationRepository.save(affectation);
        }
    }

    // ============================================================
    // MISE À JOUR
    // ============================================================

    @Transactional
    public ReponseCreationActiviteDTO updateActivite(UUID id, ActiviteDTO dto) {
        Activite activite = activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));

        Activite.StatutActivite ancienStatut = activite.getStatut();
        Activite.StatutActivite nouveauStatut = ancienStatut;
        boolean estAuProgramme = dto.getAuProgramme() != null && dto.getAuProgramme();

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

        activite.setDateDebut(dto.getDateDebut());
        activite.setDateFin(dto.getDateFin());

        if (dto.getDateDebut() != null && dto.getDateFin() != null) {
            long jours = java.time.temporal.ChronoUnit.DAYS
                    .between(dto.getDateDebut(), dto.getDateFin()) + 1;

            String typeLieuStr = dto.getTypeLieu();
            boolean isNonResident = "NON_RESIDENT".equalsIgnoreCase(typeLieuStr);

            if (isNonResident && !estAuProgramme) {
                jours = jours + 1;
            }

            activite.setNombreJours((int) Math.max(0, jours));
        } else {
            activite.setNombreJours(null);
        }

        activite.setLieu(dto.getLieu());
        activite.setSourceFinancement(dto.getSourceFinancement());
        activite.setTypeLieu(parserTypeLieu(dto.getTypeLieu()));
        activite.setAuProgramme(estAuProgramme);
        activite.setCommentaires(dto.getCommentaires());
        activite.setStatut(nouveauStatut);

        List<UUID> agentIds = dto.getAgentIds() != null ? dto.getAgentIds() : List.of();
        List<UUID> agentIdsForces = dto.getAgentIdsForces() != null ? dto.getAgentIdsForces() : List.of();
        Map<UUID, String> agentZones = dto.getAgentZones() != null ? dto.getAgentZones() : Map.of();

        if (nouveauStatut == Activite.StatutActivite.BROUILLON) {
            affectationRepository.deleteByActiviteId(id);
            affectationRepository.flush();
        }

        List<ConflitAgentDTO> conflits = new ArrayList<>();
        if (!agentIds.isEmpty() && !estAuProgramme && nouveauStatut != Activite.StatutActivite.BROUILLON) {
            conflits = detecterConflits(activite, agentIds, agentIdsForces);
        }
        if (!conflits.isEmpty()) {
            return ReponseCreationActiviteDTO.builder()
                    .succes(false).conflits(conflits)
                    .message("Conflits détectés. Veuillez confirmer ou retirer les agents concernés.")
                    .build();
        }

        activite = activiteRepository.save(activite);

        if (nouveauStatut != Activite.StatutActivite.BROUILLON) {
            affectationRepository.deleteByActiviteId(id);
            affectationRepository.flush();
            if (!agentIds.isEmpty()) {
                creerAffectations(activite, agentIds, agentZones);

                // ✅ R5 : si au programme et TERMINEE, réactiver en PLANIFIEE
                if (estAuProgramme) {
                    reactiverActiviteProgrammeSiBesoin(activite);
                }

                // ✅ R2-R3 : retirer les agents des autres activités au programme en conflit
                if (!estAuProgramme && activite.getDateDebut() != null && activite.getDateFin() != null) {
                    retirerAgentsDesActivitesProgramme(agentIds, activite.getDateDebut(), activite.getDateFin());
                }
            }
        }

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(id);
        List<Agent> agents = getAgentsDeLActivite(id);

        if (ancienStatut == Activite.StatutActivite.BROUILLON
                && nouveauStatut == Activite.StatutActivite.PLANIFIEE
                && !estAuProgramme
                && activite.getTdrPath() != null
                && Boolean.TRUE.equals(activite.getTdrConforme())
                && !Boolean.TRUE.equals(activite.getTdrEmailEnvoye())) {
            try {
                emailService.envoyerTdrAuxAgents(activite, agents);
                activite.setTdrEmailEnvoye(true);
                activiteRepository.save(activite);
            } catch (Exception e) {
                log.error("Échec envoi TDR (update) pour '{}': {}", activite.getTitre(), e.getMessage());
            }
        }

        if (ancienStatut == Activite.StatutActivite.PLANIFIEE
                && nouveauStatut == Activite.StatutActivite.EN_COURS
                && !estAuProgramme
                && activite.getOrdreMissionPath() != null
                && Boolean.TRUE.equals(activite.getOrdreMissionConforme())
                && !Boolean.TRUE.equals(activite.getOrdreMissionEmailEnvoye())) {
            try {
                emailService.envoyerOrdreMissionAuxAgents(activite, agents);
                activite.setOrdreMissionEmailEnvoye(true);
                activiteRepository.save(activite);
            } catch (Exception e) {
                log.error("Échec envoi OM (update) pour '{}': {}", activite.getTitre(), e.getMessage());
            }
        }

        return ReponseCreationActiviteDTO.builder()
                .succes(true)
                .activite(convertToDTOWithAffectations(activite, affectations))
                .message("Activité modifiée avec succès")
                .build();
    }

    // ============================================================
    // UPLOAD FICHIERS
    // ============================================================

    @Transactional
    public ActiviteDTO uploadTdr(UUID id, MultipartFile file, boolean conforme) {
        Activite activite = getActiviteOrThrow(id);
        fileStorageService.validerTypeFichier(file);

        if (activite.getTdrPath() != null) {
            fileStorageService.delete(activite.getTdrPath());
        }

        String subDir = "activites/" + id;
        String path = fileStorageService.store(file, subDir, "TDR");

        activite.setTdrFilename(file.getOriginalFilename());
        activite.setTdrPath(path);
        activite.setTdrUploadedAt(LocalDateTime.now());
        activite.setTdrConforme(conforme);
        activite.setTdrEmailEnvoye(false);

        activite = activiteRepository.save(activite);

        if (activite.getStatut() == Activite.StatutActivite.PLANIFIEE
                && !Boolean.TRUE.equals(activite.getAuProgramme())
                && conforme) {
            try {
                List<Agent> agents = getAgentsDeLActivite(id);
                if (!agents.isEmpty()) {
                    emailService.envoyerTdrAuxAgents(activite, agents);
                    activite.setTdrEmailEnvoye(true);
                    activiteRepository.save(activite);
                }
            } catch (Exception e) {
                log.error("Échec envoi TDR après upload pour '{}': {}", activite.getTitre(), e.getMessage());
            }
        }

        return convertToDTOWithAffectations(activite,
                affectationRepository.findByActiviteIdWithAgent(id));
    }

    @Transactional
    public ActiviteDTO uploadOrdreMission(UUID id, MultipartFile file, boolean conforme) {
        Activite activite = getActiviteOrThrow(id);
        fileStorageService.validerTypeFichier(file);

        if (activite.getOrdreMissionPath() != null) {
            fileStorageService.delete(activite.getOrdreMissionPath());
        }

        String subDir = "activites/" + id;
        String path = fileStorageService.store(file, subDir, "ORDRE_MISSION");

        activite.setOrdreMissionFilename(file.getOriginalFilename());
        activite.setOrdreMissionPath(path);
        activite.setOrdreMissionUploadedAt(LocalDateTime.now());
        activite.setOrdreMissionConforme(conforme);
        activite.setOrdreMissionEmailEnvoye(false);

        activite = activiteRepository.save(activite);

        return convertToDTOWithAffectations(activite,
                affectationRepository.findByActiviteIdWithAgent(id));
    }

    @Transactional
    public ActiviteDTO uploadLettreInvitation(UUID id, MultipartFile file, boolean conforme) {
        Activite activite = getActiviteOrThrow(id);
        fileStorageService.validerTypeFichier(file);

        if (activite.getLettreInvitationPath() != null) {
            fileStorageService.delete(activite.getLettreInvitationPath());
        }

        String subDir = "activites/" + id;
        String path = fileStorageService.store(file, subDir, "LETTRE");

        activite.setLettreInvitationFilename(file.getOriginalFilename());
        activite.setLettreInvitationPath(path);
        activite.setLettreInvitationUploadedAt(LocalDateTime.now());
        activite.setLettreInvitationConforme(conforme);

        activite = activiteRepository.save(activite);

        if (activite.getStatut() == Activite.StatutActivite.EN_COURS
                && !Boolean.TRUE.equals(activite.getAuProgramme())
                && activite.getOrdreMissionPath() != null
                && Boolean.TRUE.equals(activite.getOrdreMissionConforme())
                && !Boolean.TRUE.equals(activite.getOrdreMissionEmailEnvoye())) {
            try {
                List<Agent> agents = getAgentsDeLActivite(id);
                if (!agents.isEmpty()) {
                    emailService.envoyerOrdreMissionAuxAgents(activite, agents);
                    activite.setOrdreMissionEmailEnvoye(true);
                    activiteRepository.save(activite);
                }
            } catch (Exception e) {
                log.error("Échec envoi OM après upload lettre pour '{}': {}",
                        activite.getTitre(), e.getMessage());
            }
        }

        return convertToDTOWithAffectations(activite,
                affectationRepository.findByActiviteIdWithAgent(id));
    }

    @Transactional
    public void supprimerTdr(UUID id) {
        Activite a = getActiviteOrThrow(id);
        if (a.getTdrPath() != null) fileStorageService.delete(a.getTdrPath());
        a.setTdrFilename(null);
        a.setTdrPath(null);
        a.setTdrUploadedAt(null);
        a.setTdrConforme(false);
        a.setTdrEmailEnvoye(false);
        activiteRepository.save(a);
    }

    @Transactional
    public void supprimerOrdreMission(UUID id) {
        Activite a = getActiviteOrThrow(id);
        if (a.getOrdreMissionPath() != null) fileStorageService.delete(a.getOrdreMissionPath());
        a.setOrdreMissionFilename(null);
        a.setOrdreMissionPath(null);
        a.setOrdreMissionUploadedAt(null);
        a.setOrdreMissionConforme(false);
        a.setOrdreMissionEmailEnvoye(false);
        activiteRepository.save(a);
    }

    @Transactional
    public void supprimerLettreInvitation(UUID id) {
        Activite a = getActiviteOrThrow(id);
        if (a.getLettreInvitationPath() != null) fileStorageService.delete(a.getLettreInvitationPath());
        a.setLettreInvitationFilename(null);
        a.setLettreInvitationPath(null);
        a.setLettreInvitationUploadedAt(null);
        a.setLettreInvitationConforme(false);
        activiteRepository.save(a);
    }

    public byte[] telechargerFichier(UUID id, String type) {
        Activite a = getActiviteOrThrow(id);
        String path = switch (type) {
            case "tdr" -> a.getTdrPath();
            case "ordre_mission" -> a.getOrdreMissionPath();
            case "lettre" -> a.getLettreInvitationPath();
            default -> throw new BusinessException("Type de fichier inconnu: " + type);
        };
        if (path == null) throw new BusinessException("Aucun fichier de ce type attaché à l'activité");
        return fileStorageService.load(path);
    }

    public String getNomFichier(UUID id, String type) {
        Activite a = getActiviteOrThrow(id);
        return switch (type) {
            case "tdr" -> a.getTdrFilename();
            case "ordre_mission" -> a.getOrdreMissionFilename();
            case "lettre" -> a.getLettreInvitationFilename();
            default -> type;
        };
    }

    // ============================================================
    // RENVOI D'EMAILS MANUEL
    // ============================================================

    public void renvoyerTdr(UUID id) {
        Activite activite = getActiviteOrThrow(id);

        if (Boolean.TRUE.equals(activite.getAuProgramme())) {
            throw new BusinessException("Impossible de renvoyer un TDR pour une activité au programme");
        }

        Activite.StatutActivite statut = activite.getStatut();
        if (statut == Activite.StatutActivite.TERMINEE
                || statut == Activite.StatutActivite.ANNULEE
                || statut == Activite.StatutActivite.REPORTEE
                || statut == Activite.StatutActivite.BROUILLON) {
            throw new BusinessException(
                    "Impossible de renvoyer le TDR pour une activité au statut " + statut);
        }

        if (activite.getTdrPath() == null) {
            throw new BusinessException("Aucun TDR attaché à cette activité");
        }

        List<Agent> agents = getAgentsDeLActivite(id);
        emailService.envoyerTdrAuxAgents(activite, agents);
        activite.setTdrEmailEnvoye(true);
        activiteRepository.save(activite);
    }

    public void renvoyerOrdreMission(UUID id) {
        Activite activite = getActiviteOrThrow(id);

        if (Boolean.TRUE.equals(activite.getAuProgramme())) {
            throw new BusinessException("Impossible de renvoyer un ordre de mission pour une activité au programme");
        }

        if (activite.getStatut() != Activite.StatutActivite.EN_COURS) {
            throw new BusinessException(
                    "L'ordre de mission ne peut être renvoyé que pour une activité En cours");
        }

        if (activite.getOrdreMissionPath() == null) {
            throw new BusinessException("Aucun ordre de mission attaché à cette activité");
        }

        List<Agent> agents = getAgentsDeLActivite(id);
        emailService.envoyerOrdreMissionAuxAgents(activite, agents);
        activite.setOrdreMissionEmailEnvoye(true);
        activiteRepository.save(activite);
    }

    // ============================================================
    // CHANGEMENT DE STATUT
    // ============================================================

    @Transactional
    public ActiviteDTO changerStatut(UUID id, String statut) {
        Activite activite = getActiviteOrThrow(id);
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
                    "Une activité en Brouillon doit d'abord être Planifiée avant de changer d'état.");
        }

        if (ancienStatut == Activite.StatutActivite.PLANIFIEE
                && newStatut == Activite.StatutActivite.EN_COURS) {
            validerPassageEnCours(activite);
        }

        if (newStatut == Activite.StatutActivite.BROUILLON
                && ancienStatut != Activite.StatutActivite.BROUILLON) {
            affectationRepository.deleteByActiviteId(id);
            affectationRepository.flush();
        }

        activite.setStatut(newStatut);
        activite = activiteRepository.save(activite);

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(id);
        List<Agent> agents = getAgentsDeLActivite(id);

        if (ancienStatut == Activite.StatutActivite.BROUILLON
                && newStatut == Activite.StatutActivite.PLANIFIEE
                && !Boolean.TRUE.equals(activite.getAuProgramme())
                && activite.getTdrPath() != null
                && Boolean.TRUE.equals(activite.getTdrConforme())
                && !Boolean.TRUE.equals(activite.getTdrEmailEnvoye())) {
            try {
                emailService.envoyerTdrAuxAgents(activite, agents);
                activite.setTdrEmailEnvoye(true);
                activiteRepository.save(activite);
            } catch (Exception e) {
                log.error("Échec envoi TDR pour '{}': {}", activite.getTitre(), e.getMessage());
            }
        }

        if (ancienStatut == Activite.StatutActivite.PLANIFIEE
                && newStatut == Activite.StatutActivite.EN_COURS
                && !Boolean.TRUE.equals(activite.getAuProgramme())
                && activite.getOrdreMissionPath() != null
                && Boolean.TRUE.equals(activite.getOrdreMissionConforme())
                && !Boolean.TRUE.equals(activite.getOrdreMissionEmailEnvoye())) {
            try {
                emailService.envoyerOrdreMissionAuxAgents(activite, agents);
                activite.setOrdreMissionEmailEnvoye(true);
                activiteRepository.save(activite);
            } catch (Exception e) {
                log.error("Échec envoi OM pour '{}': {}", activite.getTitre(), e.getMessage());
            }
        }

        if (newStatut == Activite.StatutActivite.BROUILLON
                && ancienStatut != Activite.StatutActivite.BROUILLON) {
            try {
                emailService.notifierChangementStatut(activite, agents, "BROUILLON");
            } catch (Exception e) {
                log.error("Échec notif BROUILLON", e);
            }
        }

        if (newStatut == Activite.StatutActivite.ANNULEE) {
            try {
                emailService.notifierChangementStatut(activite, agents, "ANNULEE");
            } catch (Exception e) {
                log.error("Échec notif ANNULEE", e);
            }
        }

        if (newStatut == Activite.StatutActivite.REPORTEE) {
            try {
                emailService.notifierChangementStatut(activite, agents, "REPORTEE");
            } catch (Exception e) {
                log.error("Échec notif REPORTEE", e);
            }
        }

        return convertToDTOWithAffectations(activite, affectations);
    }

    private void validerPassageEnPlanifiee(Activite activite) {
        List<String> erreurs = new ArrayList<>();

        if (activite.getDateDebut() == null) erreurs.add("la date de début");
        if (activite.getDateFin() == null) erreurs.add("la date de fin");

        List<Affectation> affectations = affectationRepository.findByActiviteIdWithAgent(activite.getId());
        if (affectations.isEmpty()) erreurs.add("au moins un participant");

        boolean estAuProgramme = Boolean.TRUE.equals(activite.getAuProgramme());

        if (!estAuProgramme) {
            if (activite.getLieu() == null || activite.getLieu().isBlank()) erreurs.add("le lieu");
            if (activite.getSourceFinancement() == null || activite.getSourceFinancement().isBlank())
                erreurs.add("la source de financement");

            if (activite.getTdrPath() == null) {
                erreurs.add("le TDR (Termes de Référence)");
            } else if (!Boolean.TRUE.equals(activite.getTdrConforme())) {
                erreurs.add("la confirmation 'conforme à l'original' du TDR");
            }
        }

        if (!erreurs.isEmpty()) {
            throw new BusinessException(
                    "Impossible de planifier : veuillez renseigner " + String.join(", ", erreurs) + ".");
        }
    }

    private void validerPassageEnCours(Activite activite) {
        if (Boolean.TRUE.equals(activite.getAuProgramme())) {
            return;
        }

        List<String> erreurs = new ArrayList<>();

        if (activite.getOrdreMissionPath() == null) {
            erreurs.add("l'Ordre de Mission");
        } else if (!Boolean.TRUE.equals(activite.getOrdreMissionConforme())) {
            erreurs.add("la confirmation 'conforme à l'original' de l'Ordre de Mission");
        }

        if (!erreurs.isEmpty()) {
            throw new BusinessException(
                    "Impossible de démarrer : veuillez renseigner " + String.join(", ", erreurs) + ".");
        }
    }

    private Activite getActiviteOrThrow(UUID id) {
        return activiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée: " + id));
    }

    private List<Agent> getAgentsDeLActivite(UUID activiteId) {
        return affectationRepository.findActivesByActiviteId(activiteId).stream()
                .map(Affectation::getAgent)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public DisponibiliteDTO verifierDisponibilite(UUID agentId, LocalDate debut, LocalDate fin) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));
        boolean disponible = conflictService.estDisponible(agentId, debut, fin, null);

        List<Indisponibilite> indisponibilites = indisponibiliteRepository
                .findByAgentIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(agentId, fin, debut);
        List<Affectation> affectations = affectationRepository
                .findByAgentIdAndActiviteDateDebutLessThanEqualAndActiviteDateFinGreaterThanEqual(agentId, fin, debut);

        return DisponibiliteDTO.builder()
                .agentId(agentId).agentNom(agent.getNomComplet()).disponible(disponible)
                .indisponibilites(indisponibilites.stream().map(this::convertIndisponibiliteToDTO).toList())
                .affectations(affectations.stream().map(this::convertAffectationToDTO).toList())
                .build();
    }

    public Map<String, Integer> calculerJoursMissionMensuel(YearMonth mois) {
        LocalDate debut = mois.atDay(1), fin = mois.atEndOfMonth();
        List<Agent> agents = agentRepository.findByActifTrue();
        Map<String, Integer> result = new HashMap<>();
        for (Agent agent : agents) {
            result.put(agent.getNomComplet(), calculerJoursMission(agent.getId(), debut, fin));
        }
        return result;
    }

    public Map<String, Integer> calculerJoursMissionAnnuel(int annee) {
        LocalDate debut = LocalDate.of(annee, 1, 1), fin = LocalDate.of(annee, 12, 31);
        List<Agent> agents = agentRepository.findByActifTrue();
        Map<String, Integer> result = new HashMap<>();
        for (Agent agent : agents) {
            result.put(agent.getNomComplet(), calculerJoursMission(agent.getId(), debut, fin));
        }
        return result;
    }

    public int calculerJoursMission(UUID agentId, LocalDate debut, LocalDate fin) {
        return affectationRepository.findByAgentIdAndActiviteDateDebutBetween(agentId, debut, fin)
                .stream()
                .mapToInt(a -> a.getActivite().getNombreJours() != null ? a.getActivite().getNombreJours() : 0)
                .sum();
    }

    public PlanningMensuelDTO getPlanningMensuel(YearMonth mois) {
        LocalDate debut = mois.atDay(1), fin = mois.atEndOfMonth();
        List<Activite> activites = activiteRepository.findByPeriode(debut, fin);
        List<UUID> activiteIds = activites.stream().map(Activite::getId).toList();

        Map<UUID, List<Affectation>> affectationsParActivite = new HashMap<>();
        if (!activiteIds.isEmpty()) {
            for (Affectation aff : affectationRepository.findByActiviteIdInWithAgent(activiteIds)) {
                affectationsParActivite
                        .computeIfAbsent(aff.getActivite().getId(), k -> new ArrayList<>())
                        .add(aff);
            }
        }

        List<ActiviteDTO> activiteDTOs = activites.stream()
                .map(a -> convertToDTOWithAffectations(a, affectationsParActivite.getOrDefault(a.getId(), List.of())))
                .toList();

        Map<UUID, List<ActiviteDTO>> planningParAgent = new HashMap<>();
        for (ActiviteDTO dto : activiteDTOs) {
            if (dto.getAgentIds() != null) {
                for (UUID agentId : dto.getAgentIds()) {
                    planningParAgent.computeIfAbsent(agentId, k -> new ArrayList<>()).add(dto);
                }
            }
        }

        return PlanningMensuelDTO.builder()
                .mois(mois.toString())
                .activites(activiteDTOs)
                .planningParAgent(planningParAgent)
                .build();
    }

    private ActiviteDTO convertToDTOWithAffectations(Activite activite, List<Affectation> affectations) {
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
                .typeLieu(activite.getTypeLieu() != null
                        ? activite.getTypeLieu().name() : null)
                .auProgramme(activite.getAuProgramme())
                .statut(activite.getStatut().name())
                .agentIds(agentIds)
                .agentNoms(agentNoms)
                .agentZonesList(agentZonesList)
                .tdrFilename(activite.getTdrFilename())
                .tdrUploadedAt(activite.getTdrUploadedAt())
                .tdrConforme(activite.getTdrConforme())
                .ordreMissionFilename(activite.getOrdreMissionFilename())
                .ordreMissionUploadedAt(activite.getOrdreMissionUploadedAt())
                .ordreMissionConforme(activite.getOrdreMissionConforme())
                .lettreInvitationFilename(activite.getLettreInvitationFilename())
                .lettreInvitationUploadedAt(activite.getLettreInvitationUploadedAt())
                .lettreInvitationConforme(activite.getLettreInvitationConforme())
                .tdrEmailEnvoye(activite.getTdrEmailEnvoye())
                .ordreMissionEmailEnvoye(activite.getOrdreMissionEmailEnvoye())
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
    public void supprimerActivite(UUID id) {
        if (!activiteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Activité non trouvée: " + id);
        }
        affectationRepository.deleteByActiviteId(id);
        activiteRepository.deleteById(id);
    }

    public ActiviteDTO getActiviteById(UUID id) {
        Activite activite = getActiviteOrThrow(id);
        return convertToDTOWithAffectations(activite, affectationRepository.findByActiviteIdWithAgent(id));
    }

    private Activite.TypeLieu parserTypeLieu(String typeLieuStr) {
        if (typeLieuStr == null || typeLieuStr.isBlank()) {
            return Activite.TypeLieu.NON_RESIDENT;
        }
        try {
            return Activite.TypeLieu.valueOf(typeLieuStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("TypeLieu invalide reçu: '{}' → NON_RESIDENT par défaut", typeLieuStr);
            return Activite.TypeLieu.NON_RESIDENT;
        }
    }
}