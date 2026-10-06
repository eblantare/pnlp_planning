package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiviteDTO {
    private UUID id;
    private String titre;
    private String commentaires;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Integer nombreJours;
    private String lieu;
    private String sourceFinancement;

    // ✅ NOUVEAU : type de lieu
    private String typeLieu;   // "RESIDENT" ou "NON_RESIDENT"

    private String statut;
    private List<UUID> agentIds;
    private List<String> agentNoms;
    private List<UUID> agentIdsForces;
    private Map<UUID, String> agentZones;
    private List<String> agentZonesList;
    private String region;
    private List<String> districts;
    private UUID createdById;
    private String createdByNom;

    private String tdrFilename;
    private LocalDateTime tdrUploadedAt;
    private Boolean tdrConforme;

    private String ordreMissionFilename;
    private LocalDateTime ordreMissionUploadedAt;
    private Boolean ordreMissionConforme;

    private String lettreInvitationFilename;
    private LocalDateTime lettreInvitationUploadedAt;
    private Boolean lettreInvitationConforme;

    private Boolean tdrEmailEnvoye;
    private Boolean ordreMissionEmailEnvoye;
    // ✅ NOUVEAU : activité au programme
    private Boolean auProgramme;
    private Integer niveauValidationActuel;
    private Integer valideParNiveau;
    private Integer renvoyeParNiveau;
    private List<ConflitAgentDTO> conflits;
}