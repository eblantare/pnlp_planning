import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, catchError, of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { PermissionService } from '../services/permission.service';

/**
 * ✅ Guard de permissions basé sur le menu.
 *
 * IMPORTANT : il charge les permissions SI ELLES NE SONT PAS ENCORE en cache,
 * puis fait la vérification. Cela évite la redirection intempestive vers
 * /acces-refuse quand l'utilisateur arrive juste après le login.
 */
export const permissionGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const permissionService = inject(PermissionService);
    const router = inject(Router);

    // 1. Utilisateur non connecté → login
    if (!authService.isAuthenticated()) {
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;
    }

    const menu = route.data?.['menu'];
    if (!menu) return true;

    // 2. Si les permissions sont déjà en cache → vérification synchrone
    if (permissionService.estCharge()) {
        if (permissionService.aAccesAuMenu(menu)) {
            return true;
        }
        console.warn(`⛔ Accès refusé à ${state.url}. Menu requis: ${menu}`);
        router.navigate(['/acces-refuse']);
        return false;
    }

    // 3. Sinon, on charge les permissions PUIS on vérifie (async)
    return permissionService.chargerMesPermissions().pipe(
        map((permissions) => {
            if (permissions.menus?.includes(menu)) {
                return true;
            }
            console.warn(`⛔ Accès refusé à ${state.url}. Menu requis: ${menu}`);
            router.navigate(['/acces-refuse']);
            return false;
        }),
        catchError((err) => {
            console.error('Erreur chargement permissions', err);
            router.navigate(['/acces-refuse']);
            return of(false);
        })
    );
};