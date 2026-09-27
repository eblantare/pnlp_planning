package tg.pnlp.planning.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatistiquesGlobalesDTO {
    private int annee;
    private int mois;
    private String moisLibelle;

    // KPIs
    private int totalAgents;
    private int agentsActifs;
    private int agentsEnMission;
    private int agentsOccupes;         // ✅ NOUVEAU : agents affectés ce mois mais pas aujourd'hui
    private int agentsDisponibles;
    private int agentsInactifs;
    private int totalActivites;
    private int activitesEnCours;
    private int totalJoursMission;
    private double tauxOccupationMoyen;

    // Listes
    private List<StatistiquesAgentDTO> agents;
    private List<StatistiquesAgentDTO> topAgents;      // Top 5 des plus occupés
    private Map<String, Integer> repartitionMensuelle; // Pour graphique annuel

    // Détail par agent
    private Map<String, Integer> missionsParAgent;      // Nom -> nb missions dans l'année
}