package tg.pnlp.planning.dto;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffectationDTO {
    private UUID id;
    private UUID agentId;
    private String agentNom;
    private UUID activiteId;
    private String activiteTitre;
    private String role;
}
