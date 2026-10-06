import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { PermissionService } from '../../../core/services/permission.service';
import { AuthService } from '../../../core/services/auth.service';
import { HasPermissionDirective } from '../../directives/has-permission.directive';

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [CommonModule, RouterModule, HasPermissionDirective],
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent implements OnInit, OnDestroy {

    isCollapsed = false;
    permissionsChargees = false;
    private refreshSub: Subscription | null = null;

    constructor(
        public permissionService: PermissionService,
        private authService: AuthService
    ) { }

    ngOnInit(): void {
        // Charge les permissions au démarrage
        this.permissionService.chargerMesPermissions().subscribe({
            next: () => {
                this.permissionsChargees = true;
            },
            error: (err) => {
                console.error('Erreur chargement permissions', err);
                this.permissionsChargees = true;
            }
        });

        // ✅ Rafraîchit le compteur toutes les 60 secondes
        this.refreshSub = interval(60000).subscribe(() => {
            if (this.permissionService.estCharge()) {
                this.permissionService.chargerMesPermissions().subscribe({
                    error: (err) => console.error('Erreur refresh permissions', err)
                });
            }
        });
    }

    ngOnDestroy(): void {
        this.refreshSub?.unsubscribe();
    }

    toggleCollapse(): void {
        this.isCollapsed = !this.isCollapsed;
    }

    logout(): void {
        this.authService.logout();
    }

    // ============================================================
    // ✅ NOUVEAU : Badge validation
    // ============================================================

    /** Retourne true si l'utilisateur est validateur (N1, N2 ou N3). */
    peutVoirValidation(): boolean {
        return this.permissionService.estValidateur();
    }

    /** Nombre d'activités à valider pour le niveau du validateur. */
    get badgeValidation(): number {
        return this.permissionService.getActivitesAValider();
    }

    /** Libellé du niveau (N1, N2, N3, Super Admin). */
    get niveauLabel(): string {
        const n = this.permissionService.getNiveauValidation();
        if (n === -1) return 'Super Admin';
        if (n === 1) return 'Niveau 1';
        if (n === 2) return 'Niveau 2';
        if (n === 3) return 'Niveau 3';
        return '';
    }

    /** Affichage du nom d'utilisateur. */
    get username(): string {
        return this.authService.getCurrentUser()?.username || 'Utilisateur';
    }
}