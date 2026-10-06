package tg.pnlp.planning.dto;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationConflitDTO {
    private UUID affectationId;
    private String action;              // RETIRER, REMPLACER, FORCER
    private UUID agentRemplacantId;
    private String motif;
}