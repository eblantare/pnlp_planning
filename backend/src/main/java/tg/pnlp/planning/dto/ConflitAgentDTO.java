package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflitAgentDTO {
    private UUID agentId;
    private String agentNom;
    private String agentPrenom;
    private String agentPoste;
    private String typeConflit;       // "AFFECTATION_SIMULTANEE" ou "INDISPONIBILITE"
    private String activiteConflit;
    private LocalDate dateDebut;
    private LocalDate dateFin;

    /** Motif d'origine du conflit (indisponibilité, etc.) — venu de la détection. */
    private String motif;

    // ✅ NOUVEAU : gestion par le validateur
    private UUID affectationId;
    private String actionValidation;   // EN_ATTENTE, RETIRER, REMPLACER, FORCER
    private UUID agentRemplacantId;
    private String agentRemplacantNom;
    private Boolean force;

    /** Motif saisi par le validateur lors du traitement du conflit. */
    private String motifConflit;       // ✅ AJOUTÉ
}