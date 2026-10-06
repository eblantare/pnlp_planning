package tg.pnlp.planning.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionValidationDTO {
    private List<ValidationConflitDTO> actions;
    private String decision;            // VALIDER, RENVOYER_NIVEAU_SUPERIEUR, RENVOYER_PLANIFICATEUR
    private String commentaire;
}