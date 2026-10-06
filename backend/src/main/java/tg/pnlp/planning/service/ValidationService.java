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
import tg.pnlp.planning.security.ProfilCodeNormalizer;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final ActiviteRepository activiteRepository;
    private final AffectationRepository affectationRepository;
    private final AgentRepository agentRepository;
    private final NotificationService notificationService;

    // ============================================================
    // NIVEAU DU VALIDATEUR CONNECTÉ
    // ============================================================

    /**
     * Retourne le niveau de validation (1, 2, 3) de l'utilisateur, ou null si pas validateur.
     * Si SUPER_ADMIN → retourne un niveau "override" = -1 (peut tout faire).
     */
    public Integer getNiveauValidation(Set<String> profils) {
        if (profils == null || profils.isEmpty()) return null;
        Set<String> normalises = new HashSet<>();
        for (String p : profils) normalises.add(ProfilCodeNormalizer.normaliser(p));

        if (normalises.contains(ProfilCodeNormalizer.SUPER_ADMIN)) return -1; // override
        if (normalises.contains(ProfilCodeNormalizer.VALIDATEUR_NIVEAU_3)) return 3;
        if (normalises.contains(ProfilCodeNormalizer.VALIDATEUR_NIVEAU_2)) return 2;
        if (normalises.contains(ProfilCodeNormalizer.VALIDATEUR_NIVEAU_1)) return 1;
        return null;
    }

    public boolean peutValiderNiveau(Set<String> profils, int niveauCible) {
        Integer niveau = getNiveauValidation(profils);
        if (niveau == null) return false;
        if (niveau == -1) return true; // SUPER_ADMIN
        return niveau == niveauCible;
    }

    // ============================================================
    // FILE D'ATTENTE
    // ============================================================

    public List<ActiviteDTO> listerActivitesAValider(Integer niveau) {
        if (niveau == null) return List.of();
        List<Activite> activites = (niveau == -1)
                ? activiteRepository.findAllEnAttenteValidation()
                : (niveau >= 1 && niveau <= 3 ? activiteRepository.findByStatutEnAttenteAndNiveau(niveau) : List.of());
        return activites.stream().map(this::convertToDTO).toList();
    }

    public long compterActivitesAValider(Integer niveau) {
        if (niveau == null) return 0;
        if (niveau == -1) {
            return activiteRepository.countByStatut(Activite.StatutActivite.EN_ATTENTE_VALIDATION);
        }
        if (niveau < 1 || niveau > 3) return 0;
        return activiteRepository.countByStatutAndNiveauValidationActuel(
                Activite.StatutActivite.EN_ATTENTE_VALIDATION, niveau);
    }

    // ============================================================
    // TRAITEMENT DES CONFLITS (RETIRER / REMPLACER / FORCER)
    // ============================================================

    @Transactional
    public ActiviteDTO traiterConflits(UUID activiteId,
                                       List<ValidationConflitDTO> actions,
                                       Set<String> profils) {
        Activite activite = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée"));

        verifierDroitValidation(activite, profils);

        if (actions == null || actions.isEmpty()) {
            throw new BusinessException("Aucune action fournie");
        }

        appliquerActionsSoumises(activite, actions);

        return convertToDTO(activite);
    }

    @Transactional
    public ActiviteDTO valider(UUID activiteId,
                               DecisionValidationDTO decision,
                               Set<String> profils) {
        Activite activite = activiteRepository.findById(activiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Activité non trouvée"));

        verifierDroitValidation(activite, profils);

        if (activite.getStatut() != Activite.StatutActivite.EN_ATTENTE_VALIDATION) {
            throw new BusinessException("L'activité n'est pas en attente de validation");
        }

        // ✅ CORRECTIF CRITIQUE : persiste les choix Retirer/Remplacer/Forcer envoyés avec
        // la décision. Sans cette ligne, rien de ce que le validateur a sélectionné dans
        // l'UI n'est jamais enregistré, puisqu'aucun bouton du template n'appelle
        // /conflits séparément avant /valider — le workflow échouait systématiquement.
        if (decision.getActions() != null && !decision.getActions().isEmpty()) {
            appliquerActionsSoumises(activite, decision.getActions());
        }

        String dec = decision.getDecision() == null ? "" : decision.getDecision().toUpperCase();
        int niveauActuel = activite.getNiveauValidationActuel() != null
                ? activite.getNiveauValidationActuel() : 1;

        switch (dec) {
            case "VALIDER" -> {
                verifierTousConflitsTraites(activite);
                appliquerActionsConflits(activite);
                activite.setStatut(Activite.StatutActivite.PLANIFIEE);
                activite.setValideParNiveau(niveauActuel);
                activite.setNiveauValidationActuel(null);
                activiteRepository.save(activite);

                notificationService.notifierValidationFinale(activite);
                log.info("Activité '{}' validée par niveau {}", activite.getTitre(), niveauActuel);
            }
            case "RENVOYER_NIVEAU_SUPERIEUR" -> {
                if (niveauActuel >= 3) {
                    throw new BusinessException(
                            "Le niveau 3 est le dernier niveau. Renvoyez au planificateur ou validez.");
                }
                // ✅ SUPPRIMÉ : verifierTousConflitsTraites(activite);
                // On autorise le renvoi N+1 sans avoir traité les conflits.
                activite.setNiveauValidationActuel(niveauActuel + 1);
                activiteRepository.save(activite);

                notificationService.notifierValidateur(activite, niveauActuel + 1);
                log.info("Activité '{}' renvoyée au niveau {}", activite.getTitre(), niveauActuel + 1);
            }
            case "RENVOYER_PLANIFICATEUR" -> {
                if (decision.getCommentaire() == null || decision.getCommentaire().isBlank()) {
                    throw new BusinessException("Un commentaire est requis pour renvoyer au planificateur");
                }
                activite.setStatut(Activite.StatutActivite.RENVOYE_POUR_CORRECTION);
                activite.setRenvoyeParNiveau(niveauActuel);
                activite.setCommentaires(
                        (activite.getCommentaires() != null ? activite.getCommentaires() + "\n\n" : "")
                                + "[Renvoi N" + niveauActuel + "] " + decision.getCommentaire()
                );
                activiteRepository.save(activite);

                notificationService.notifierPlanificateurRenvoi(activite, decision.getCommentaire());
                log.info("Activité '{}' renvoyée au planificateur par N{}", activite.getTitre(), niveauActuel);
            }
            default -> throw new BusinessException("Décision inconnue: " + decision.getDecision());
        }

        return convertToDTO(activite);
    }

// ============================================================
// ✅ NOUVEAU : logique partagée d'application d'une liste d'actions
// ============================================================

    /**
     * Applique une liste d'actions de résolution de conflit à leurs affectations.
     * Une action "EN_ATTENTE" (agent pas encore traité par le validateur) est
     * ignorée silencieusement : c'est un état normal tant que le validateur n'a
     * pas fini de traiter tous les conflits (notamment avant un renvoi au
     * planificateur, où il n'est pas obligatoire d'avoir tout résolu).
     */
    private void appliquerActionsSoumises(Activite activite, List<ValidationConflitDTO> actions) {
        for (ValidationConflitDTO action : actions) {
            if (action.getAction() == null
                    || action.getAction().equalsIgnoreCase("EN_ATTENTE")) {
                continue; // rien choisi pour cet agent, on ne touche à rien
            }

            Affectation aff = affectationRepository.findById(action.getAffectationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Affectation non trouvée: " + action.getAffectationId()));

            if (!aff.getActivite().getId().equals(activite.getId())) {
                throw new BusinessException("L'affectation n'appartient pas à cette activité");
            }
            if (!Boolean.TRUE.equals(aff.getEnConflit())) {
                throw new BusinessException("Cette affectation n'est pas en conflit");
            }

            switch (action.getAction().toUpperCase()) {
                case "RETIRER" -> {
                    aff.setActionValidation(Affectation.ActionValidation.RETIRER);
                    aff.setForce(false);
                    aff.setAgentRemplacant(null);
                    aff.setMotifConflit(action.getMotif());
                }
                case "FORCER" -> {
                    aff.setActionValidation(Affectation.ActionValidation.FORCER);
                    aff.setForce(true);
                    aff.setAgentRemplacant(null);
                    aff.setMotifConflit(action.getMotif());
                }
                case "REMPLACER" -> {
                    if (action.getAgentRemplacantId() == null) {
                        throw new BusinessException("Un agent remplaçant est requis pour REMPLACER");
                    }
                    Agent remplacant = agentRepository.findById(action.getAgentRemplacantId())
                            .orElseThrow(() -> new ResourceNotFoundException("Agent remplaçant non trouvé"));
                    aff.setActionValidation(Affectation.ActionValidation.REMPLACER);
                    aff.setForce(false);
                    aff.setAgentRemplacant(remplacant);
                    aff.setMotifConflit(action.getMotif());
                }
                default -> throw new BusinessException("Action inconnue: " + action.getAction());
            }
            affectationRepository.save(aff);
        }
        affectationRepository.flush();
    }
    // ============================================================
    // HELPERS PRIVÉS
    // ============================================================

    private void verifierDroitValidation(Activite activite, Set<String> profils) {
        Integer niveauValidateur = getNiveauValidation(profils);
        if (niveauValidateur == null) {
            throw new BusinessException("Vous n'avez pas le profil validateur");
        }
        if (niveauValidateur == -1) return; // SUPER_ADMIN : override

        Integer niveauActuel = activite.getNiveauValidationActuel();
        if (niveauActuel == null || !niveauActuel.equals(niveauValidateur)) {
            throw new BusinessException(
                    "Vous ne pouvez pas valider cette activité (niveau actuel: " + niveauActuel
                            + ", votre niveau: " + niveauValidateur + ")");
        }
    }

    private void verifierTousConflitsTraites(Activite activite) {
        List<Affectation> conflits = affectationRepository
                .findByActiviteIdAndEnConflitTrue(activite.getId());

        if (conflits.isEmpty()) return;

        List<Affectation> nonTraites = conflits.stream()
                .filter(a -> a.getActionValidation() == null
                        || a.getActionValidation() == Affectation.ActionValidation.EN_ATTENTE)
                .toList();

        if (!nonTraites.isEmpty()) {
            throw new BusinessException(
                    "Tous les conflits doivent être traités avant de valider. "
                            + nonTraites.size() + " conflit(s) restant(s).");
        }
    }

    /**
     * Applique réellement les actions décidées :
     *  - RETIRER : supprime l'affectation de l'agent en conflit
     *  - REMPLACER : supprime l'affectation + crée une nouvelle avec le remplaçant
     *  - FORCER : garde l'affectation (marque force=true)
     *
     * Les agents NON en conflit restent intacts.
     */
    private void appliquerActionsConflits(Activite activite) {
        List<Affectation> conflits = affectationRepository
                .findByActiviteIdAndEnConflitTrue(activite.getId());

        for (Affectation aff : conflits) {
            if (aff.getActionValidation() == null) continue;
            switch (aff.getActionValidation()) {
                case RETIRER -> {
                    affectationRepository.delete(aff);
                    log.info("Agent {} retiré de l'activité '{}'",
                            aff.getAgent().getNomComplet(), activite.getTitre());
                }
                case REMPLACER -> {
                    Agent remplacant = aff.getAgentRemplacant();
                    String zone = aff.getZoneAffectation();
                    affectationRepository.delete(aff);

                    if (remplacant != null) {
                        Affectation nouvelle = Affectation.builder()
                                .agent(remplacant)
                                .activite(activite)
                                .zoneAffectation(zone)
                                .enConflit(false)
                                .force(false)
                                .build();
                        affectationRepository.save(nouvelle);
                        log.info("Agent {} remplacé par {} dans '{}'",
                                aff.getAgent().getNomComplet(),
                                remplacant.getNomComplet(),
                                activite.getTitre());
                    }
                }
                case FORCER -> {
                    aff.setForce(true);
                    aff.setEnConflit(false);
                    affectationRepository.save(aff);
                    log.info("Conflit forcé pour {} dans '{}'",
                            aff.getAgent().getNomComplet(), activite.getTitre());
                }
                default -> { /* EN_ATTENTE : ne rien faire */ }
            }
        }
        affectationRepository.flush();
    }

    public ActiviteDTO convertToDTO(Activite activite) {
        List<Affectation> affectations = affectationRepository
                .findByActiviteIdWithAgent(activite.getId());

        List<UUID> agentIds = new ArrayList<>();
        List<String> agentNoms = new ArrayList<>();
        List<String> agentZones = new ArrayList<>();
        List<ConflitAgentDTO> conflits = new ArrayList<>();

        for (Affectation aff : affectations) {
            if (aff.getAgent() != null) {
                agentIds.add(aff.getAgent().getId());
                agentNoms.add(aff.getAgent().getNomComplet());
                agentZones.add(aff.getZoneAffectation());
            }
            if (Boolean.TRUE.equals(aff.getEnConflit())) {
                conflits.add(ConflitAgentDTO.builder()
                        .agentId(aff.getAgent().getId())
                        .agentNom(aff.getAgent().getNom())
                        .agentPrenom(aff.getAgent().getPrenom())
                        .agentPoste(aff.getAgent().getPoste())
                        .affectationId(aff.getId())
                        .actionValidation(aff.getActionValidation() != null
                                ? aff.getActionValidation().name() : "EN_ATTENTE")
                        .agentRemplacantId(aff.getAgentRemplacant() != null
                                ? aff.getAgentRemplacant().getId() : null)
                        .agentRemplacantNom(aff.getAgentRemplacant() != null
                                ? aff.getAgentRemplacant().getNomComplet() : null)
                        .force(aff.getForce())
                        .motifConflit(aff.getMotifConflit())
                        .build());
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
                .typeLieu(activite.getTypeLieu() != null ? activite.getTypeLieu().name() : null)
                .auProgramme(activite.getAuProgramme())
                .statut(activite.getStatut().name())
                .niveauValidationActuel(activite.getNiveauValidationActuel())
                .valideParNiveau(activite.getValideParNiveau())
                .renvoyeParNiveau(activite.getRenvoyeParNiveau())
                .agentIds(agentIds)
                .agentNoms(agentNoms)
                .agentZonesList(agentZones)
                .conflits(conflits)
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
}
