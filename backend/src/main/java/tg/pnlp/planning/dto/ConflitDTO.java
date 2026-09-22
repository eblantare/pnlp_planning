package tg.pnlp.planning.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflitDTO {
    private UUID agentId;
    private String agentNom;
    private String typeConflit;
    private String activiteConflit;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String motif;
}
