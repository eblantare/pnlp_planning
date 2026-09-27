package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.dto.StatistiquesAgentDTO;
import tg.pnlp.planning.dto.StatistiquesGlobalesDTO;
import tg.pnlp.planning.entity.Activite;
import tg.pnlp.planning.entity.Affectation;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.repository.ActiviteRepository;
import tg.pnlp.planning.repository.AffectationRepository;
import tg.pnlp.planning.repository.AgentRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatistiquesService {

    private final AgentRepository agentRepository;
    private final ActiviteRepository activiteRepository;
    private final AffectationRepository affectationRepository;

    private static final String[] MOIS_LIBELLES = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    // ✅ Abréviations distinctes (Jun ≠ Jul)
    private static final String[] MOIS_ABREGES = {
            "Jan", "Fév", "Mar", "Avr", "Mai", "Jun",
            "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"
    };

    /**
     * ✅ Helper : vérifie qu'une activité a bien des dates valides.
     * Les BROUILLONS sans dates doivent être IGNORÉS dans les statistiques.
     */
    private boolean aDesDatesValides(Activite act) {
        return act != null && act.getDateDebut() != null && act.getDateFin() != null;
    }

    /**
     * Calcule toutes les statistiques pour un mois donné.
     */
    public StatistiquesGlobalesDTO getStatistiquesMensuelles(int annee, int mois) {
        log.info("Calcul des statistiques pour {} {}", MOIS_LIBELLES[mois - 1], annee);

        YearMonth yearMonth = YearMonth.of(annee, mois);
        LocalDate debut = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();
        LocalDate aujourdHui = LocalDate.now();

        int joursOuvrables = compterJoursOuvrables(debut, fin);

        // 1. Récupérer tous les agents actifs
        List<Agent> agents = agentRepository.findByActifTrue();

        // 2. Récupérer toutes les activités du mois (avec affectations)
        // ✅ On filtre immédiatement les activités sans dates (brouillons)
        List<Activite> activites = activiteRepository.findByPeriodeWithAffectations(debut, fin)
                .stream()
                .filter(this::aDesDatesValides)
                .toList();

        // 3. Récupérer les activités en cours aujourd'hui
        List<Activite> activitesEnCours = activites.stream()
                .filter(a -> !aujourdHui.isBefore(a.getDateDebut())
                        && !aujourdHui.isAfter(a.getDateFin()))
                .toList();

        // 4. Construire la liste des statistiques par agent
        List<StatistiquesAgentDTO> statsAgents = new ArrayList<>();
        Map<UUID, Set<Activite>> activitesParAgent = new HashMap<>();

        for (Activite activite : activites) {
            for (Affectation aff : activite.getAffectations()) {
                if (aff.getAgent() != null) {
                    activitesParAgent
                            .computeIfAbsent(aff.getAgent().getId(), k -> new HashSet<>())
                            .add(activite);
                }
            }
        }

        for (Agent agent : agents) {
            Set<Activite> activitesAgent = activitesParAgent.getOrDefault(agent.getId(), Collections.emptySet());

            int joursMission = calculerJoursMissionUniques(activitesAgent, debut, fin);
            double tauxOccupation = joursOuvrables > 0
                    ? Math.round((joursMission * 100.0 / joursOuvrables) * 100.0) / 100.0
                    : 0;

            String statut = determinerStatut(agent, activitesAgent, aujourdHui);

            String activiteEnCours = null;
            String periodeActuelle = null;
            if ("EN_MISSION".equals(statut)) {
                Optional<Activite> activiteEnCoursOpt = activitesAgent.stream()
                        .filter(a -> aDesDatesValides(a)
                                && !aujourdHui.isBefore(a.getDateDebut())
                                && !aujourdHui.isAfter(a.getDateFin()))
                        .findFirst();

                if (activiteEnCoursOpt.isPresent()) {
                    Activite act = activiteEnCoursOpt.get();
                    activiteEnCours = act.getTitre();
                    periodeActuelle = act.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM"))
                            + " au "
                            + act.getDateFin().format(DateTimeFormatter.ofPattern("dd/MM"));
                }
            }

            statsAgents.add(StatistiquesAgentDTO.builder()
                    .agentId(agent.getId())
                    .nom(agent.getNom())
                    .prenom(agent.getPrenom())
                    .nomComplet(agent.getNomComplet())
                    .poste(agent.getPoste())
                    .unite(agent.getUnite())
                    .actif(agent.getActif())
                    .nombreActivites(activitesAgent.size())
                    .joursMission(joursMission)
                    .joursOuvrables(joursOuvrables)
                    .tauxOccupation(tauxOccupation)
                    .statut(statut)
                    .activiteEnCours(activiteEnCours)
                    .periodeActuelle(periodeActuelle)
                    .build());
        }

        statsAgents.sort(Comparator.comparingDouble(StatistiquesAgentDTO::getTauxOccupation).reversed());

        // 5. KPIs globaux
        long agentsEnMission = statsAgents.stream()
                .filter(s -> "EN_MISSION".equals(s.getStatut()))
                .count();

        long agentsDisponibles = statsAgents.stream()
                .filter(s -> "DISPONIBLE".equals(s.getStatut()))
                .count();

        int totalJoursMission = statsAgents.stream()
                .mapToInt(StatistiquesAgentDTO::getJoursMission)
                .sum();

        double tauxOccupationMoyen = statsAgents.isEmpty() ? 0
                : statsAgents.stream()
                .mapToDouble(StatistiquesAgentDTO::getTauxOccupation)
                .average()
                .orElse(0);
        tauxOccupationMoyen = Math.round(tauxOccupationMoyen * 100.0) / 100.0;

        // 6. Top 5 des agents
        List<StatistiquesAgentDTO> topAgents = statsAgents.stream()
                .filter(s -> s.getJoursMission() > 0)
                .limit(5)
                .toList();

        // 7. Répartition mensuelle (ordre Jan → Déc avec abréviations distinctes)
        // ✅ On filtre les activités sans dates pour ne pas fausser le comptage
        Map<String, Integer> repartitionMensuelle = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(annee, m);
            long count = activiteRepository.findByPeriodeWithAffectations(
                            ym.atDay(1), ym.atEndOfMonth())
                    .stream()
                    .filter(this::aDesDatesValides)
                    .count();
            repartitionMensuelle.put(MOIS_ABREGES[m - 1], (int) count);
        }

        // 8. Missions par agent sur l'année
        Map<String, Integer> missionsParAgent = new LinkedHashMap<>();
        LocalDate debutAnnee = LocalDate.of(annee, 1, 1);
        LocalDate finAnnee = LocalDate.of(annee, 12, 31);
        List<Activite> toutesActivites = activiteRepository.findByPeriodeWithAffectations(debutAnnee, finAnnee)
                .stream()
                .filter(this::aDesDatesValides)
                .toList();

        Map<UUID, Integer> compteur = new HashMap<>();
        Map<UUID, Agent> agentsParId = new HashMap<>();
        for (Activite act : toutesActivites) {
            for (Affectation aff : act.getAffectations()) {
                if (aff.getAgent() != null) {
                    compteur.merge(aff.getAgent().getId(), 1, Integer::sum);
                    agentsParId.put(aff.getAgent().getId(), aff.getAgent());
                }
            }
        }

        compteur.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Agent a = agentsParId.get(e.getKey());
                    if (a != null) {
                        missionsParAgent.put(a.getNomComplet(), e.getValue());
                    }
                });

        return StatistiquesGlobalesDTO.builder()
                .annee(annee)
                .mois(mois)
                .moisLibelle(MOIS_LIBELLES[mois - 1])
                .totalAgents(agents.size())
                .agentsActifs(agents.size())
                .agentsEnMission((int) agentsEnMission)
                .agentsDisponibles((int) agentsDisponibles)
                .agentsInactifs(0)
                .totalActivites(activites.size())
                .activitesEnCours(activitesEnCours.size())
                .totalJoursMission(totalJoursMission)
                .tauxOccupationMoyen(tauxOccupationMoyen)
                .agents(statsAgents)
                .topAgents(topAgents)
                .repartitionMensuelle(repartitionMensuelle)
                .missionsParAgent(missionsParAgent)
                .build();
    }

    private int compterJoursOuvrables(LocalDate debut, LocalDate fin) {
        int jours = 0;
        LocalDate current = debut;
        while (!current.isAfter(fin)) {
            if (current.getDayOfWeek().getValue() <= 5) {
                jours++;
            }
            current = current.plusDays(1);
        }
        return jours;
    }

    private int calculerJoursMissionUniques(Set<Activite> activites, LocalDate debutMois, LocalDate finMois) {
        Set<LocalDate> joursUniques = new HashSet<>();

        for (Activite act : activites) {
            // ✅ Sécurité : ignorer les activités sans dates
            if (!aDesDatesValides(act)) {
                continue;
            }

            LocalDate debut = act.getDateDebut().isBefore(debutMois) ? debutMois : act.getDateDebut();
            LocalDate fin = act.getDateFin().isAfter(finMois) ? finMois : act.getDateFin();

            LocalDate current = debut;
            while (!current.isAfter(fin)) {
                joursUniques.add(current);
                current = current.plusDays(1);
            }
        }

        return joursUniques.size();
    }

    private String determinerStatut(Agent agent, Set<Activite> activites, LocalDate aujourdHui) {
        if (!agent.getActif()) {
            return "INACTIF";
        }

        boolean enMission = activites.stream()
                .anyMatch(a -> aDesDatesValides(a)
                        && !aujourdHui.isBefore(a.getDateDebut())
                        && !aujourdHui.isAfter(a.getDateFin()));

        return enMission ? "EN_MISSION" : "DISPONIBLE";
    }
}