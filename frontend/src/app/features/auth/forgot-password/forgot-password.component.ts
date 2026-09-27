import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PasswordResetService } from '../../../core/services/password-reset.service';

@Component({
    selector: 'app-forgot-password',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterLink],
    templateUrl: './forgot-password.component.html',
    styleUrls: ['./forgot-password.component.css']
})
export class ForgotPasswordComponent {
    email = '';
    isLoading = false;
    successMessage = '';
    errorMessage = '';
    emailSent = false;

    // ✅ AJOUT : Année courante pour le footer
    currentYear = new Date().getFullYear();

    constructor(
        private passwordResetService: PasswordResetService,
        private router: Router
    ) { }

    onSubmit(): void {
        if (!this.email) {
            this.errorMessage = 'Veuillez saisir votre email';
            return;
        }

        this.isLoading = true;
        this.errorMessage = '';

        this.passwordResetService.demanderReinitialisation(this.email).subscribe({
            next: (response) => {
                this.successMessage = response.message;
                this.emailSent = true;
                this.isLoading = false;
            },
            error: (err: any) => {
                this.errorMessage = err.error?.message || 'Une erreur est survenue';
                this.isLoading = false;
            }
        });
    }
}