import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PlanningService, Activite, PlanningMensuel } from '../../core/services/planning.service';
import { AgentService, Agent } from '../../core/services/agent.service';

@Component({
    selector: 'app-planning',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './planning.component.html',
    styleUrls: ['./planning.component.css']
})
export class PlanningComponent implements OnInit {
    activites: Activite[] = [];
    filteredActivites: Activite[] = [];
    agents: Agent[] = [];
    filteredAgents: Agent[] = [];
    planningMensuel: PlanningMensuel | null = null;

    annee = new Date().getFullYear();
    mois = new Date().getMonth() + 1;

    // Filtres
    searchTerm = '';
    filterStatut = '';
    searchAgent = '';

    // Formulaire
    nouvelleActivite: Activite = this.getEmptyActivite();
    editingActivite: Activite | null = null;

    // Modals
    showForm = false;
    showDetailModal = false;
    selectedActivite: Activite | null = null;

    // États
    isLoading = false;
    errorMessage = '';
    successMessage = '';

    constructor(
        private planningService: PlanningService,
        private agentService: AgentService
    ) { }

    ngOnInit(): void {
        this.loadAgents();
        this.loadPlanning();
    }

    getEmptyActivite(): Activite {
        return {
            titre: '',
            description: '',
            dateDebut: '',
            dateFin: '',
            lieu: '',
            sourceFinancement: '',
            statut: 'PLANIFIEE',
            agentIds: []
        };
    }

    loadAgents(): void {
        this.agentService.getAllAgents().subscribe({
            next: (data) => {
                this.agents = data;
                this.filteredAgents = data;
            },
            error: (err) => console.error('Erreur chargement agents', err)
        });
    }

    loadPlanning(): void {
        this.isLoading = true;
        this.planningService.getPlanningMensuel(this.annee, this.mois).subscribe({
            next: (data) => {
                this.planningMensuel = data;
                // Trier les activités selon l'ordre personnalisé
                this.activites = this.trierActivites(data.activites);
                this.applyFilters();
                this.isLoading = false;
            },
            error: (err) => {
                console.error('Erreur chargement planning', err);
                this.isLoading = false;
            }
        });
    }

    // ========== TRI PERSONNALISÉ ==========
    trierActivites(activites: Activite[]): Activite[] {
        // Ordre de priorité des statuts
        const ordreStatut: { [key: string]: number } = {
            'PLANIFIEE': 1,   // 1er : Planifiées
            'EN_COURS': 2,    // 2ème : En cours
            'REPORTEE': 3,    // 3ème : Reportées (juste après les en cours)
            'TERMINEE': 4,    // 4ème : Terminées
            'ANNULEE': 5      // 5ème : Annulées (à la fin)
        };

        return [...activites].sort((a, b) => {
            const ordreA = ordreStatut[a.statut || 'PLANIFIEE'] || 99;
            const ordreB = ordreStatut[b.statut || 'PLANIFIEE'] || 99;

            if (ordreA !== ordreB) {
                return ordreA - ordreB;
            }

            // Si même statut, trier par date de début
            return new Date(a.dateDebut).getTime() - new Date(b.dateDebut).getTime();
        });
    }

    // ========== STATISTIQUES ==========
    getAgentsOccupes(): number {
        // Récupérer tous les agents affectés aux activités EN COURS
        const agentsOccupes = new Set<string>();

        this.activites
            .filter(a => a.statut === 'EN_COURS')
            .forEach(a => {
                a.agentIds?.forEach(id => agentsOccupes.add(id));
            });

        return agentsOccupes.size;
    }

    // ========== FILTRES ==========
    applyFilters(): void {
        let filtered = [...this.activites];

        if (this.searchTerm) {
            const term = this.searchTerm.toLowerCase();
            filtered = filtered.filter(a =>
                a.titre?.toLowerCase().includes(term) ||
                a.lieu?.toLowerCase().includes(term) ||
                a.description?.toLowerCase().includes(term)
            );
        }

        if (this.filterStatut) {
            filtered = filtered.filter(a => a.statut === this.filterStatut);
        }

        this.filteredActivites = filtered;
    }

    // ========== FORMULAIRE ==========
    openCreateForm(): void {
        this.editingActivite = null;
        this.nouvelleActivite = this.getEmptyActivite();
        this.showForm = true;
    }

    editActivite(activite: Activite): void {
        this.editingActivite = activite;
        this.nouvelleActivite = {
            ...activite,
            agentIds: activite.agentIds ? [...activite.agentIds] : []
        };
        this.showForm = true;
    }

    closeForm(): void {
        this.showForm = false;
        this.editingActivite = null;
        this.nouvelleActivite = this.getEmptyActivite();
    }

    calculerNombreJours(): void {
        if (this.nouvelleActivite.dateDebut && this.nouvelleActivite.dateFin) {
            const debut = new Date(this.nouvelleActivite.dateDebut);
            const fin = new Date(this.nouvelleActivite.dateFin);
            const diff = Math.ceil((fin.getTime() - debut.getTime()) / (1000 * 60 * 60 * 24)) + 1;
            this.nouvelleActivite.nombreJours = diff > 0 ? diff : 0;
        }
    }

    creerActivite(): void {
        if (!this.nouvelleActivite.titre) {
            this.showError('Le titre est obligatoire');
            return;
        }

        this.isLoading = true;

        // Si on modifie
        if (this.editingActivite && this.editingActivite.id) {
            this.planningService.updateActivite(this.editingActivite.id, this.nouvelleActivite).subscribe({
                next: () => {
                    this.showSuccess('Activité modifiée avec succès');
                    this.closeForm();
                    this.loadPlanning();
                    this.isLoading = false;
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur lors de la modification');
                    this.isLoading = false;
                }
            });
        } else {
            // Si on crée
            this.planningService.creerActivite(this.nouvelleActivite).subscribe({
                next: () => {
                    this.showSuccess('Activité créée avec succès');
                    this.closeForm();
                    this.loadPlanning();
                    this.isLoading = false;
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur lors de la création');
                    this.isLoading = false;
                }
            });
        }
    }

    // ========== GESTION DU STATUT (SELECT DIRECT) ==========
    changerStatutDirect(activite: Activite, nouveauStatut: string): void {
        if (!activite.id) return;

        // Si le statut est identique, ne rien faire
        if (activite.statut === nouveauStatut) return;

        const label = this.getStatutLabel(nouveauStatut);

        // Confirmation
        if (!confirm(`Changer le statut de "${activite.titre}" en "${label}" ?`)) {
            // Annuler : recharger pour remettre l'ancien statut
            this.loadPlanning();
            return;
        }

        this.isLoading = true;
        this.planningService.changerStatut(activite.id, nouveauStatut).subscribe({
            next: () => {
                this.showSuccess(`Statut changé en "${label}"`);
                this.loadPlanning();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors du changement de statut');
                this.loadPlanning();
                this.isLoading = false;
            }
        });
    }

    // ========== AGENTS ==========
    isAgentSelected(agentId: string): boolean {
        return this.nouvelleActivite.agentIds?.includes(agentId) || false;
    }

    toggleAgent(agentId: string): void {
        if (!this.nouvelleActivite.agentIds) {
            this.nouvelleActivite.agentIds = [];
        }
        const index = this.nouvelleActivite.agentIds.indexOf(agentId);
        if (index > -1) {
            this.nouvelleActivite.agentIds.splice(index, 1);
        } else {
            this.nouvelleActivite.agentIds.push(agentId);
        }
    }

    filterAgentsList(): void {
        if (!this.searchAgent) {
            this.filteredAgents = this.agents;
            return;
        }
        const term = this.searchAgent.toLowerCase();
        this.filteredAgents = this.agents.filter(a =>
            a.nom?.toLowerCase().includes(term) ||
            a.prenom?.toLowerCase().includes(term) ||
            a.poste?.toLowerCase().includes(term)
        );
    }

    // ========== ACTIONS ==========
    viewActivite(activite: Activite): void {
        this.selectedActivite = activite;
        this.showDetailModal = true;
    }

    closeDetailModal(): void {
        this.showDetailModal = false;
        this.selectedActivite = null;
    }

    deleteActivite(activite: Activite): void {
        if (!activite.id) return;

        if (confirm(`Supprimer l'activité "${activite.titre}" ?`)) {
            this.planningService.supprimerActivite(activite.id).subscribe({
                next: () => {
                    this.showSuccess('Activité supprimée');
                    this.loadPlanning();
                },
                error: (err) => {
                    this.showError('Erreur lors de la suppression');
                }
            });
        }
    }

    // ========== UTILITAIRES ==========
    getStatutLabel(statut?: string): string {
        const labels: { [key: string]: string } = {
            'PLANIFIEE': 'Planifiée',
            'EN_COURS': 'En cours',
            'TERMINEE': 'Terminée',
            'ANNULEE': 'Annulée',
            'REPORTEE': 'Reportée'
        };
        return labels[statut || 'PLANIFIEE'] || statut || '-';
    }

    getStatutClass(statut?: string): string {
        const classes: { [key: string]: string } = {
            'PLANIFIEE': 'badge-planifiee',
            'EN_COURS': 'badge-en-cours',
            'TERMINEE': 'badge-terminee',
            'ANNULEE': 'badge-annulee',
            'REPORTEE': 'badge-reportee'
        };
        return classes[statut || 'PLANIFIEE'] || 'badge-planifiee';
    }

    getMoisNom(mois: number): string {
        const moisNoms = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
            'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
        return moisNoms[mois - 1];
    }

    changerMois(delta: number): void {
        this.mois += delta;
        if (this.mois > 12) {
            this.mois = 1;
            this.annee++;
        } else if (this.mois < 1) {
            this.mois = 12;
            this.annee--;
        }
        this.loadPlanning();
    }

    showSuccess(message: string): void {
        this.successMessage = message;
        setTimeout(() => this.successMessage = '', 3000);
    }

    showError(message: string): void {
        this.errorMessage = message;
        setTimeout(() => this.errorMessage = '', 5000);
    }
}