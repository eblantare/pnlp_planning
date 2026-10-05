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

    // ✅ NOUVEAU : période personnalisée
    private String dateDebut;
    private String dateFin;
    private String periodeLibelle;
    private int agentsAuProgramme;

    private int totalAgents;
    private int agentsActifs;
    private int agentsEnMission;
    private int agentsOccupes;
    private int agentsDisponibles;
    private int agentsInactifs;
    private int totalActivites;
    private int activitesEnCours;
    private int totalJoursMission;
    private double tauxOccupationMoyen;

    private List<StatistiquesAgentDTO> agents;
    private List<StatistiquesAgentDTO> topAgents;
    private Map<String, Integer> repartitionMensuelle;
    private Map<String, Integer> missionsParAgent;

    // ✅ NOUVEAU : répartition par source de financement
    private List<FinancementStatDTO> repartitionParFinancement;
}