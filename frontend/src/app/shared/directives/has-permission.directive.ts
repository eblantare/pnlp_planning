import { Directive, Input, TemplateRef, ViewContainerRef, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { PermissionService } from '../../core/services/permission.service';

/**
 * ✅ Directive structurelle *appHasPermission
 *
 * Utilisation :
 *   <button *appHasPermission="'crudPlanning'">Créer</button>
 *   <a *appHasPermission="'menu:PROFILS'">Profils</a>
 */
@Directive({
    selector: '[appHasPermission]',
    standalone: true
})
export class HasPermissionDirective implements OnInit, OnDestroy {
    @Input('appHasPermission') permission: string = '';

    private sub?: Subscription;
    private hasView = false;

    constructor(
        private templateRef: TemplateRef<any>,
        private viewContainer: ViewContainerRef,
        private permissionService: PermissionService
    ) { }

    ngOnInit(): void {
        this.sub = this.permissionService.permissions$.subscribe(() => {
            this.updateView();
        });
    }

    ngOnDestroy(): void {
        this.sub?.unsubscribe();
    }

    private updateView(): void {
        const autorise = this.checkPermission();

        if (autorise && !this.hasView) {
            this.viewContainer.createEmbeddedView(this.templateRef);
            this.hasView = true;
        } else if (!autorise && this.hasView) {
            this.viewContainer.clear();
            this.hasView = false;
        }
    }

    private checkPermission(): boolean {
        if (!this.permission) return true;

        // Format : "menu:XXX" → vérifie l'accès au menu
        if (this.permission.startsWith('menu:')) {
            const menu = this.permission.substring(5);
            return this.permissionService.aAccesAuMenu(menu);
        }

        // Sinon, on traite comme un flag CRUD
        switch (this.permission) {
            case 'crudPlanning': return this.permissionService.peutCrudPlanning();
            case 'crudAgents': return this.permissionService.peutCrudAgents();
            case 'crudProfils': return this.permissionService.peutCrudProfils();
            case 'crudUtilisateurs': return this.permissionService.peutCrudUtilisateurs();
            default: return false;
        }
    }
}