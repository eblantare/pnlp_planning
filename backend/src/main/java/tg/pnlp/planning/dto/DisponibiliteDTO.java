package tg.pnlp.planning.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibiliteDTO {
    private UUID agentId;
    private String agentNom;
    private Boolean disponible;
    private List<IndisponibiliteDTO> indisponibilites;
    private List<AffectationDTO> affectations;
}
