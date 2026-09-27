import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
    // Route publique
    {
        path: 'login',
        loadComponent: () => import('./features/auth/login/login.component')
            .then(m => m.LoginComponent)
    },
    // ✅ NOUVELLES ROUTES
    {
        path: 'forgot-password',
        loadComponent: () => import('./features/auth/forgot-password/forgot-password.component')
            .then(m => m.ForgotPasswordComponent)
    },
    {
        path: 'reset-password',
        loadComponent: () => import('./features/auth/reset-password/reset-password.component')
            .then(m => m.ResetPasswordComponent)
    },
    // Routes protégées
    {
        path: 'dashboard',
        canActivate: [authGuard],
        loadComponent: () => import('./features/dashboard/dashboard.component')
            .then(m => m.DashboardComponent)
    },
    {
        path: 'planning',
        canActivate: [authGuard],
        loadComponent: () => import('./features/planning/planning.component')
            .then(m => m.PlanningComponent)
    },
    {
        path: 'agents',
        canActivate: [authGuard],
        loadComponent: () => import('./features/agents/agents.component')
            .then(m => m.AgentsComponent)
    },
    {
        path: 'utilisateurs',
        canActivate: [authGuard],
        loadComponent: () => import('./features/utilisateurs/utilisateurs.component')
            .then(m => m.UtilisateursComponent)
    },
    {
        path: 'profils',
        canActivate: [authGuard],
        loadComponent: () => import('./features/profils/profils.component')
            .then(m => m.ProfilsComponent)
    },
    {
        path: 'statistiques',
        canActivate: [authGuard],
        loadComponent: () => import('./features/statistiques/statistiques.component')
            .then(m => m.StatistiquesComponent)
    },

    // Redirections
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    { path: '**', redirectTo: 'login' }
];