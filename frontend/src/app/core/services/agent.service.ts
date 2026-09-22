// src/app/core/services/agent.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Agent {
    id?: string;
    nom: string;
    prenom: string;
    email?: string;
    telephone?: string;
    poste?: string;
    directionId?: string;
    directionNom?: string;
    districtId?: string;
    districtNom?: string;
    actif?: boolean;
    createdAt?: string;
    updatedAt?: string;
}

export interface CreateAgentRequest {
    nom: string;
    prenom: string;
    email?: string;
    telephone?: string;
    poste?: string;
    directionId?: string;
    districtId?: string;
}

@Injectable({
    providedIn: 'root'
})
export class AgentService {
    private apiUrl = `${environment.apiUrl}/agents`;

    constructor(private http: HttpClient) { }

    getAllAgents(): Observable<Agent[]> {
        return this.http.get<Agent[]>(this.apiUrl);
    }

    getAgentById(id: string): Observable<Agent> {
        return this.http.get<Agent>(`${this.apiUrl}/${id}`);
    }

    createAgent(agent: CreateAgentRequest): Observable<Agent> {
        return this.http.post<Agent>(this.apiUrl, agent);
    }

    updateAgent(id: string, agent: Partial<Agent>): Observable<Agent> {
        return this.http.put<Agent>(`${this.apiUrl}/${id}`, agent);
    }

    deleteAgent(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }

    getAgentsByDirection(directionId: string): Observable<Agent[]> {
        const params = new HttpParams().set('directionId', directionId);
        return this.http.get<Agent[]>(this.apiUrl, { params });
    }

    getAgentsDisponibles(debut: string, fin: string): Observable<Agent[]> {
        const params = new HttpParams()
            .set('debut', debut)
            .set('fin', fin);
        return this.http.get<Agent[]>(`${this.apiUrl}/disponibles`, { params });
    }
}