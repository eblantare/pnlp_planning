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
    private String motif;
}