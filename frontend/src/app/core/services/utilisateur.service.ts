import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Utilisateur {
    id?: string;
    username: string;
    email?: string;
    profilId?: string;
    profilCode?: string;
    profilLibelle?: string;
    agentId?: string;
    agentNom?: string;
    actif?: boolean;
    derniereConnexion?: string;
    createdAt?: string;
}

export interface CreateUtilisateurRequest {
    username: string;
    password: string;
    email?: string;
    profilId: string;
    agentId: string;
}

export interface UpdateUtilisateurRequest {
    email?: string;
    password?: string;
    profilId?: string;
    agentId?: string;
    actif?: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class UtilisateurService {
    private apiUrl = `${environment.apiUrl}/utilisateurs`;

    constructor(private http: HttpClient) { }

    getAllUtilisateurs(): Observable<Utilisateur[]> {
        return this.http.get<Utilisateur[]>(this.apiUrl);
    }

    getUtilisateurById(id: string): Observable<Utilisateur> {
        return this.http.get<Utilisateur>(`${this.apiUrl}/${id}`);
    }

    createUtilisateur(request: CreateUtilisateurRequest): Observable<Utilisateur> {
        return this.http.post<Utilisateur>(this.apiUrl, request);
    }

    updateUtilisateur(id: string, request: UpdateUtilisateurRequest): Observable<Utilisateur> {
        return this.http.put<Utilisateur>(`${this.apiUrl}/${id}`, request);
    }

    // ✅ NOUVELLE MÉTHODE : Changer le statut (actif/inactif)
    changerStatut(id: string, actif: boolean): Observable<Utilisateur> {
        return this.http.patch<Utilisateur>(`${this.apiUrl}/${id}/statut`, { actif });
    }

    // ✅ NOUVELLE MÉTHODE : Basculer l'état actif/inactif
    toggleActif(id: string): Observable<Utilisateur> {
        return this.http.patch<Utilisateur>(`${this.apiUrl}/${id}/toggle-actif`, null);
    }

    deleteUtilisateur(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}