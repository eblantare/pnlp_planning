import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
    PlanningService,
    Activite,
    ConflitAgent,
    ValidationConflit,
    DecisionValidation
} from '../../../core/services/planning.service';
import { AgentService, Agent } from '../../../core/services/agent.service';
import { PermissionService } from '../../../core/services/permission.service';
import { RefreshService } from '../../../core/services/refresh.service';

interface ConflitEditable {
    affectationId: string;
    agentId: string;
    agentNom: string;
    agentPrenom: string;
    agentPoste?: string;
    typeConflit: string;
    activiteConflit?: string;
    dateDebut?: string;
    dateFin?: string;
    motif?: string;

    // État d'édition
    action: 'EN_ATTENTE' | 'RETIRER' | 'REMPLACER' | 'FORCER';
    agentRemplacantId?: string;
    agentRemplacantNom?: string;   // ✅ AJOUT
}

@Component({
    selector: 'app-validation-conflits',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './validation-conflits.component.html',
    styleUrls: ['./validation-conflits.component.css'],
    encapsulation: ViewEncapsulation.None
})
export class ValidationConflitsComponent implements OnInit {

    activites: Activite[] = [];
    isLoading = false;
    errorMessage = '';
    successMessage = '';

    // Modal de traitement
    showModal = false;
    activiteSelectionnee: Activite | null = null;
    conflitsEditable: ConflitEditable[] = [];
    commentaire = '';
    decisionEnCours: 'VALIDER' | 'RENVOYER_NIVEAU_SUPERIEUR' | 'RENVOYER_PLANIFICATEUR' | null = null;

    // Remplaçants
    remplacantsDisponibles: Agent[] = [];
    isLoadingRemplacants = false;
    indexRemplacantEnCours: number | null = null;

    // Niveau du validateur connecté
    niveauValidation: number | null = null;

    constructor(
        private planningService: PlanningService,
        private agentService: AgentService,
        public permissionService: PermissionService,
        private refreshService: RefreshService
    ) { }

    ngOnInit(): void {
        this.niveauValidation = this.permissionService.getNiveauValidation();
        this.loadActivitesAValider();
    }

    // ============================================================
    // CHARGEMENT
    // ============================================================

    loadActivitesAValider(): void {
        this.isLoading = true;
        this.planningService.getActivitesAValider().subscribe({
            next: (data) => {
                this.activites = data || [];
                this.isLoading = false;
            },
            error: (err) => {
                console.error('Erreur chargement activités à valider', err);
                this.showError('Impossible de charger les activités à valider');
                this.isLoading = false;
            }
        });
    }

    // ============================================================
    // OUVERTURE DU MODAL
    // ============================================================

    ouvrirTraitement(activite: Activite): void {
        this.activiteSelectionnee = activite;
        this.commentaire = '';
        this.decisionEnCours = null;

        this.conflitsEditable = (activite.conflits || []).map(c => ({
            affectationId: c.affectationId!,
            agentId: c.agentId,
            agentNom: c.agentNom,
            agentPrenom: c.agentPrenom,
            agentPoste: c.agentPoste,
            typeConflit: c.typeConflit,
            activiteConflit: c.activiteConflit,
            dateDebut: c.dateDebut,
            dateFin: c.dateFin,
            motif: c.motif,
            action: (c.actionValidation as any) || 'EN_ATTENTE',
            agentRemplacantId: c.agentRemplacantId
        }));

        this.showModal = true;
    }

    fermerModal(): void {
        this.showModal = false;
        this.activiteSelectionnee = null;
        this.conflitsEditable = [];
        this.commentaire = '';
        this.decisionEnCours = null;
        this.indexRemplacantEnCours = null;
        this.remplacantsDisponibles = [];
    }

    // ============================================================
    // ACTIONS SUR LES CONFLITS
    // ============================================================

    setAction(index: number, action: 'RETIRER' | 'REMPLACER' | 'FORCER'): void {
        const c = this.conflitsEditable[index];
        if (!c) return;

        c.action = action;
        if (action !== 'REMPLACER') {
            c.agentRemplacantId = undefined;
        }
    }

    ouvrirSelectionRemplacant(index: number): void {
        const c = this.conflitsEditable[index];
        if (!c) return;

        this.indexRemplacantEnCours = index;
        this.isLoadingRemplacants = true;
        this.remplacantsDisponibles = [];

        const debut = this.activiteSelectionnee?.dateDebut || '';
        const fin = this.activiteSelectionnee?.dateFin || '';

        this.planningService.trouverRemplacants(debut, fin).subscribe({
            next: (agents) => {
                // Exclure les agents déjà en conflit / déjà dans l'activité
                const idsDejaPresents = new Set(this.conflitsEditable.map(x => x.agentId));
                this.remplacantsDisponibles = agents.filter(a =>
                    a.id && !idsDejaPresents.has(a.id)
                );
                this.isLoadingRemplacants = false;
            },
            error: (err) => {
                console.error('Erreur chargement remplaçants', err);
                this.isLoadingRemplacants = false;
            }
        });
    }

    choisirRemplacant(agent: Agent): void {
        if (this.indexRemplacantEnCours === null) return;
        const c = this.conflitsEditable[this.indexRemplacantEnCours];
        if (!c || !agent.id) return;

        c.agentRemplacantId = agent.id;
        c.agentRemplacantNom = `${agent.prenom} ${agent.nom}`;   // ✅ AJOUT
        c.action = 'REMPLACER';
        this.indexRemplacantEnCours = null;
        this.remplacantsDisponibles = [];
    }

    annulerSelectionRemplacant(): void {
        this.indexRemplacantEnCours = null;
        this.remplacantsDisponibles = [];
    }

    // ============================================================
    // ÉTAT DU TRAITEMENT
    // ============================================================

    /** Vrai si tous les conflits ont une action valide. */
    tousConflitsTraites(): boolean {
        if (this.conflitsEditable.length === 0) return true;
        return this.conflitsEditable.every(c =>
            c.action !== 'EN_ATTENTE'
            && (c.action !== 'REMPLACER' || !!c.agentRemplacantId)
        );
    }

    /** Nombre de conflits restants à traiter. */
    getNbConflitsRestants(): number {
        return this.conflitsEditable.filter(c =>
            c.action === 'EN_ATTENTE'
            || (c.action === 'REMPLACER' && !c.agentRemplacantId)
        ).length;
    }

    // ============================================================
    // TRAITEMENT / VALIDATION
    // ============================================================

    enregistrerConflits(): void {
        if (!this.activiteSelectionnee?.id) return;
        if (!this.tousConflitsTraites()) {
            this.showError('Tous les conflits doivent être traités avant d\'enregistrer');
            return;
        }

        const actions: ValidationConflit[] = this.conflitsEditable.map(c => ({
            affectationId: c.affectationId,
            action: c.action as any,
            agentRemplacantId: c.agentRemplacantId
        }));

        this.isLoading = true;
        this.planningService.traiterConflits(this.activiteSelectionnee.id, actions).subscribe({
            next: (updated) => {
                this.showSuccess('Conflits enregistrés');
                this.activiteSelectionnee = updated;
                this.conflitsEditable = (updated.conflits || []).map(c => ({
                    affectationId: c.affectationId!,
                    agentId: c.agentId,
                    agentNom: c.agentNom,
                    agentPrenom: c.agentPrenom,
                    agentPoste: c.agentPoste,
                    typeConflit: c.typeConflit,
                    activiteConflit: c.activiteConflit,
                    dateDebut: c.dateDebut,
                    dateFin: c.dateFin,
                    motif: c.motif,
                    action: (c.actionValidation as any) || 'EN_ATTENTE',
                    agentRemplacantId: c.agentRemplacantId
                }));
                this.isLoading = false;
                this.loadActivitesAValider();
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors de l\'enregistrement');
                this.isLoading = false;
            }
        });
    }

    valider(): void {
        if (!this.activiteSelectionnee?.id) return;
        if (!this.tousConflitsTraites()) {
            this.showError('Veuillez traiter tous les conflits avant de valider');
            return;
        }

        const actions: ValidationConflit[] = this.conflitsEditable.map(c => ({
            affectationId: c.affectationId,
            action: c.action as any,
            agentRemplacantId: c.agentRemplacantId
        }));

        const decision: DecisionValidation = {
            actions,
            decision: 'VALIDER'
        };

        this.isLoading = true;
        this.planningService.valider(this.activiteSelectionnee.id, decision).subscribe({
            next: () => {
                this.showSuccess('Activité validée avec succès');
                this.fermerModal();
                this.loadActivitesAValider();
                this.refreshService.demanderRafraichissement();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors de la validation');
                this.isLoading = false;
            }
        });
    }

    renvoyerNiveauSuperieur(): void {
        if (!this.activiteSelectionnee?.id) return;
        if (!this.tousConflitsTraites()) {
            this.showError('Veuillez traiter tous les conflits avant de renvoyer');
            return;
        }
        if (!confirm('Renvoyer cette activité au niveau supérieur ?')) return;

        const actions: ValidationConflit[] = this.conflitsEditable.map(c => ({
            affectationId: c.affectationId,
            action: c.action as any,
            agentRemplacantId: c.agentRemplacantId
        }));

        const decision: DecisionValidation = {
            actions,
            decision: 'RENVOYER_NIVEAU_SUPERIEUR',
            commentaire: this.commentaire
        };

        this.isLoading = true;
        this.planningService.valider(this.activiteSelectionnee.id, decision).subscribe({
            next: () => {
                this.showSuccess('Activité renvoyée au niveau supérieur');
                this.fermerModal();
                this.loadActivitesAValider();
                this.refreshService.demanderRafraichissement();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors du renvoi');
                this.isLoading = false;
            }
        });
    }

    renvoyerPlanificateur(): void {
        if (!this.activiteSelectionnee?.id) return;
        if (!this.commentaire || this.commentaire.trim().length === 0) {
            this.showError('Un commentaire est obligatoire pour renvoyer au planificateur');
            return;
        }
        if (!confirm('Renvoyer cette activité au planificateur pour correction ?')) return;

        const actions: ValidationConflit[] = this.conflitsEditable.map(c => ({
            affectationId: c.affectationId,
            action: c.action as any,
            agentRemplacantId: c.agentRemplacantId
        }));

        const decision: DecisionValidation = {
            actions,
            decision: 'RENVOYER_PLANIFICATEUR',
            commentaire: this.commentaire
        };

        this.isLoading = true;
        this.planningService.valider(this.activiteSelectionnee.id, decision).subscribe({
            next: () => {
                this.showSuccess('Activité renvoyée au planificateur');
                this.fermerModal();
                this.loadActivitesAValider();
                this.refreshService.demanderRafraichissement();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors du renvoi');
                this.isLoading = false;
            }
        });
    }

    // ============================================================
    // HELPERS
    // ============================================================

    getNiveauLabel(): string {
        const n = this.niveauValidation;
        if (n === -1) return 'Super Admin (override)';
        if (n === 1 || n === 2 || n === 3) return `Validateur Niveau ${n}`;
        return 'Validateur';
    }

    getStatutLabel(statut?: string): string {
        const labels: { [key: string]: string } = {
            'EN_ATTENTE_VALIDATION': 'En attente de validation',
            'RENVOYE_POUR_CORRECTION': 'Renvoyé pour correction',
            'PLANIFIEE': 'Planifiée',
            'BROUILLON': 'Brouillon',
            'EN_COURS': 'En cours',
            'TERMINEE': 'Terminée',
            'ANNULEE': 'Annulée',
            'REPORTEE': 'Reportée'
        };
        return labels[statut || ''] || statut || '-';
    }

    showSuccess(message: string): void {
        this.successMessage = message;
        setTimeout(() => this.successMessage = '', 4000);
    }

    showError(message: string): void {
        this.errorMessage = message;
        setTimeout(() => this.errorMessage = '', 5000);
    }
}