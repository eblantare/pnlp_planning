package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiviteResumeDTO {
    private UUID id;
    private String titre;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Integer nombreJours;
    private String lieu;
    private String sourceFinancement;
    private String statut;
    private String zone;

    // ✅ NOUVEAU
    private String typeLieu;   // "RESIDENT" ou "NON_RESIDENT"
    // ✅ NOUVEAU : flag "au programme"
    private Boolean auProgramme;
}