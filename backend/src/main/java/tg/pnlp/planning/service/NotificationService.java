package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.entity.Activite;
import tg.pnlp.planning.entity.Utilisateur;
import tg.pnlp.planning.repository.UtilisateurRepository;
import tg.pnlp.planning.security.ProfilCodeNormalizer;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final UtilisateurRepository utilisateurRepository;
    private final EmailService emailService;

    /**
     * Notifie tous les validateurs d'un niveau donné (par email).
     * Le SMS peut être ajouté plus tard si la passerelle est choisie.
     */
    public void notifierValidateur(Activite activite, int niveau) {
        String profilCode = switch (niveau) {
            case 1 -> ProfilCodeNormalizer.VALIDATEUR_NIVEAU_1;
            case 2 -> ProfilCodeNormalizer.VALIDATEUR_NIVEAU_2;
            case 3 -> ProfilCodeNormalizer.VALIDATEUR_NIVEAU_3;
            default -> null;
        };
        if (profilCode == null) return;

        var validateurs = utilisateurRepository.findByProfilCode(profilCode);
        for (Utilisateur u : validateurs) {
            try {
                emailService.envoyerNotificationValidation(u, activite, niveau);
            } catch (Exception e) {
                log.error("Échec notif validateur N{} {}: {}", niveau, u.getUsername(), e.getMessage());
            }
        }
    }

    public void notifierPlanificateurRenvoi(Activite activite, String motif) {
        var planificateurs = utilisateurRepository.findByProfilCode(ProfilCodeNormalizer.PLANIFICATEUR);
        for (Utilisateur u : planificateurs) {
            try {
                emailService.envoyerNotificationRenvoiPlanificateur(u, activite, motif);
            } catch (Exception e) {
                log.error("Échec notif renvoi planificateur {}: {}", u.getUsername(), e.getMessage());
            }
        }
    }

    public void notifierValidationFinale(Activite activite) {
        if (activite.getCreatedBy() != null && activite.getCreatedBy().getEmail() != null) {
            try {
                emailService.envoyerNotificationValidationFinale(activite.getCreatedBy(), activite);
            } catch (Exception e) {
                log.error("Échec notif validation finale: {}", e.getMessage());
            }
        }
    }
}