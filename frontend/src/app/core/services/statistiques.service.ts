import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

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
    statut: 'EN_MISSION' | 'DISPONIBLE' | 'INACTIF';
    activiteEnCours?: string;
    periodeActuelle?: string;
}

export interface StatistiquesGlobales {
    annee: number;
    mois: number;
    moisLibelle: string;
    totalAgents: number;
    agentsActifs: number;
    agentsEnMission: number;
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
}

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
}