package tg.pnlp.planning.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReponseCreationActiviteDTO {
    private boolean succes;
    private ActiviteDTO activite;
    private List<ConflitAgentDTO> conflits;
    private String message;
}