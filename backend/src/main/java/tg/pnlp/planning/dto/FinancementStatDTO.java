package tg.pnlp.planning.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancementStatDTO {
    private String source;               // "FM", "PNLP", "État"...
    private int totalActivites;          // total planifié
    private int activitesRealisees;      // TERMINEE
    private int activitesEnCours;        // EN_COURS
    private int activitesPlanifiees;     // PLANIFIEE
    private int activitesRestantes;      // PLANIFIEE + EN_COURS
    private int totalJours;
    private double tauxRealisation;      // réalisées / total * 100
}