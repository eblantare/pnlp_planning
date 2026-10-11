package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.entity.Activite;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.entity.Utilisateur;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final FileStorageService fileStorageService;
    private final EmailSender emailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ============================================================
    // TDR
    // ============================================================
    public void envoyerTdrAuxAgents(Activite activite, List<Agent> agents) {
        if (agents == null || agents.isEmpty()) {
            log.warn("Aucun agent à qui envoyer le TDR pour '{}'", activite.getTitre());
            return;
        }
        if (activite.getTdrPath() == null) {
            log.warn("Aucun TDR attaché pour '{}'", activite.getTitre());
            return;
        }

        String corps = buildEmailTdrCorps(activite);
        byte[] fichier = fileStorageService.load(activite.getTdrPath());
        String nomPieceJointe = construireNomPieceJointe(
                "Termes de référence", activite.getTitre(), activite.getTdrFilename()
        );

        int envoyes = 0, echecs = 0;

        for (Agent agent : agents) {
            if (agent.getEmail() == null || agent.getEmail().isBlank()) {
                log.warn("Agent sans email: {} {}", agent.getPrenom(), agent.getNom());
                echecs++;
                continue;
            }
            try {
                emailSender.sendEmail(
                        agent.getEmail(),
                        "PNLP - Termes de référence : " + activite.getTitre(),
                        corps, null, nomPieceJointe, fichier
                );
                envoyes++;
                log.info("TDR envoyé à: {}", agent.getEmail());
            } catch (Exception e) {
                log.error("Échec envoi TDR à {}: {}", agent.getEmail(), e.getMessage());
                echecs++;
            }
        }
        log.info("Envoi TDR terminé pour '{}': {} envoyés, {} échecs",
                activite.getTitre(), envoyes, echecs);
    }

    // ============================================================
    // ORDRE DE MISSION + LETTRE D'INVITATION (✅ CORRIGÉ)
    // ============================================================
    public void envoyerOrdreMissionAuxAgents(Activite activite, List<Agent> agents) {
        if (agents == null || agents.isEmpty()) {
            log.warn("Aucun agent pour l'ordre de mission de '{}'", activite.getTitre());
            return;
        }
        if (activite.getOrdreMissionPath() == null) {
            log.warn("Aucun Ordre de Mission attaché pour '{}'", activite.getTitre());
            return;
        }

        String corps = buildEmailOrdreMissionCorps(activite);

        // ✅ Construire la liste des pièces jointes
        List<EmailSender.Attachment> piecesJointes = new ArrayList<>();

        // 1. Ordre de mission (toujours présent)
        byte[] om = fileStorageService.load(activite.getOrdreMissionPath());
        String nomOm = construireNomPieceJointe(
                "Ordre de mission", activite.getTitre(), activite.getOrdreMissionFilename()
        );
        piecesJointes.add(new EmailSender.Attachment(nomOm, om));

        // 2. Lettre d'invitation (si présente) — ✅ AJOUT
        if (activite.getLettreInvitationPath() != null) {
            byte[] lettre = fileStorageService.load(activite.getLettreInvitationPath());
            String nomLettre = construireNomPieceJointe(
                    "Lettre d'invitation", activite.getTitre(),
                    activite.getLettreInvitationFilename()
            );
            piecesJointes.add(new EmailSender.Attachment(nomLettre, lettre));
        }

        int envoyes = 0, echecs = 0;

        for (Agent agent : agents) {
            if (agent.getEmail() == null || agent.getEmail().isBlank()) {
                echecs++;
                continue;
            }
            try {
                emailSender.sendEmail(
                        agent.getEmail(),
                        "PNLP - Ordre de mission : " + activite.getTitre(),
                        corps, null,
                        piecesJointes   // ✅ toutes les pièces jointes
                );
                envoyes++;
                log.info("Ordre de mission envoyé à: {} ({} pièce(s) jointe(s))",
                        agent.getEmail(), piecesJointes.size());
            } catch (Exception e) {
                log.error("Échec envoi OM à {}: {}", agent.getEmail(), e.getMessage());
                echecs++;
            }
        }
        log.info("Envoi OM terminé pour '{}': {} envoyés, {} échecs",
                activite.getTitre(), envoyes, echecs);
    }

    // ============================================================
    // NOTIFICATIONS DE VALIDATION
    // ============================================================
    public void envoyerNotificationValidation(Utilisateur validateur, Activite activite, int niveau) {
        if (validateur.getEmail() == null || validateur.getEmail().isBlank()) return;
        try {
            emailSender.sendSimpleEmail(
                    validateur.getEmail(),
                    "PNLP - Activité en attente de validation N" + niveau + " : " + activite.getTitre(),
                    "Bonjour " + validateur.getUsername() + ",\n\n"
                            + "Une activité nécessite votre validation (niveau " + niveau + ").\n\n"
                            + "Titre : " + activite.getTitre() + "\n"
                            + "Période : " + formatDate(activite.getDateDebut())
                            + " au " + formatDate(activite.getDateFin()) + "\n"
                            + "Lieu : " + (activite.getLieu() != null ? activite.getLieu() : "-") + "\n\n"
                            + "Connectez-vous à l'application pour traiter les conflits et valider.\n\n"
                            + "Cordialement,\nLe PNLP"
            );
            log.info("Notif validation N{} envoyée à {}", niveau, validateur.getEmail());
        } catch (Exception e) {
            log.error("Échec notif validation: {}", e.getMessage());
        }
    }

    public void envoyerNotificationRenvoiPlanificateur(Utilisateur planificateur, Activite activite, String motif) {
        if (planificateur.getEmail() == null || planificateur.getEmail().isBlank()) return;
        try {
            emailSender.sendSimpleEmail(
                    planificateur.getEmail(),
                    "PNLP - Activité renvoyée pour correction : " + activite.getTitre(),
                    "Bonjour " + planificateur.getUsername() + ",\n\n"
                            + "L'activité \"" + activite.getTitre() + "\" vous a été renvoyée pour correction.\n\n"
                            + "Motif : " + motif + "\n\n"
                            + "Merci de corriger et resoumettre.\n\n"
                            + "Cordialement,\nLe PNLP"
            );
            log.info("Notif renvoi envoyée à {}", planificateur.getEmail());
        } catch (Exception e) {
            log.error("Échec notif renvoi: {}", e.getMessage());
        }
    }

    public void envoyerNotificationValidationFinale(Utilisateur createur, Activite activite) {
        if (createur.getEmail() == null || createur.getEmail().isBlank()) return;
        try {
            emailSender.sendSimpleEmail(
                    createur.getEmail(),
                    "PNLP - Activité validée : " + activite.getTitre(),
                    "Bonjour " + createur.getUsername() + ",\n\n"
                            + "Bonne nouvelle : l'activité \"" + activite.getTitre() + "\" a été validée.\n"
                            + "Elle est maintenant planifiée.\n\n"
                            + "Cordialement,\nLe PNLP"
            );
            log.info("Notif validation finale envoyée à {}", createur.getEmail());
        } catch (Exception e) {
            log.error("Échec notif validation finale: {}", e.getMessage());
        }
    }

    public void notifierChangementStatut(Activite activite, List<Agent> agents, String typeChangement) {
        if (agents == null || agents.isEmpty()) return;

        String sujet, corps;

        switch (typeChangement) {
            case "BROUILLON" -> {
                sujet = "PNLP - Activité en cours de modification : " + activite.getTitre();
                corps = "L'activité \"" + activite.getTitre() + "\" est en cours de modification.";
            }
            case "ANNULEE" -> {
                sujet = "PNLP - Activité annulée : " + activite.getTitre();
                corps = "L'activité \"" + activite.getTitre() + "\" a été ANNULÉE.";
            }
            case "REPORTEE" -> {
                sujet = "PNLP - Activité reportée : " + activite.getTitre();
                corps = "L'activité \"" + activite.getTitre() + "\" a été REPORTÉE.";
            }
            default -> { return; }
        }

        for (Agent agent : agents) {
            if (agent.getEmail() == null || agent.getEmail().isBlank()) continue;
            try {
                emailSender.sendSimpleEmail(agent.getEmail(), sujet, corps);
                log.info("Notification '{}' envoyée à: {}", typeChangement, agent.getEmail());
            } catch (Exception e) {
                log.error("Échec notification à {}: {}", agent.getEmail(), e.getMessage());
            }
        }
    }

    // ============================================================
    // RESET PASSWORD
    // ============================================================
    public void envoyerEmailReinitialisation(Utilisateur user, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + token;

            String textBody = "Bonjour " + user.getUsername() + ",\n\n"
                    + "Vous avez demandé la réinitialisation de votre mot de passe.\n\n"
                    + "Cliquez sur le lien suivant pour réinitialiser :\n"
                    + resetLink + "\n\n"
                    + "Ce lien est valide pendant 30 minutes.\n\n"
                    + "Cordialement,\nLe PNLP";

            emailSender.sendEmail(
                    user.getEmail(),
                    "PNLP Planning - Réinitialisation de votre mot de passe",
                    textBody,
                    buildEmailResetHtml(user, resetLink),
                    null, null
            );

            log.info("Email de réinitialisation envoyé à: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Erreur envoi email à {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de réinitialisation", e);
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private String formatDate(java.time.LocalDate d) {
        return d != null ? d.format(DF) : "?";
    }

    private String construireNomPieceJointe(String typeDocument, String titreActivite, String originalFilename) {
        String extension = ".pdf";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String titrePropre = (titreActivite != null && !titreActivite.isBlank())
                ? titreActivite.replaceAll("[\\\\/:*?\"<>|]", "_").trim()
                : "activite";
        if (titrePropre.length() > 60) {
            titrePropre = titrePropre.substring(0, 60);
        }
        return typeDocument + " - " + titrePropre + extension;
    }

    private String buildEmailTdrCorps(Activite activite) {
        String dateDebut = formatDate(activite.getDateDebut());
        String dateFin = formatDate(activite.getDateFin());
        String lieu = activite.getLieu() != null ? activite.getLieu() : "-";
        return "Bonjour Mesdames/Messieurs,\n\n"
                + "Veuillez trouver ci-joint les termes de référence relatifs à \""
                + activite.getTitre() + "\" qui se tiendra du " + dateDebut
                + " au " + dateFin + " à " + lieu + ".\n\n"
                + "NB : la lettre d'information vous parviendra dès sa signature par l'autorité.\n\n"
                + "Merci et bonne réception.\n\nCordialement,\nLe PNLP";
    }

    private String buildEmailOrdreMissionCorps(Activite activite) {
        String dateDebut = formatDate(activite.getDateDebut());
        String dateFin = formatDate(activite.getDateFin());
        String lieu = activite.getLieu() != null ? activite.getLieu() : "-";
        return "Bonjour Mesdames/Messieurs,\n\n"
                + "Veuillez trouver ci-joint votre ordre de mission pour l'activité \""
                + activite.getTitre() + "\" qui se tiendra du " + dateDebut
                + " au " + dateFin + " à " + lieu + ".\n\n"
                + (activite.getLettreInvitationPath() != null
                ? "La lettre d'invitation est également jointe.\n\n" : "")
                + "Merci et bonne réception.\n\nCordialement,\nLe PNLP";
    }

    private String buildEmailResetHtml(Utilisateur user, String resetLink) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head>"
                + "<body style='font-family: Arial, sans-serif; background: #F3F4F6; padding: 20px;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1);'>"
                + "<div style='background: linear-gradient(135deg, #1B5E20 0%, #2E7D32 100%); padding: 30px; text-align: center;'>"
                + "<h1 style='color: white; margin: 0; font-size: 24px;'>PNLP Planning</h1>"
                + "<p style='color: rgba(255,255,255,0.9); margin: 8px 0 0 0; font-size: 14px;'>Programme National de Lutte contre le Paludisme</p>"
                + "</div>"
                + "<div style='padding: 30px;'>"
                + "<h2 style='color: #1B5E20; margin-top: 0;'>Réinitialisation de mot de passe</h2>"
                + "<p style='color: #374151; line-height: 1.6;'>Bonjour <strong>" + user.getUsername() + "</strong>,</p>"
                + "<p style='color: #374151; line-height: 1.6;'>Vous avez demandé la réinitialisation de votre mot de passe.</p>"
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + resetLink + "' style='display: inline-block; background: linear-gradient(135deg, #1B5E20 0%, #2E7D32 100%); color: white; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: 600;'>Réinitialiser mon mot de passe</a>"
                + "</div>"
                + "<p style='color: #6B7280; font-size: 13px; line-height: 1.6;'>Ce lien est valide pendant <strong>30 minutes</strong>.</p>"
                + "</div>"
                + "<div style='background: #F9FAFB; padding: 20px; text-align: center; border-top: 1px solid #E5E7EB;'>"
                + "<p style='color: #9CA3AF; font-size: 12px; margin: 0;'>© 2026 PNLP - Tous droits réservés</p>"
                + "</div></div></body></html>";
    }
}