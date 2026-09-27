import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { PasswordValidatorService, PasswordValidationResult } from '../../core/services/password-validator.service';
import {
    UtilisateurService,
    Utilisateur,
    CreateUtilisateurRequest,
    UpdateUtilisateurRequest
} from '../../core/services/utilisateur.service';
import { ProfilService, Profil } from '../../core/services/profil.service';
import { AgentService, Agent } from '../../core/services/agent.service';
import { ActionButton, ActionButtonsComponent } from '../../shared/components/action-buttons/action-buttons.component';

@Component({
    selector: 'app-utilisateurs',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ButtonModule,
        TooltipModule,
        ActionButtonsComponent
    ],
    templateUrl: './utilisateurs.component.html',
    styleUrls: ['./utilisateurs.component.css']
})
export class UtilisateursComponent implements OnInit {
    utilisateurs: Utilisateur[] = [];
    filteredUtilisateurs: Utilisateur[] = [];
    profils: Profil[] = [];
    agents: Agent[] = [];                // ✅ Contiendra uniquement les agents actifs
    filteredAgents: Agent[] = [];

    // Filtres
    searchTerm = '';
    filterProfil = '';

    // Formulaire
    formData: any = this.getEmptyForm();
    editingUtilisateur: Utilisateur | null = null;
    searchAgent = '';

    // Modals
    showForm = false;
    showDetailModal = false;
    selectedUtilisateur: Utilisateur | null = null;

    // États
    isLoading = false;
    errorMessage = '';
    successMessage = '';

    showPassword = false;

    passwordValidation: PasswordValidationResult = {
        valid: true,
        errors: [],
        strength: 0
    };
    showPasswordRules = false;

    constructor(
        private utilisateurService: UtilisateurService,
        private profilService: ProfilService,
        private agentService: AgentService,
        private passwordValidator: PasswordValidatorService
    ) { }

    ngOnInit(): void {
        this.loadProfils();
        this.loadAgents();
        this.loadUtilisateurs();
    }

    getEmptyForm(): any {
        return {
            username: '',
            password: '',
            email: '',
            profilId: '',
            agentId: '',
            actif: true
        };
    }

    loadProfils(): void {
        this.profilService.getProfilsActifs().subscribe({
            next: (data) => this.profils = data,
            error: (err: any) => console.error('Erreur chargement profils', err)
        });
    }

    // ✅ CORRIGÉ : Ne garder que les agents ACTIFS
    loadAgents(): void {
        this.agentService.getAllAgents().subscribe({
            next: (data) => {
                this.agents = data.filter(a => a.actif === true);
                this.filteredAgents = [...this.agents];
                console.log(`${this.agents.length} agent(s) actif(s) chargé(s) pour le modal utilisateur`);
            },
            error: (err: any) => console.error('Erreur chargement agents', err)
        });
    }

    loadUtilisateurs(): void {
        this.isLoading = true;
        this.utilisateurService.getAllUtilisateurs().subscribe({
            next: (data) => {
                this.utilisateurs = data;
                this.applyFilters();
                this.isLoading = false;
            },
            error: (err: any) => {
                console.error('Erreur chargement utilisateurs', err);
                this.showError('Erreur lors du chargement des utilisateurs');
                this.isLoading = false;
            }
        });
    }

    applyFilters(): void {
        let filtered = [...this.utilisateurs];

        if (this.searchTerm) {
            const term = this.searchTerm.toLowerCase();
            filtered = filtered.filter(u =>
                u.username?.toLowerCase().includes(term) ||
                u.email?.toLowerCase().includes(term) ||
                u.agentNom?.toLowerCase().includes(term)
            );
        }

        if (this.filterProfil) {
            filtered = filtered.filter(u => u.profilId === this.filterProfil);
        }

        this.filteredUtilisateurs = filtered;
    }

    openCreateForm(): void {
        this.editingUtilisateur = null;
        this.formData = this.getEmptyForm();
        this.searchAgent = '';
        this.filteredAgents = [...this.agents];
        this.passwordValidation = { valid: true, errors: [], strength: 0 };
        this.showPassword = false;
        this.showPasswordRules = false;
        this.showForm = true;
    }

    editUtilisateur(user: Utilisateur): void {
        this.editingUtilisateur = user;
        this.formData = {
            username: user.username,
            password: '',
            email: user.email || '',
            profilId: user.profilId || '',
            agentId: user.agentId || '',
            actif: user.actif
        };
        this.passwordValidation = { valid: true, errors: [], strength: 0 };
        this.showPassword = false;
        this.showPasswordRules = false;
        this.showForm = true;
    }

    closeForm(): void {
        this.showForm = false;
        this.editingUtilisateur = null;
        this.formData = this.getEmptyForm();
        this.passwordValidation = { valid: true, errors: [], strength: 0 };
        this.showPassword = false;
        this.showPasswordRules = false;
    }

    onPasswordChange(): void {
        if (!this.formData.password) {
            this.passwordValidation = { valid: true, errors: [], strength: 0 };
            return;
        }
        this.passwordValidation = this.passwordValidator.validate(this.formData.password);
    }

    getPasswordStrengthLabel(): string {
        return this.passwordValidator.getStrengthLabel(this.passwordValidation.strength);
    }

    getPasswordStrengthColor(): string {
        return this.passwordValidator.getStrengthColor(this.passwordValidation.strength);
    }

    togglePasswordVisibility(): void {
        this.showPassword = !this.showPassword;
    }

    saveUtilisateur(): void {
        if (!this.formData.username || !this.formData.profilId) {
            this.showError('Le nom d\'utilisateur et le profil sont obligatoires');
            return;
        }

        if (!this.formData.agentId) {
            this.showError('Veuillez sélectionner un agent associé');
            return;
        }

        if (!this.editingUtilisateur && !this.formData.password) {
            this.showError('Le mot de passe est obligatoire pour un nouvel utilisateur');
            return;
        }

        if (this.formData.password) {
            const validation = this.passwordValidator.validate(this.formData.password);
            if (!validation.valid) {
                this.showError('Mot de passe invalide : ' + validation.errors.join(', '));
                return;
            }
        }

        this.isLoading = true;

        if (this.editingUtilisateur?.id) {
            const request: UpdateUtilisateurRequest = {
                email: this.formData.email || undefined,
                password: this.formData.password || undefined,
                profilId: this.formData.profilId,
                agentId: this.formData.agentId,
                actif: this.formData.actif
            };
            this.utilisateurService.updateUtilisateur(this.editingUtilisateur.id, request).subscribe({
                next: () => {
                    this.showSuccess('Utilisateur modifié');
                    this.closeForm();
                    this.loadUtilisateurs();
                },
                error: (err: any) => {
                    this.showError(err.error?.message || 'Erreur lors de la modification');
                    this.isLoading = false;
                }
            });
        } else {
            const request: CreateUtilisateurRequest = {
                username: this.formData.username,
                password: this.formData.password,
                email: this.formData.email || undefined,
                profilId: this.formData.profilId,
                agentId: this.formData.agentId
            };
            this.utilisateurService.createUtilisateur(request).subscribe({
                next: () => {
                    this.showSuccess('Utilisateur créé');
                    this.closeForm();
                    this.loadUtilisateurs();
                },
                error: (err: any) => {
                    this.showError(err.error?.message || 'Erreur lors de la création');
                    this.isLoading = false;
                }
            });
        }
    }

    deleteUtilisateur(user: Utilisateur): void {
        if (!user.id) return;

        const msg = `⚠️ ATTENTION : Supprimer DÉFINITIVEMENT l'utilisateur "${user.username}" ?\n\nCette action est irréversible.`;
        if (!confirm(msg)) return;

        this.isLoading = true;
        this.utilisateurService.deleteUtilisateur(user.id).subscribe({
            next: () => {
                this.showSuccess('Utilisateur supprimé définitivement');
                this.loadUtilisateurs();
            },
            error: (err: any) => {
                this.showError(err.error?.message || 'Erreur lors de la suppression');
                this.isLoading = false;
            }
        });
    }

    viewUtilisateur(user: Utilisateur): void {
        this.selectedUtilisateur = user;
        this.showDetailModal = true;
    }

    closeDetailModal(): void {
        this.showDetailModal = false;
        this.selectedUtilisateur = null;
    }

    filterAgentsList(): void {
        if (!this.searchAgent) {
            this.filteredAgents = [...this.agents];
            return;
        }
        const term = this.searchAgent.toLowerCase();
        this.filteredAgents = this.agents.filter(a =>
            a.nom?.toLowerCase().includes(term) ||
            a.prenom?.toLowerCase().includes(term) ||
            a.poste?.toLowerCase().includes(term)
        );
    }

    isAgentDisponible(agentId: string): boolean {
        if (this.editingUtilisateur && this.editingUtilisateur.agentId === agentId) {
            return true;
        }
        return !this.utilisateurs.some(u => u.agentId === agentId);
    }

    getProfilBadgeClass(code?: string): string {
        const classes: { [key: string]: string } = {
            'SUPER_ADMIN': 'badge-super-admin',
            'ADMIN': 'badge-admin',
            'COORDINATEUR': 'badge-coordinateur',
            'SUPERVISEUR': 'badge-superviseur',
            'AGENT': 'badge-agent',
            'LECTEUR': 'badge-lecteur'
        };
        return classes[code || ''] || 'badge-agent';
    }

    countActifs(): number {
        return this.utilisateurs.filter(u => u.actif).length;
    }

    showSuccess(message: string): void {
        this.successMessage = message;
        setTimeout(() => this.successMessage = '', 3000);
    }

    showError(message: string): void {
        this.errorMessage = message;
        setTimeout(() => this.errorMessage = '', 5000);
    }

    getActionsFor(user: Utilisateur): ActionButton[] {
        return [
            {
                id: 'view',
                icon: 'pi pi-eye',
                label: 'Voir détails',
                severity: 'info'
            },
            {
                id: 'toggle',
                icon: user.actif ? 'pi pi-lock' : 'pi pi-lock-open',
                label: user.actif ? 'Désactiver' : 'Activer',
                severity: user.actif ? 'danger' : 'success'
            },
            {
                id: 'edit',
                icon: 'pi pi-pencil',
                label: 'Modifier',
                severity: 'warning'
            },
            {
                id: 'delete',
                icon: 'pi pi-trash',
                label: 'Supprimer définitivement',
                severity: 'danger'
            }
        ];
    }

    onAction(actionId: string, user: Utilisateur): void {
        switch (actionId) {
            case 'view':   this.viewUtilisateur(user); break;
            case 'edit':   this.editUtilisateur(user); break;
            case 'toggle': this.toggleActif(user); break;
            case 'delete': this.deleteUtilisateur(user); break;
        }
    }

    toggleActif(user: Utilisateur): void {
        if (!user.id) return;

        const action = user.actif ? 'désactiver' : 'activer';
        if (!confirm(`Voulez-vous ${action} l'utilisateur "${user.username}" ?`)) return;

        this.isLoading = true;
        this.utilisateurService.changerStatut(user.id, !user.actif).subscribe({
            next: () => {
                this.showSuccess(`Utilisateur ${user.actif ? 'désactivé' : 'activé'} avec succès`);
                this.loadUtilisateurs();
            },
            error: (err: any) => {
                this.showError(err.error?.message || `Erreur lors de la ${action}`);
                this.isLoading = false;
            }
        });
    }
}