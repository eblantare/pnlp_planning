import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface MesPermissions {
    menus: string[];
    crudPlanning: boolean;
    crudAgents: boolean;
    crudProfils: boolean;
    crudUtilisateurs: boolean;
    profils: string[];

    // ✅ NOUVEAU : workflow de validation
    peutValiderN1: boolean;
    peutValiderN2: boolean;
    peutValiderN3: boolean;
    niveauValidation: number | null;   // 1, 2, 3, -1 (SUPER_ADMIN), ou null
    activitesAValider: number;         // compteur pour le badge
}

@Injectable({
    providedIn: 'root'
})
export class PermissionService {
    private apiUrl = `${environment.apiUrl}/permissions`;

    private permissionsSubject = new BehaviorSubject<MesPermissions | null>(null);
    public permissions$ = this.permissionsSubject.asObservable();

    constructor(private http: HttpClient) { }

    chargerMesPermissions(): Observable<MesPermissions> {
        return this.http.get<MesPermissions>(`${this.apiUrl}/moi`)
            .pipe(tap(p => this.permissionsSubject.next(p)));
    }

    // ============================================================
    // MENUS
    // ============================================================

    aAccesAuMenu(menu: string): boolean {
        const p = this.permissionsSubject.value;
        return p?.menus?.includes(menu) || false;
    }

    // ============================================================
    // CRUD
    // ============================================================

    peutCrudPlanning(): boolean {
        return this.permissionsSubject.value?.crudPlanning || false;
    }

    peutCrudAgents(): boolean {
        return this.permissionsSubject.value?.crudAgents || false;
    }

    peutCrudProfils(): boolean {
        return this.permissionsSubject.value?.crudProfils || false;
    }

    peutCrudUtilisateurs(): boolean {
        return this.permissionsSubject.value?.crudUtilisateurs || false;
    }

    // ============================================================
    // ✅ NOUVEAU : VALIDATION
    // ============================================================

    peutValiderN1(): boolean {
        return this.permissionsSubject.value?.peutValiderN1 || false;
    }

    peutValiderN2(): boolean {
        return this.permissionsSubject.value?.peutValiderN2 || false;
    }

    peutValiderN3(): boolean {
        return this.permissionsSubject.value?.peutValiderN3 || false;
    }

    /** Retourne true si l'utilisateur est validateur (peu importe le niveau). */
    estValidateur(): boolean {
        return this.peutValiderN1() || this.peutValiderN2() || this.peutValiderN3();
    }

    /** Retourne le niveau du validateur connecté (1, 2, 3, -1 pour SUPER_ADMIN, null sinon). */
    getNiveauValidation(): number | null {
        return this.permissionsSubject.value?.niveauValidation ?? null;
    }

    /** Retourne le nombre d'activités à valider (badge). */
    getActivitesAValider(): number {
        return this.permissionsSubject.value?.activitesAValider || 0;
    }

    // ============================================================
    // ÉTAT
    // ============================================================

    estCharge(): boolean {
        return this.permissionsSubject.value !== null;
    }

    vider(): void {
        this.permissionsSubject.next(null);
    }
}