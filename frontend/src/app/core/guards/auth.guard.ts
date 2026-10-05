import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { PermissionService } from '../services/permission.service';
/**
 * ✅ Guard d'authentification de base.
 * Vérifie simplement que l'utilisateur est connecté.
 */
export const authGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.isAuthenticated()) {
        return true;
    }

    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
};

/**
 * ✅ NOUVEAU : Guard de rôles.
 * Vérifie que l'utilisateur possède AU MOINS UN des rôles requis.
 *
 * Utilisation dans les routes :
 *   { path: 'admin', component: ..., canActivate: [roleGuard], data: { roles: ['ADMIN', 'SUPER_ADMIN'] } }
 *   { path: 'coordinateur', component: ..., canActivate: [roleGuard], data: { roles: ['COORDINATEUR'] } }
 */
export const roleGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    // 1. Vérifier que l'utilisateur est connecté
    if (!authService.isAuthenticated()) {
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;
    }

    // 2. Récupérer les rôles autorisés depuis la route
    const rolesAutorises: string[] = route.data?.['roles'] || [];

    // Si aucun rôle n'est spécifié → autorisé (juste authentifié)
    if (rolesAutorises.length === 0) {
        return true;
    }

    // 3. Vérifier que l'utilisateur a AU MOINS UN des rôles
    if (authService.hasAnyRole(...rolesAutorises)) {
        return true;
    }

    // 4. Sinon → accès refusé
    console.warn(`Accès refusé à ${state.url}. Rôles requis: ${rolesAutorises.join(', ')}. Rôles utilisateur: ${authService.getProfilsCodes().join(', ')}`);
    router.navigate(['/acces-refuse']);
    return false;
};
