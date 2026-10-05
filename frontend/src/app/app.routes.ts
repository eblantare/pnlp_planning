import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';
export const routes: Routes = [
    // ROUTES PUBLIQUES
    {
        path: 'login',
        loadComponent: () => import('./features/auth/login/login.component')
            .then(m => m.LoginComponent)
    },
    // ... (forgot-password, reset-password inchangés) ...

    // ============================================================
    // ROUTES PROTÉGÉES AVEC PERMISSIONS
    // ============================================================
    {
        path: 'dashboard',
        canActivate: [authGuard, permissionGuard],
        data: { menu: 'DASHBOARD' },
        loadComponent: () => import('./features/dashboard/dashboard.component')
            .then(m => m.DashboardComponent)
    },
    {
        path: 'planning',
        canActivate: [authGuard, permissionGuard],
        data: { menu: 'PLANNING' },
        loadComponent: () => import('./features/planning/planning.component')
            .then(m => m.PlanningComponent)
    },
    {
        path: 'statistiques',
        canActivate: [authGuard, permissionGuard],
        data: { menu: 'STATISTIQUES' },
        loadComponent: () => import('./features/statistiques/statistiques.component')
            .then(m => m.StatistiquesComponent)
    },
    {
        path: 'agents',
        canActivate: [authGuard, permissionGuard],
        data: { menu: 'AGENTS' },
        loadComponent: () => import('./features/agents/agents.component')
            .then(m => m.AgentsComponent)
    },
    {
        path: 'utilisateurs',
        canActivate: [authGuard, permissionGuard],
        data: { menu: 'UTILISATEURS' },
        loadComponent: () => import('./features/utilisateurs/utilisateurs.component')
            .then(m => m.UtilisateursComponent)
    },
    {
        path: 'profils',
        canActivate: [authGuard, permissionGuard],
        data: { menu: 'PROFILS' },
        loadComponent: () => import('./features/profils/profils.component')
            .then(m => m.ProfilsComponent)
    },

    // ACCÈS REFUSÉ
    {
        path: 'acces-refuse',
        canActivate: [authGuard],
        loadComponent: () => import('./features/acces-refuse/acces-refuse.component')
            .then(m => m.AccesRefuseComponent)
    },

    // REDIRECTIONS
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    { path: '**', redirectTo: 'login' }
];