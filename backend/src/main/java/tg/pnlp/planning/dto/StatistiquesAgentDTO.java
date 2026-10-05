package tg.pnlp.planning.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatistiquesAgentDTO {
    private UUID agentId;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String poste;
    private String unite;
    private Boolean actif;

    private Integer nombreActivites;
    private Integer joursMission;
    private Integer joursOuvrables;
    private Double tauxOccupation;
    private String statut;

    private String activiteEnCours;
    private String periodeActuelle;

    private String tacheEnCours;

    private List<JourOccupationDTO> joursDetails;
    private List<ActiviteResumeDTO> activites;

    // ✅ Compteurs missions
    private Integer missionsResident;
    private Integer missionsNonResident;
    private Integer missionsTotal;

    // ✅ Jours par type de lieu
    private Integer joursResident;
    private Integer joursNonResident;

    // ✅ Taux par type de lieu
    private Double tauxResident;
    private Double tauxNonResident;

    // ✅ Nombre d'activités "au programme"
    private Integer missionsProgramme;
}