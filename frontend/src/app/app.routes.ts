import { Routes } from '@angular/router';

export const routes: Routes = [
    {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component')
            .then(m => m.DashboardComponent)
    },
    {
        path: 'planning',
        loadComponent: () => import('./features/planning/planning.component')
            .then(m => m.PlanningComponent)
    },
    {
        path: 'parametres',
        loadComponent: () => import('./features/parametres/parametres.component')
            .then(m => m.ParametresComponent)
    },
    {
        path: 'administration',
        loadComponent: () => import('./features/administration/administration.component')
            .then(m => m.AdministrationComponent)
    },
    {
        path: '',
        redirectTo: '/planning',
        pathMatch: 'full'
    },
    {
        path: '**',
        redirectTo: '/planning'
    }
];