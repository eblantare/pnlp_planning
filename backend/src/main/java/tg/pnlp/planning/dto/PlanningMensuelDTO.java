package tg.pnlp.planning.dto;

import lombok.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanningMensuelDTO {
    private String mois;
    private List<ActiviteDTO> activites;
    private Map<UUID, List<ActiviteDTO>> planningParAgent;
}
