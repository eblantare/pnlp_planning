import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Activite {
    id?: string;
    titre: string;
    description?: string;
    dateDebut: string;
    dateFin: string;
    nombreJours?: number;
    lieu?: string;
    sourceFinancement?: string;
    statut?: string;
    commentaires?: string;
    agentIds?: string[];
    agentNoms?: string[];
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

    creerActivite(activite: Activite): Observable<Activite> {
        return this.http.post<Activite>(`${this.apiUrl}/activites`, activite);
    }

    updateActivite(id: string, activite: Activite): Observable<Activite> {
        return this.http.put<Activite>(`${this.apiUrl}/activites/${id}`, activite);
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
}