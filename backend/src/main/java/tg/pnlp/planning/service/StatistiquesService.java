package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.dto.*;
import tg.pnlp.planning.entity.Activite;
import tg.pnlp.planning.entity.Affectation;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.exception.ResourceNotFoundException;
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

    private static final String[] MOIS_ABREGES = {
            "Jan", "Fév", "Mar", "Avr", "Mai", "Jun",
            "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"
    };

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean aDesDatesValides(Activite act) {
        return act != null && act.getDateDebut() != null && act.getDateFin() != null;
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

    /**
     * ✅ Calcule les jours de mission UNIQUES (dédupliqués par jour calendaire).
     *
     * RÈGLE MÉTIER :
     *   - On ignore les activités AU PROGRAMME et les statuts non comptables
     *     (TERMINEE, ANNULEE, REPORTEE, BROUILLON).
     *   - ✅ Bonus +1 jour UNIQUEMENT pour les activités NON_RESIDENT.
     *      (Les activités au programme sont déjà exclues en amont, donc le
     *      bonus ne s'applique JAMAIS pour elles.)
     */
    private int calculerJoursMissionUniques(Set<Activite> activites, LocalDate debutMois, LocalDate finMois) {
        Set<LocalDate> joursUniques = new HashSet<>();
        int bonusNonResident = 0;

        for (Activite act : activites) {
            if (!aDesDatesValides(act)) continue;

            // ✅ Exclut : au programme + TERMINEE/ANNULEE/REPORTEE/BROUILLON
            if (estActiviteNonComptable(act)) continue;

            LocalDate debut = act.getDateDebut().isBefore(debutMois) ? debutMois : act.getDateDebut();
            LocalDate fin = act.getDateFin().isAfter(finMois) ? finMois : act.getDateFin();
            LocalDate current = debut;
            while (!current.isAfter(fin)) {
                joursUniques.add(current);
                current = current.plusDays(1);
            }

            // ✅ Bonus +1 UNIQUEMENT si NON_RESIDENT
            //    (les activités au programme sont déjà filtrées ci-dessus)
            if (act.getTypeLieu() == Activite.TypeLieu.NON_RESIDENT) {
                bonusNonResident++;
            }
        }
        return joursUniques.size() + bonusNonResident;
    }

    /**
     * ✅ Calcule les jours RÉSIDENT et NON_RÉSIDENT séparément (dédupliqués).
     *
     * RÈGLE MÉTIER :
     *   - On ignore les activités AU PROGRAMME et les statuts non comptables.
     *   - ✅ Bonus +1 jour UNIQUEMENT pour les activités NON_RESIDENT.
     *
     * Renvoie [joursResident, joursNonResident].
     */
    private int[] calculerJoursParType(Set<Activite> activites, LocalDate debutMois, LocalDate finMois) {
        Set<LocalDate> joursResident = new HashSet<>();
        Set<LocalDate> joursNonResident = new HashSet<>();
        int bonusNonResident = 0;

        for (Activite act : activites) {
            if (!aDesDatesValides(act)) continue;

            // ✅ Exclut : au programme + TERMINEE/ANNULEE/REPORTEE/BROUILLON
            if (estActiviteNonComptable(act)) continue;

            LocalDate debut = act.getDateDebut().isBefore(debutMois) ? debutMois : act.getDateDebut();
            LocalDate fin = act.getDateFin().isAfter(finMois) ? finMois : act.getDateFin();

            boolean estResident = (act.getTypeLieu() == Activite.TypeLieu.RESIDENT);
            Set<LocalDate> target = estResident ? joursResident : joursNonResident;

            LocalDate current = debut;
            while (!current.isAfter(fin)) {
                target.add(current);
                current = current.plusDays(1);
            }

            // ✅ Bonus +1 UNIQUEMENT si NON_RESIDENT
            if (!estResident) {
                bonusNonResident++;
            }
        }

        return new int[]{
                joursResident.size(),
                joursNonResident.size() + bonusNonResident
        };
    }

    /**
     * ✅ Détermine le statut d'un agent à une date de référence.
     * - INACTIF       : agent désactivé
     * - EN_MISSION    : mission (hors programme) en cours à la date de référence
     * - AU_PROGRAMME  : agent a une activité au programme dans la période
     * - OCCUPE        : agent a uniquement des activités TERMINEE dans la période
     * - DISPONIBLE    : aucune activité
     */
    private String determinerStatut(Agent agent, Set<Activite> activites, LocalDate dateReference) {
        if (!agent.getActif()) return "INACTIF";

        Set<Activite> activitesComptables = activites.stream()
                .filter(this::aDesDatesValides)
                .filter(a -> !estActiviteNonComptable(a))
                .collect(java.util.stream.Collectors.toSet());

        // 1. Mission en cours à la date de référence
        boolean enMission = activitesComptables.stream()
                .anyMatch(a -> !dateReference.isBefore(a.getDateDebut())
                        && !dateReference.isAfter(a.getDateFin()));
        if (enMission) return "EN_MISSION";

        // 2. Au programme
        boolean auProgramme = activites.stream()
                .anyMatch(a -> aDesDatesValides(a)
                        && estActiviteProgramme(a)
                        && (a.getStatut() == Activite.StatutActivite.PLANIFIEE
                        || a.getStatut() == Activite.StatutActivite.EN_COURS
                        || a.getStatut() == Activite.StatutActivite.REPORTEE));
        if (auProgramme) return "AU_PROGRAMME";

        // 3. Occupé
        boolean aDesActivitesTerminees = activites.stream()
                .anyMatch(a -> aDesDatesValides(a)
                        && !estActiviteProgramme(a)
                        && a.getStatut() == Activite.StatutActivite.TERMINEE);
        if (aDesActivitesTerminees) return "OCCUPE";

        return "DISPONIBLE";
    }

    /**
     * ✅ Compte les missions RÉSIDENT et NON_RÉSIDENT en EXCLUANT les activités au programme.
     *    Renvoie [resident, nonResident].
     */
    private int[] compterMissionsParType(Set<Activite> activites) {
        int resident = 0;
        int nonResident = 0;
        for (Activite act : activites) {
            if (act == null || estActiviteProgramme(act)) continue;
            if (estStatutNonComptable(act)) continue;

            if (act.getTypeLieu() == Activite.TypeLieu.RESIDENT) {
                resident++;
            } else {
                nonResident++;
            }
        }
        return new int[]{resident, nonResident};
    }

    /**
     * ✅ Vrai si le statut de l'activité ne doit pas être compté.
     */
    private boolean estStatutNonComptable(Activite act) {
        if (act == null || act.getStatut() == null) return false;
        Activite.StatutActivite s = act.getStatut();
        return s == Activite.StatutActivite.TERMINEE
                || s == Activite.StatutActivite.ANNULEE
                || s == Activite.StatutActivite.REPORTEE
                || s == Activite.StatutActivite.BROUILLON;
    }

    /**
     * ✅ Compte les activités AU PROGRAMME (hors statuts non comptables).
     */
    private int compterMissionsProgramme(Set<Activite> activites) {
        if (activites == null) return 0;
        return (int) activites.stream()
                .filter(this::estActiviteProgramme)
                .filter(a -> !estStatutNonComptable(a))
                .count();
    }

    /**
     * ✅ Vérifie si l'activité est "au programme".
     */
    private boolean estActiviteProgramme(Activite act) {
        return act != null && Boolean.TRUE.equals(act.getAuProgramme());
    }

    /**
     * ✅ Vrai si l'activité NE DOIT PAS être comptée dans les statistiques
     *    des jours de mission (elle est au programme OU dans un statut non comptable).
     */
    private boolean estActiviteNonComptable(Activite act) {
        if (act == null) return true;
        if (estActiviteProgramme(act)) return true;
        return estStatutNonComptable(act);
    }

    private ActiviteResumeDTO convertToActiviteResume(Activite act) {
        String zone = null;
        if (act.getAffectations() != null && !act.getAffectations().isEmpty()) {
            zone = act.getAffectations().stream()
                    .findFirst()
                    .map(Affectation::getZoneAffectation)
                    .orElse(null);
        }
        return ActiviteResumeDTO.builder()
                .id(act.getId())
                .titre(act.getTitre())
                .dateDebut(act.getDateDebut())
                .dateFin(act.getDateFin())
                .nombreJours(act.getNombreJours())
                .lieu(act.getLieu())
                .sourceFinancement(act.getSourceFinancement())
                .statut(act.getStatut() != null ? act.getStatut().name() : null)
                .zone(zone)
                .typeLieu(act.getTypeLieu() != null ? act.getTypeLieu().name() : null)
                .auProgramme(act.getAuProgramme())
                .build();
    }

    /**
     * ✅ Calcule le libellé de la tâche en cours ou à venir à la date de référence.
     * - EN_MISSION   → tâche en cours à la date de référence
     * - AU_PROGRAMME → prochaine tâche à venir
     */
    private String[] calculerTacheEtPeriode(Set<Activite> activitesAgent,
                                            String statut,
                                            LocalDate dateReference) {
        String tache = null;
        String periode = null;

        if ("EN_MISSION".equals(statut)) {
            Optional<Activite> opt = activitesAgent.stream()
                    .filter(this::aDesDatesValides)
                    .filter(a -> !estActiviteNonComptable(a))
                    .filter(a -> !dateReference.isBefore(a.getDateDebut())
                            && !dateReference.isAfter(a.getDateFin()))
                    .findFirst();
            if (opt.isPresent()) {
                Activite act = opt.get();
                tache = act.getTitre();
                periode = act.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM"))
                        + " au "
                        + act.getDateFin().format(DateTimeFormatter.ofPattern("dd/MM"));
            }
        } else if ("AU_PROGRAMME".equals(statut)) {
            Optional<Activite> opt = activitesAgent.stream()
                    .filter(this::aDesDatesValides)
                    .filter(this::estActiviteProgramme)
                    .filter(a -> a.getStatut() == Activite.StatutActivite.PLANIFIEE
                            || a.getStatut() == Activite.StatutActivite.EN_COURS
                            || a.getStatut() == Activite.StatutActivite.REPORTEE)
                    .min(Comparator.comparing(Activite::getDateDebut));
            if (opt.isPresent()) {
                Activite act = opt.get();
                tache = act.getTitre();
                periode = act.getDateDebut().format(DateTimeFormatter.ofPattern("dd/MM"))
                        + " au "
                        + act.getDateFin().format(DateTimeFormatter.ofPattern("dd/MM"));
            }
        }

        return new String[]{tache, periode};
    }

    /**
     * ✅ Calcule la date de référence pour une période :
     *    - Si aujourd'hui ∈ [debut, fin] → aujourd'hui
     *    - Sinon → le dernier jour de la période
     */
    private LocalDate calculerDateReference(LocalDate debut, LocalDate fin) {
        LocalDate aujourdHui = LocalDate.now();
        return (!aujourdHui.isBefore(debut) && !aujourdHui.isAfter(fin))
                ? aujourdHui
                : fin;
    }

    // ============================================================
    // STATISTIQUES MENSUELLES
    // ============================================================

    public StatistiquesGlobalesDTO getStatistiquesMensuelles(int annee, int mois) {
        log.info("Calcul des statistiques pour {} {}", MOIS_LIBELLES[mois - 1], annee);

        YearMonth yearMonth = YearMonth.of(annee, mois);
        LocalDate debut = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();
        LocalDate dateReference = calculerDateReference(debut, fin);

        int joursOuvrables = compterJoursOuvrables(debut, fin);

        List<Agent> agents = agentRepository.findByActifTrue();

        List<Activite> activites = activiteRepository.findByPeriodeWithAffectations(debut, fin)
                .stream()
                .filter(this::aDesDatesValides)
                .toList();

        List<Activite> activitesEnCours = activites.stream()
                .filter(a -> !dateReference.isBefore(a.getDateDebut())
                        && !dateReference.isAfter(a.getDateFin()))
                .toList();

        List<StatistiquesAgentDTO> statsAgents = new ArrayList<>();
        Map<UUID, Set<Activite>> activitesParAgent = new HashMap<>();

        for (Activite activite : activites) {
            for (Affectation aff : activite.getAffectations()) {
                if (aff.getAgent() != null && Boolean.TRUE.equals(aff.getAgent().getActif())) {
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

            String statut = determinerStatut(agent, activitesAgent, dateReference);
            String[] tacheEtPeriode = calculerTacheEtPeriode(activitesAgent, statut, dateReference);
            String tacheEnCours = tacheEtPeriode[0];
            String periodeActuelle = tacheEtPeriode[1];

            int[] compteurs = compterMissionsParType(activitesAgent);
            int[] joursParType = calculerJoursParType(activitesAgent, debut, fin);

            int missionsProgramme = compterMissionsProgramme(activitesAgent);
            int missionsTotal = compteurs[0] + compteurs[1];

            double tauxResident = joursOuvrables > 0
                    ? Math.round((joursParType[0] * 100.0 / joursOuvrables) * 100.0) / 100.0
                    : 0;
            double tauxNonResident = joursOuvrables > 0
                    ? Math.round((joursParType[1] * 100.0 / joursOuvrables) * 100.0) / 100.0
                    : 0;

            statsAgents.add(StatistiquesAgentDTO.builder()
                    .agentId(agent.getId())
                    .nom(agent.getNom())
                    .prenom(agent.getPrenom())
                    .nomComplet(agent.getNomComplet())
                    .poste(agent.getPoste())
                    .unite(agent.getUnite())
                    .actif(agent.getActif())
                    .nombreActivites(missionsTotal)
                    .joursMission(joursMission)
                    .joursOuvrables(joursOuvrables)
                    .tauxOccupation(tauxOccupation)
                    .statut(statut)
                    .tacheEnCours(tacheEnCours)
                    .periodeActuelle(periodeActuelle)
                    .missionsResident(compteurs[0])
                    .missionsNonResident(compteurs[1])
                    .missionsTotal(missionsTotal)
                    .missionsProgramme(missionsProgramme)
                    .joursResident(joursParType[0])
                    .joursNonResident(joursParType[1])
                    .tauxResident(tauxResident)
                    .tauxNonResident(tauxNonResident)
                    .build());
        }

        statsAgents.sort(Comparator.comparingDouble(StatistiquesAgentDTO::getTauxOccupation).reversed());

        long agentsEnMission = statsAgents.stream().filter(s -> "EN_MISSION".equals(s.getStatut())).count();
        long agentsAuProgramme = statsAgents.stream().filter(s -> "AU_PROGRAMME".equals(s.getStatut())).count();
        long agentsOccupes = statsAgents.stream().filter(s -> "OCCUPE".equals(s.getStatut())).count();
        long agentsDisponibles = statsAgents.stream().filter(s -> "DISPONIBLE".equals(s.getStatut())).count();
        long agentsInactifs = statsAgents.stream().filter(s -> "INACTIF".equals(s.getStatut())).count();

        int totalJoursMission = statsAgents.stream().mapToInt(StatistiquesAgentDTO::getJoursMission).sum();

        double tauxOccupationMoyen = statsAgents.isEmpty() ? 0
                : statsAgents.stream().mapToDouble(StatistiquesAgentDTO::getTauxOccupation).average().orElse(0);
        tauxOccupationMoyen = Math.round(tauxOccupationMoyen * 100.0) / 100.0;

        List<StatistiquesAgentDTO> topAgents = statsAgents.stream()
                .filter(s -> s.getJoursMission() > 0)
                .limit(5)
                .toList();

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
                    if (a != null) missionsParAgent.put(a.getNomComplet(), e.getValue());
                });

        List<FinancementStatDTO> repartitionFinancement = calculerRepartitionFinancement(activites);

        return StatistiquesGlobalesDTO.builder()
                .annee(annee)
                .mois(mois)
                .moisLibelle(MOIS_LIBELLES[mois - 1])
                .dateDebut(debut.toString())
                .dateFin(fin.toString())
                .periodeLibelle(MOIS_LIBELLES[mois - 1] + " " + annee)
                .totalAgents(agents.size())
                .agentsActifs(agents.size())
                .agentsEnMission((int) agentsEnMission)
                .agentsAuProgramme((int) agentsAuProgramme)
                .agentsOccupes((int) agentsOccupes)
                .agentsDisponibles((int) agentsDisponibles)
                .agentsInactifs((int) agentsInactifs)
                .totalActivites(activites.size())
                .activitesEnCours(activitesEnCours.size())
                .totalJoursMission(totalJoursMission)
                .tauxOccupationMoyen(tauxOccupationMoyen)
                .agents(statsAgents)
                .topAgents(topAgents)
                .repartitionMensuelle(repartitionMensuelle)
                .missionsParAgent(missionsParAgent)
                .repartitionParFinancement(repartitionFinancement)
                .build();
    }

    // ============================================================
    // STATISTIQUES PÉRIODE PERSONNALISÉE
    // ============================================================

    public StatistiquesGlobalesDTO getStatistiquesPeriode(LocalDate debut, LocalDate fin) {
        log.info("Calcul des statistiques du {} au {}", debut, fin);

        LocalDate dateReference = calculerDateReference(debut, fin);
        int joursOuvrables = compterJoursOuvrables(debut, fin);

        List<Agent> agents = agentRepository.findByActifTrue();

        List<Activite> activites = activiteRepository.findByPeriodeWithAffectations(debut, fin)
                .stream()
                .filter(this::aDesDatesValides)
                .toList();

        List<Activite> activitesEnCours = activites.stream()
                .filter(a -> !dateReference.isBefore(a.getDateDebut())
                        && !dateReference.isAfter(a.getDateFin()))
                .toList();

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

        List<StatistiquesAgentDTO> statsAgents = new ArrayList<>();

        for (Agent agent : agents) {
            Set<Activite> activitesAgent = activitesParAgent.getOrDefault(
                    agent.getId(), Collections.emptySet());

            int joursMission = calculerJoursMissionUniques(activitesAgent, debut, fin);
            double tauxOccupation = joursOuvrables > 0
                    ? Math.round((joursMission * 100.0 / joursOuvrables) * 100.0) / 100.0
                    : 0;

            String statut = determinerStatut(agent, activitesAgent, dateReference);

            List<JourOccupationDTO> joursDetails = new ArrayList<>();
            for (Activite act : activitesAgent) {
                if (!aDesDatesValides(act)) continue;
                LocalDate d = act.getDateDebut().isBefore(debut) ? debut : act.getDateDebut();
                LocalDate f = act.getDateFin().isAfter(fin) ? fin : act.getDateFin();
                LocalDate cur = d;
                while (!cur.isAfter(f)) {
                    joursDetails.add(JourOccupationDTO.builder()
                            .date(cur)
                            .activiteTitre(act.getTitre())
                            .build());
                    cur = cur.plusDays(1);
                }
            }

            List<ActiviteResumeDTO> activitesResume = activitesAgent.stream()
                    .sorted(Comparator.comparing(Activite::getDateDebut))
                    .map(this::convertToActiviteResume)
                    .toList();

            String[] tacheEtPeriode = calculerTacheEtPeriode(activitesAgent, statut, dateReference);
            String tacheEnCours = tacheEtPeriode[0];
            String periodeActuelle = tacheEtPeriode[1];

            int[] compteurs = compterMissionsParType(activitesAgent);
            int[] joursParType = calculerJoursParType(activitesAgent, debut, fin);

            int missionsProgramme = compterMissionsProgramme(activitesAgent);
            int missionsTotal = compteurs[0] + compteurs[1];

            double tauxResident = joursOuvrables > 0
                    ? Math.round((joursParType[0] * 100.0 / joursOuvrables) * 100.0) / 100.0
                    : 0;
            double tauxNonResident = joursOuvrables > 0
                    ? Math.round((joursParType[1] * 100.0 / joursOuvrables) * 100.0) / 100.0
                    : 0;

            statsAgents.add(StatistiquesAgentDTO.builder()
                    .agentId(agent.getId())
                    .nom(agent.getNom())
                    .prenom(agent.getPrenom())
                    .nomComplet(agent.getNomComplet())
                    .poste(agent.getPoste())
                    .unite(agent.getUnite())
                    .actif(agent.getActif())
                    .nombreActivites(missionsTotal)
                    .joursMission(joursMission)
                    .joursOuvrables(joursOuvrables)
                    .tauxOccupation(tauxOccupation)
                    .statut(statut)
                    .tacheEnCours(tacheEnCours)
                    .periodeActuelle(periodeActuelle)
                    .joursDetails(joursDetails)
                    .activites(activitesResume)
                    .missionsResident(compteurs[0])
                    .missionsNonResident(compteurs[1])
                    .missionsTotal(missionsTotal)
                    .missionsProgramme(missionsProgramme)
                    .joursResident(joursParType[0])
                    .joursNonResident(joursParType[1])
                    .tauxResident(tauxResident)
                    .tauxNonResident(tauxNonResident)
                    .build());
        }

        statsAgents.sort(Comparator.comparingDouble(StatistiquesAgentDTO::getTauxOccupation).reversed());

        long agentsEnMission = statsAgents.stream().filter(s -> "EN_MISSION".equals(s.getStatut())).count();
        long agentsAuProgramme = statsAgents.stream().filter(s -> "AU_PROGRAMME".equals(s.getStatut())).count();
        long agentsOccupes = statsAgents.stream().filter(s -> "OCCUPE".equals(s.getStatut())).count();
        long agentsDisponibles = statsAgents.stream().filter(s -> "DISPONIBLE".equals(s.getStatut())).count();
        long agentsInactifs = statsAgents.stream().filter(s -> "INACTIF".equals(s.getStatut())).count();

        int totalJoursMission = statsAgents.stream().mapToInt(StatistiquesAgentDTO::getJoursMission).sum();

        double tauxOccupationMoyen = statsAgents.isEmpty() ? 0
                : statsAgents.stream().mapToDouble(StatistiquesAgentDTO::getTauxOccupation).average().orElse(0);
        tauxOccupationMoyen = Math.round(tauxOccupationMoyen * 100.0) / 100.0;

        List<StatistiquesAgentDTO> topAgents = statsAgents.stream()
                .filter(s -> s.getJoursMission() > 0)
                .limit(5)
                .toList();

        int anneeRef = debut.getYear();
        Map<String, Integer> repartitionMensuelle = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(anneeRef, m);
            long count = activiteRepository.findByPeriodeWithAffectations(
                            ym.atDay(1), ym.atEndOfMonth())
                    .stream()
                    .filter(this::aDesDatesValides)
                    .count();
            repartitionMensuelle.put(MOIS_ABREGES[m - 1], (int) count);
        }

        Map<String, Integer> missionsParAgent = new LinkedHashMap<>();
        Map<UUID, Integer> compteur = new HashMap<>();
        Map<UUID, Agent> agentsParId = new HashMap<>();
        for (Activite act : activites) {
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
                    if (a != null) missionsParAgent.put(a.getNomComplet(), e.getValue());
                });

        List<FinancementStatDTO> repartitionFinancement = calculerRepartitionFinancement(activites);

        return StatistiquesGlobalesDTO.builder()
                .annee(anneeRef)
                .mois(debut.getMonthValue())
                .moisLibelle(MOIS_LIBELLES[debut.getMonthValue() - 1])
                .dateDebut(debut.toString())
                .dateFin(fin.toString())
                .periodeLibelle(debut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " → " + fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .totalAgents(agents.size())
                .agentsActifs(agents.size())
                .agentsEnMission((int) agentsEnMission)
                .agentsAuProgramme((int) agentsAuProgramme)
                .agentsOccupes((int) agentsOccupes)
                .agentsDisponibles((int) agentsDisponibles)
                .agentsInactifs((int) agentsInactifs)
                .totalActivites(activites.size())
                .activitesEnCours(activitesEnCours.size())
                .totalJoursMission(totalJoursMission)
                .tauxOccupationMoyen(tauxOccupationMoyen)
                .agents(statsAgents)
                .topAgents(topAgents)
                .repartitionMensuelle(repartitionMensuelle)
                .missionsParAgent(missionsParAgent)
                .repartitionParFinancement(repartitionFinancement)
                .build();
    }

    // ============================================================
    // HISTORIQUE D'UN AGENT
    // ============================================================

    public StatistiquesAgentDTO getHistoriqueAgent(UUID agentId, LocalDate debut, LocalDate fin) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + agentId));

        int joursOuvrables = compterJoursOuvrables(debut, fin);

        List<Activite> activites = activiteRepository
                .findByPeriodeWithAffectationsAndAgent(debut, fin, agentId)
                .stream()
                .filter(this::aDesDatesValides)
                .toList();

        Set<Activite> activitesAgent = new HashSet<>(activites);

        int joursMission = calculerJoursMissionUniques(activitesAgent, debut, fin);
        double tauxOccupation = joursOuvrables > 0
                ? Math.round((joursMission * 100.0 / joursOuvrables) * 100.0) / 100.0
                : 0;

        List<JourOccupationDTO> joursDetails = new ArrayList<>();
        for (Activite act : activites) {
            LocalDate d = act.getDateDebut().isBefore(debut) ? debut : act.getDateDebut();
            LocalDate f = act.getDateFin().isAfter(fin) ? fin : act.getDateFin();
            LocalDate cur = d;
            while (!cur.isAfter(f)) {
                joursDetails.add(JourOccupationDTO.builder()
                        .date(cur).activiteTitre(act.getTitre()).build());
                cur = cur.plusDays(1);
            }
        }

        List<ActiviteResumeDTO> activitesResume = activites.stream()
                .sorted(Comparator.comparing(Activite::getDateDebut))
                .map(this::convertToActiviteResume)
                .toList();

        LocalDate dateReference = calculerDateReference(debut, fin);
        String statut = determinerStatut(agent, activitesAgent, dateReference);
        String[] tacheEtPeriode = calculerTacheEtPeriode(activitesAgent, statut, dateReference);
        String tacheEnCours = tacheEtPeriode[0];
        String periodeActuelle = tacheEtPeriode[1];

        int[] compteurs = compterMissionsParType(activitesAgent);
        int[] joursParType = calculerJoursParType(activitesAgent, debut, fin);

        int missionsProgramme = compterMissionsProgramme(activitesAgent);
        int missionsTotal = compteurs[0] + compteurs[1];

        double tauxResident = joursOuvrables > 0
                ? Math.round((joursParType[0] * 100.0 / joursOuvrables) * 100.0) / 100.0
                : 0;
        double tauxNonResident = joursOuvrables > 0
                ? Math.round((joursParType[1] * 100.0 / joursOuvrables) * 100.0) / 100.0
                : 0;

        return StatistiquesAgentDTO.builder()
                .agentId(agent.getId())
                .nom(agent.getNom())
                .prenom(agent.getPrenom())
                .nomComplet(agent.getNomComplet())
                .poste(agent.getPoste())
                .unite(agent.getUnite())
                .actif(agent.getActif())
                .nombreActivites(missionsTotal)
                .joursMission(joursMission)
                .joursOuvrables(joursOuvrables)
                .tauxOccupation(tauxOccupation)
                .statut(statut)
                .tacheEnCours(tacheEnCours)
                .periodeActuelle(periodeActuelle)
                .joursDetails(joursDetails)
                .activites(activitesResume)
                .missionsResident(compteurs[0])
                .missionsNonResident(compteurs[1])
                .missionsTotal(missionsTotal)
                .missionsProgramme(missionsProgramme)
                .joursResident(joursParType[0])
                .joursNonResident(joursParType[1])
                .tauxResident(tauxResident)
                .tauxNonResident(tauxNonResident)
                .build();
    }

    // ============================================================
    // RÉPARTITION PAR SOURCE DE FINANCEMENT
    // ============================================================

    private List<FinancementStatDTO> calculerRepartitionFinancement(List<Activite> activites) {
        Map<String, List<Activite>> parSource = new LinkedHashMap<>();

        for (Activite act : activites) {
            if (!aDesDatesValides(act)) continue;
            String source = act.getSourceFinancement();
            if (source == null || source.isBlank()) source = "Non spécifié";
            parSource.computeIfAbsent(source, k -> new ArrayList<>()).add(act);
        }

        List<FinancementStatDTO> result = new ArrayList<>();

        for (Map.Entry<String, List<Activite>> entry : parSource.entrySet()) {
            String source = entry.getKey();
            List<Activite> acts = entry.getValue();

            int total = acts.size();
            int realisees = (int) acts.stream()
                    .filter(a -> a.getStatut() == Activite.StatutActivite.TERMINEE)
                    .count();
            int enCours = (int) acts.stream()
                    .filter(a -> a.getStatut() == Activite.StatutActivite.EN_COURS)
                    .count();
            int planifiees = (int) acts.stream()
                    .filter(a -> a.getStatut() == Activite.StatutActivite.PLANIFIEE)
                    .count();
            int restantes = planifiees + enCours;
            int totalJours = acts.stream()
                    .mapToInt(a -> a.getNombreJours() != null ? a.getNombreJours() : 0)
                    .sum();
            double tauxRealisation = total > 0
                    ? Math.round((realisees * 100.0 / total) * 100.0) / 100.0
                    : 0;

            result.add(FinancementStatDTO.builder()
                    .source(source)
                    .totalActivites(total)
                    .activitesRealisees(realisees)
                    .activitesEnCours(enCours)
                    .activitesPlanifiees(planifiees)
                    .activitesRestantes(restantes)
                    .totalJours(totalJours)
                    .tauxRealisation(tauxRealisation)
                    .build());
        }

        result.sort(Comparator.comparingInt(FinancementStatDTO::getTotalActivites).reversed());
        return result;
    }
}