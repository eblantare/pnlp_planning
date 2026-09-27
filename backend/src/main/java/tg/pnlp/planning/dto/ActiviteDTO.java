package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDate;
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
    // ✅ Dates nullables (brouillons sans dates)
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Integer nombreJours;
    private String lieu;
    private String sourceFinancement;
    private String statut;
    private List<UUID> agentIds;
    private List<String> agentNoms;
    private List<UUID> agentIdsForces;

    // ✅ Map agentId -> zone d'affectation (optionnel)
    private Map<UUID, String> agentZones;

    // ✅ Liste parallèle à agentIds pour les zones (frontend)
    private List<String> agentZonesList;
    private String region;
    private List<String> districts;

    private UUID createdById;
    private String createdByNom;
}