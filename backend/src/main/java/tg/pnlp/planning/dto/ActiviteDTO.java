package tg.pnlp.planning.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiviteDTO {
    private UUID id;
    private String titre;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Integer nombreJours;
    private String lieu;
    private String sourceFinancement;
    private String statut;
    private String commentaires;
    private List<UUID> agentIds;
    private List<String> agentNoms;
}
