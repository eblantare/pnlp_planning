import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { ProfilService, Profil } from '../../core/services/profil.service';
import { ActionButton, ActionButtonsComponent } from '../../shared/components/action-buttons/action-buttons.component';

@Component({
    selector: 'app-profils',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ButtonModule,
        TooltipModule,
        InputTextModule,
        InputNumberModule,
        InputTextareaModule,
        ActionButtonsComponent
    ],
    templateUrl: './profils.component.html',
    styleUrls: ['./profils.component.css']
})
export class ProfilsComponent implements OnInit {
    profils: Profil[] = [];
    filteredProfils: Profil[] = [];
    searchTerm = '';

    // Formulaire
    nouveauProfil: Profil = this.getEmptyProfil();
    editingProfil: Profil | null = null;

    // Modal détail
    showDetailModal = false;
    selectedProfil: Profil | null = null;

    // Modals
    showForm = false;

    // États
    isLoading = false;
    errorMessage = '';
    successMessage = '';

    constructor(private profilService: ProfilService) { }

    ngOnInit(): void {
        this.loadProfils();
    }

    getEmptyProfil(): Profil {
        return {
            code: '',
            libelle: '',
            description: '',
            niveau: 10,
            actif: false   // ✅ Cohérent avec le backend
        };
    }

    loadProfils(): void {
        this.isLoading = true;
        this.profilService.getAllProfils().subscribe({
            next: (data) => {
                this.profils = data.sort((a, b) => (b.niveau || 0) - (a.niveau || 0));
                this.applyFilters();
                this.isLoading = false;
            },
            error: (err) => {
                console.error('Erreur chargement profils', err);
                this.showError('Erreur lors du chargement des profils');
                this.isLoading = false;
            }
        });
    }

    applyFilters(): void {
        if (!this.searchTerm) {
            this.filteredProfils = [...this.profils];
            return;
        }
        const term = this.searchTerm.toLowerCase();
        this.filteredProfils = this.profils.filter(p =>
            p.code?.toLowerCase().includes(term) ||
            p.libelle?.toLowerCase().includes(term) ||
            p.description?.toLowerCase().includes(term)
        );
    }

    openCreateForm(): void {
        this.editingProfil = null;
        this.nouveauProfil = this.getEmptyProfil();
        this.showForm = true;
    }

    editProfil(profil: Profil): void {
        this.editingProfil = profil;
        this.nouveauProfil = { ...profil };
        this.showForm = true;
    }

    closeForm(): void {
        this.showForm = false;
        this.editingProfil = null;
        this.nouveauProfil = this.getEmptyProfil();
    }

    viewProfil(profil: Profil): void {
        this.selectedProfil = profil;
        this.showDetailModal = true;
    }

    closeDetailModal(): void {
        this.showDetailModal = false;
        this.selectedProfil = null;
    }

    saveProfil(): void {
        if (!this.nouveauProfil.code || !this.nouveauProfil.libelle) {
            this.showError('Le code et le libellé sont obligatoires');
            return;
        }

        this.isLoading = true;

        if (this.editingProfil?.id) {
            this.profilService.updateProfil(this.editingProfil.id, this.nouveauProfil).subscribe({
                next: () => {
                    this.showSuccess('Profil modifié avec succès');
                    this.closeForm();
                    this.loadProfils();
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur lors de la modification');
                    this.isLoading = false;
                }
            });
        } else {
            this.profilService.createProfil(this.nouveauProfil).subscribe({
                next: () => {
                    this.showSuccess('Profil créé. Il est inactif par défaut — activez-le pour l\'utiliser.');
                    this.closeForm();
                    this.loadProfils();
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur lors de la création');
                    this.isLoading = false;
                }
            });
        }
    }

    toggleActif(profil: Profil): void {
        if (!profil.id) return;

        const action = profil.actif ? 'désactiver' : 'activer';
        if (!confirm(`Voulez-vous ${action} le profil "${profil.libelle}" ?`)) return;

        this.isLoading = true;
        this.profilService.toggleActif(profil.id).subscribe({
            next: (updated) => {
                this.showSuccess(`Profil ${updated.actif ? 'activé' : 'désactivé'}`);
                this.loadProfils();
            },
            error: (err) => {
                this.showError('Erreur lors du changement d\'état');
                this.isLoading = false;
            }
        });
    }

    deleteProfil(profil: Profil): void {
        if (!profil.id) return;

        const msg = `⚠️ ATTENTION : Supprimer DÉFINITIVEMENT le profil "${profil.libelle}" ?\n\nCette action est irréversible.`;
        if (!confirm(msg)) return;

        this.isLoading = true;
        this.profilService.deleteProfil(profil.id).subscribe({
            next: () => {
                this.showSuccess('Profil supprimé définitivement');
                this.loadProfils();
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors de la suppression');
                this.isLoading = false;
            }
        });
    }

    countActifs(): number {
        return this.profils.filter(p => p.actif).length;
    }

    showSuccess(message: string): void {
        this.successMessage = message;
        setTimeout(() => this.successMessage = '', 4000);
    }

    showError(message: string): void {
        this.errorMessage = message;
        setTimeout(() => this.errorMessage = '', 5000);
    }

    getActionsFor(profil: Profil): ActionButton[] {
        return [
            {
                id: 'view',
                icon: 'pi pi-eye',
                label: 'Voir détails',
                severity: 'info'
            },
            {
                id: 'edit',
                icon: 'pi pi-pencil',
                label: 'Modifier',
                severity: 'warning'
            },
            {
                id: profil.actif ? 'deactivate' : 'activate',
                icon: profil.actif ? 'pi pi-lock' : 'pi pi-lock-open',
                label: profil.actif ? 'Désactiver' : 'Activer',
                severity: profil.actif ? 'danger' : 'success'
            },
            {
                id: 'delete',
                icon: 'pi pi-trash',
                label: 'Supprimer définitivement',
                severity: 'danger'
            }
        ];
    }

    onAction(actionId: string, profil: Profil): void {
        console.log('📥 onAction reçu:', actionId, 'profil:', profil.libelle);   // ✅ Debug

        switch (actionId) {
            case 'view':
                this.viewProfil(profil);
                break;
            case 'edit':
                this.editProfil(profil);
                break;
            case 'activate':
            case 'deactivate':
                this.toggleActif(profil);
                break;
            case 'delete':
                this.deleteProfil(profil);
                break;
        }
    }
}