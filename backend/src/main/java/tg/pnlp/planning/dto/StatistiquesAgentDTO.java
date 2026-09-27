package tg.pnlp.planning.dto;

import lombok.*;

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

    // Statistiques
    private Integer nombreActivites;      // Nombre d'activités sur le mois
    private Integer joursMission;         // Nombre de jours de mission
    private Integer joursOuvrables;       // Jours ouvrables du mois
    private Double tauxOccupation;        // (joursMission / joursOuvrables) * 100
    private String statut;                // "EN_MISSION" | "DISPONIBLE" | "INACTIF"

    // Détails
    private String activiteEnCours;       // Titre de l'activité en cours
    private String periodeActuelle;       // "25/09 au 30/09"
}
