import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface MesPermissions {
    menus: string[];              // ['DASHBOARD', 'PLANNING', ...]
    crudPlanning: boolean;
    crudAgents: boolean;
    crudProfils: boolean;
    crudUtilisateurs: boolean;
    profils: string[];
}

@Injectable({
    providedIn: 'root'
})
export class PermissionService {
    private apiUrl = `${environment.apiUrl}/permissions`;

    // Cache local des permissions
    private permissionsSubject = new BehaviorSubject<MesPermissions | null>(null);
    public permissions$ = this.permissionsSubject.asObservable();

    constructor(private http: HttpClient) { }

    /**
     * Charge les permissions depuis le backend et les met en cache.
     */
    chargerMesPermissions(): Observable<MesPermissions> {
        return this.http.get<MesPermissions>(`${this.apiUrl}/moi`)
            .pipe(tap(p => this.permissionsSubject.next(p)));
    }

    /**
     * Vérifie si l'utilisateur a accès à un menu.
     */
    aAccesAuMenu(menu: string): boolean {
        const p = this.permissionsSubject.value;
        return p?.menus?.includes(menu) || false;
    }

    /**
     * Vérifie si l'utilisateur peut créer/modifier/supprimer dans le planning.
     */
    peutCrudPlanning(): boolean {
        return this.permissionsSubject.value?.crudPlanning || false;
    }

    /**
     * Vérifie si l'utilisateur peut gérer les agents.
     */
    peutCrudAgents(): boolean {
        return this.permissionsSubject.value?.crudAgents || false;
    }

    /**
     * Vérifie si l'utilisateur peut gérer les profils.
     */
    peutCrudProfils(): boolean {
        return this.permissionsSubject.value?.crudProfils || false;
    }

    /**
     * Vérifie si l'utilisateur peut gérer les utilisateurs.
     */
    peutCrudUtilisateurs(): boolean {
        return this.permissionsSubject.value?.crudUtilisateurs || false;
    }

    /**
     * ✅ NOUVEAU : vérifie si les permissions ont déjà été chargées.
     */
    estCharge(): boolean {
        return this.permissionsSubject.value !== null;
    }
    /**
     * Vide le cache (appelé au logout).
     */
    vider(): void {
        this.permissionsSubject.next(null);
    }
}