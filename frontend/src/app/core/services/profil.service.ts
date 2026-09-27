import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Profil {
    id?: string;
    code: string;
    libelle: string;
    description?: string;
    niveau?: number;
    actif?: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class ProfilService {
    private apiUrl = `${environment.apiUrl}/profils`;

    constructor(private http: HttpClient) { }

    getAllProfils(): Observable<Profil[]> {
        return this.http.get<Profil[]>(this.apiUrl);
    }

    getProfilsActifs(): Observable<Profil[]> {
        return this.http.get<Profil[]>(`${this.apiUrl}/actifs`);
    }

    getProfilById(id: string): Observable<Profil> {
        return this.http.get<Profil>(`${this.apiUrl}/${id}`);
    }

    createProfil(profil: Profil): Observable<Profil> {
        return this.http.post<Profil>(this.apiUrl, profil);
    }

    updateProfil(id: string, profil: Profil): Observable<Profil> {
        return this.http.put<Profil>(`${this.apiUrl}/${id}`, profil);
    }

    /**
     * ✅ NOUVEAU : Basculer actif/inactif
     */
    toggleActif(id: string): Observable<Profil> {
        return this.http.patch<Profil>(`${this.apiUrl}/${id}/toggle-actif`, null);
    }

    /**
     * ✅ MODIFIÉ : Suppression réelle
     */
    deleteProfil(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}