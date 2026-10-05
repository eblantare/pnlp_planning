import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface JourOccupation {
    date: string;
    activiteTitre: string;
}

export interface ActiviteResume {
    id: string;
    titre: string;
    dateDebut: string;
    dateFin: string;
    nombreJours: number;
    lieu?: string;
    sourceFinancement?: string;
    statut?: string;
    zone?: string;
    typeLieu?: string;
    auProgramme?: boolean;   // ✅ NOUVEAU
}

export interface StatistiquesAgent {
    agentId: string;
    nom: string;
    prenom: string;
    nomComplet: string;
    poste?: string;
    unite?: string;
    actif: boolean;
    nombreActivites: number;
    joursMission: number;
    joursOuvrables: number;
    tauxOccupation: number;

    // ✅ Statuts possibles (AU_PROGRAMME remplace OCCUPE pour les agents au programme)
    statut: 'EN_MISSION' | 'AU_PROGRAMME' | 'OCCUPE' | 'DISPONIBLE' | 'INACTIF';

    // ✅ NOUVEAU : libellé de la tâche (en cours ou à venir)
    tacheEnCours?: string;
    periodeActuelle?: string;

    joursDetails?: JourOccupation[];
    activites?: ActiviteResume[];

    // Compteurs RÉSIDENT / NON_RÉSIDENT
    missionsResident?: number;
    missionsNonResident?: number;
    missionsTotal?: number;
    missionsProgramme?: number;   // ✅ NOUVEAU

    joursResident?: number;
    joursNonResident?: number;

    tauxResident?: number;
    tauxNonResident?: number;
}

export interface FinancementStat {
    source: string;
    totalActivites: number;
    activitesRealisees: number;
    activitesEnCours: number;
    activitesPlanifiees: number;
    activitesRestantes: number;
    totalJours: number;
    tauxRealisation: number;
}

export interface StatistiquesGlobales {
    annee: number;
    mois: number;
    moisLibelle: string;
    dateDebut?: string;
    dateFin?: string;
    periodeLibelle?: string;
    totalAgents: number;
    agentsActifs: number;
    agentsEnMission: number;
    agentsAuProgramme: number;   // ✅ NOUVEAU
    agentsOccupes: number;
    agentsDisponibles: number;
    agentsInactifs: number;
    totalActivites: number;
    activitesEnCours: number;
    totalJoursMission: number;
    tauxOccupationMoyen: number;
    agents: StatistiquesAgent[];
    topAgents: StatistiquesAgent[];
    repartitionMensuelle: { [mois: string]: number };
    missionsParAgent: { [nom: string]: number };
    repartitionParFinancement?: FinancementStat[];
}

export type ModePeriode = 'SEMAINE' | 'MOIS' | 'ANNEE' | 'INTERVALLE';

@Injectable({
    providedIn: 'root'
})
export class StatistiquesService {
    private apiUrl = `${environment.apiUrl}/statistiques`;

    constructor(private http: HttpClient) { }

    getStatistiquesMensuelles(annee: number, mois: number): Observable<StatistiquesGlobales> {
        const params = new HttpParams()
            .set('annee', annee.toString())
            .set('mois', mois.toString());
        return this.http.get<StatistiquesGlobales>(`${this.apiUrl}/mensuel`, { params });
    }

    getStatistiquesPeriode(debut: string, fin: string): Observable<StatistiquesGlobales> {
        const params = new HttpParams().set('debut', debut).set('fin', fin);
        return this.http.get<StatistiquesGlobales>(`${this.apiUrl}/periode`, { params });
    }

    getHistoriqueAgent(agentId: string, debut: string, fin: string): Observable<StatistiquesAgent> {
        const params = new HttpParams().set('debut', debut).set('fin', fin);
        return this.http.get<StatistiquesAgent>(`${this.apiUrl}/agent/${agentId}/historique`, { params });
    }
}