import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Agent } from './agent.service';

export interface Activite {
    id?: string;
    titre: string;
    commentaires?: string;
    // ✅ Dates OPTIONNELLES pour permettre les brouillons
    dateDebut?: string;
    dateFin?: string;
    nombreJours?: number;
    lieu?: string;
    sourceFinancement?: string;
    statut?: string;
    agentIds?: string[];
    agentNoms?: string[];
    agentZonesList?: (string | null)[];
    agentIdsForces?: string[];
    agentZones?: { [agentId: string]: string };
    createdById?: string;
    createdByNom?: string;
}

export interface ConflitAgent {
    agentId: string;
    agentNom: string;
    agentPrenom: string;
    agentPoste?: string;
    typeConflit: 'AFFECTATION_SIMULTANEE' | 'INDISPONIBILITE';
    activiteConflit?: string;
    dateDebut?: string;
    dateFin?: string;
    motif?: string;
}

export interface ReponseCreationActivite {
    succes: boolean;
    activite?: Activite;
    conflits: ConflitAgent[];
    message: string;
}

export interface Disponibilite {
    agentId: string;
    agentNom: string;
    disponible: boolean;
    indisponibilites: any[];
    affectations: any[];
}

export interface PlanningMensuel {
    mois: string;
    activites: Activite[];
    planningParAgent: { [key: string]: Activite[] };
}

@Injectable({
    providedIn: 'root'
})
export class PlanningService {
    private apiUrl = `${environment.apiUrl}/planning`;

    constructor(private http: HttpClient) { }

    creerActivite(activite: Activite): Observable<ReponseCreationActivite> {
        return this.http.post<ReponseCreationActivite>(`${this.apiUrl}/activites`, activite);
    }

    updateActivite(id: string, activite: Activite): Observable<ReponseCreationActivite> {
        return this.http.put<ReponseCreationActivite>(`${this.apiUrl}/activites/${id}`, activite);
    }

    changerStatut(id: string, statut: string): Observable<Activite> {
        const params = new HttpParams().set('statut', statut);
        return this.http.patch<Activite>(`${this.apiUrl}/activites/${id}/statut`, null, { params });
    }

    supprimerActivite(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/activites/${id}`);
    }

    getActiviteById(id: string): Observable<Activite> {
        return this.http.get<Activite>(`${this.apiUrl}/activites/${id}`);
    }

    verifierDisponibilite(agentId: string, debut: string, fin: string): Observable<Disponibilite> {
        const params = new HttpParams()
            .set('agentId', agentId)
            .set('debut', debut)
            .set('fin', fin);
        return this.http.get<Disponibilite>(`${this.apiUrl}/disponibilite`, { params });
    }

    getJoursMissionMensuel(annee: number, mois: number): Observable<{ [key: string]: number }> {
        const params = new HttpParams()
            .set('annee', annee.toString())
            .set('mois', mois.toString());
        return this.http.get<{ [key: string]: number }>(`${this.apiUrl}/jours-mission/mensuel`, { params });
    }

    getJoursMissionAnnuel(annee: number): Observable<{ [key: string]: number }> {
        const params = new HttpParams().set('annee', annee.toString());
        return this.http.get<{ [key: string]: number }>(`${this.apiUrl}/jours-mission/annuel`, { params });
    }

    getPlanningMensuel(annee: number, mois: number): Observable<PlanningMensuel> {
        const params = new HttpParams()
            .set('annee', annee.toString())
            .set('mois', mois.toString());
        return this.http.get<PlanningMensuel>(`${this.apiUrl}/mensuel`, { params });
    }

    trouverRemplacants(debut: string, fin: string): Observable<Agent[]> {
        const params = new HttpParams()
            .set('debut', debut)
            .set('fin', fin);
        return this.http.get<Agent[]>(`${this.apiUrl}/remplacants`, { params });
    }
}