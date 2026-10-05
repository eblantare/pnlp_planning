import { APP_INITIALIZER, Provider } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { PermissionService } from '../services/permission.service';

/**
 * ✅ Initializer : charge les permissions au démarrage de l'app
 * SI l'utilisateur est déjà connecté (ex: refresh de page avec token en localStorage).
 */
export function chargerPermissionsInitializer(
    authService: AuthService,
    permissionService: PermissionService
) {
    return () => {
        if (!authService.isAuthenticated()) {
            return Promise.resolve();
        }
        return firstValueFrom(permissionService.chargerMesPermissions())
            .catch((err) => {
                console.error('Erreur chargement permissions initial', err);
            });
    };
}

export const PERMISSIONS_INITIALIZER: Provider = {
    provide: APP_INITIALIZER,
    useFactory: chargerPermissionsInitializer,
    deps: [AuthService, PermissionService],
    multi: true
};