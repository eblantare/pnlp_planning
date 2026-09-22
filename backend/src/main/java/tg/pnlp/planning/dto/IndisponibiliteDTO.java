package tg.pnlp.planning.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndisponibiliteDTO {
    private UUID id;
    private UUID agentId;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String type;
    private String motif;
}
