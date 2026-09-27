import { Injectable } from '@angular/core';

export interface PasswordValidationResult {
    valid: boolean;
    errors: string[];
    strength: number;  // 0 à 4
}

@Injectable({
    providedIn: 'root'
})
export class PasswordValidatorService {

    validate(password: string): PasswordValidationResult {
        const errors: string[] = [];
        let strength = 0;

        // Longueur minimum
        if (!password || password.length < 8) {
            errors.push('Au moins 8 caractères');
        } else {
            strength++;
        }

        // Chiffre
        if (!/\d/.test(password || '')) {
            errors.push('Au moins 1 chiffre');
        } else {
            strength++;
        }

        // Majuscule
        if (!/[A-Z]/.test(password || '')) {
            errors.push('Au moins 1 lettre majuscule');
        } else {
            strength++;
        }

        // Caractère spécial
        if (!/[!@#$%^&*(),.?":{}|<>_\-+=\[\]\\/~`';]/.test(password || '')) {
            errors.push('Au moins 1 caractère spécial (!@#$%^&*...)');
        } else {
            strength++;
        }

        return {
            valid: errors.length === 0,
            errors,
            strength
        };
    }

    getStrengthLabel(strength: number): string {
        switch (strength) {
            case 0: return 'Très faible';
            case 1: return 'Faible';
            case 2: return 'Moyen';
            case 3: return 'Bon';
            case 4: return 'Excellent';
            default: return '';
        }
    }

    getStrengthColor(strength: number): string {
        switch (strength) {
            case 0: return '#DC2626';
            case 1: return '#EF4444';
            case 2: return '#F59E0B';
            case 3: return '#10B981';
            case 4: return '#059669';
            default: return '#9CA3AF';
        }
    }
}