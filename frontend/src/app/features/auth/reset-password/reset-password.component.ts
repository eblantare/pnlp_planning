import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PasswordResetService } from '../../../core/services/password-reset.service';
import { PasswordValidatorService, PasswordValidationResult } from '../../../core/services/password-validator.service';

@Component({
    selector: 'app-reset-password',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterLink],
    templateUrl: './reset-password.component.html',
    styleUrls: ['./reset-password.component.css']
})
export class ResetPasswordComponent implements OnInit {
    token = '';
    nouveauMotDePasse = '';
    confirmation = '';
    showPassword = false;

    isLoading = false;
    isVerifying = true;
    tokenValide = false;
    errorMessage = '';
    successMessage = '';
    reinitialise = false;

    passwordValidation: PasswordValidationResult = {
        valid: false,
        errors: [],
        strength: 0
    };

    currentYear = new Date().getFullYear();

    constructor(
        private passwordResetService: PasswordResetService,
        private passwordValidator: PasswordValidatorService,
        private route: ActivatedRoute,
        private router: Router
    ) { }

    ngOnInit(): void {
        this.token = this.route.snapshot.queryParams['token'] || '';

        if (!this.token) {
            this.errorMessage = 'Lien de réinitialisation invalide';
            this.isVerifying = false;
            return;
        }

        // Vérifier que le token est valide
        this.passwordResetService.verifierToken(this.token).subscribe({
            next: (response) => {
                this.tokenValide = response.valide;
                this.isVerifying = false;
                if (!this.tokenValide) {
                    this.errorMessage = 'Ce lien a expiré ou a déjà été utilisé';
                }
            },
            error: () => {
                this.isVerifying = false;
                this.errorMessage = 'Erreur lors de la vérification du lien';
            }
        });
    }

    onPasswordChange(): void {
        if (!this.nouveauMotDePasse) {
            this.passwordValidation = { valid: false, errors: [], strength: 0 };
            return;
        }
        this.passwordValidation = this.passwordValidator.validate(this.nouveauMotDePasse);
    }

    getStrengthLabel(): string {
        return this.passwordValidator.getStrengthLabel(this.passwordValidation.strength);
    }

    getStrengthColor(): string {
        return this.passwordValidator.getStrengthColor(this.passwordValidation.strength);
    }

    onSubmit(): void {
        if (!this.passwordValidation.valid) {
            this.errorMessage = 'Le mot de passe ne respecte pas les règles de sécurité';
            return;
        }

        if (this.nouveauMotDePasse !== this.confirmation) {
            this.errorMessage = 'Les mots de passe ne correspondent pas';
            return;
        }

        this.isLoading = true;
        this.errorMessage = '';

        this.passwordResetService.reinitialiserMotDePasse(this.token, this.nouveauMotDePasse).subscribe({
            next: (response) => {
                this.successMessage = response.message;
                this.reinitialise = true;
                this.isLoading = false;

                // Redirection automatique après 3 secondes
                setTimeout(() => this.router.navigate(['/login']), 3000);
            },
            error: (err) => {
                this.errorMessage = err.error?.message || 'Erreur lors de la réinitialisation';
                this.isLoading = false;
            }
        });
    }
}