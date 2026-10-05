import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';  // ✅ AJOUT RouterLink
import { AuthService } from '../../../core/services/auth.service';
import { PermissionService } from '../../../core/services/permission.service';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterLink],  // ✅ AJOUT RouterLink
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.css']
})
export class LoginComponent {
    username = '';
    password = '';
    isLoading = false;
    errorMessage = '';
    showPassword = false;
    currentYear = new Date().getFullYear();

    constructor(
        private authService: AuthService,
        private router: Router,
        private route: ActivatedRoute,
        private permissionService: PermissionService,   // ✅ NOUVEAU
    ) { }

    onSubmit(): void {
        if (!this.username || !this.password) {
            this.errorMessage = 'Veuillez remplir tous les champs';
            return;
        }

        this.isLoading = true;
        this.errorMessage = '';

        this.authService.login({
            username: this.username,
            password: this.password
        }).subscribe({
            next: () => {
                // ✅ NOUVEAU : charger les permissions AVANT la redirection
                this.permissionService.chargerMesPermissions().subscribe({
                    next: () => {
                        const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
                        this.router.navigate([returnUrl]);
                    },
                    error: () => {
                        // En cas d'erreur, on redirige quand même
                        const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
                        this.router.navigate([returnUrl]);
                    }
                });
            },
            error: (err: any) => {
                this.errorMessage = err.error?.message || 'Identifiants incorrects';
                this.isLoading = false;
            }
        });
    }


    togglePassword(): void {
        this.showPassword = !this.showPassword;
    }
}