import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { PlanningService, PlanningMensuel, Activite } from '../../core/services/planning.service';
import { AgentService, Agent } from '../../core/services/agent.service';
import { UtilisateurService, Utilisateur } from '../../core/services/utilisateur.service';
import { ProfilService, Profil } from '../../core/services/profil.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  // Données
  activites: Activite[] = [];
  agents: Agent[] = [];
  utilisateurs: Utilisateur[] = [];
  profils: Profil[] = [];

  // Période courante
  annee = new Date().getFullYear();
  mois = new Date().getMonth() + 1;
  aujourdHui = new Date();

  // États
  isLoading = true;
  errorMessage = '';

  // Stats calculées
  stats = {
    totalAgents: 0,
    agentsActifs: 0,
    agentsEnMission: 0,
    agentsDisponibles: 0,
    totalActivites: 0,
    activitesEnCours: 0,
    activitesPlanifiees: 0,
    activitesTerminees: 0,
    totalUtilisateurs: 0,
    utilisateursActifs: 0,
    totalProfils: 0,
    tauxOccupation: 0
  };

  // Activités récentes
  activitesRecentes: Activite[] = [];
  activitesEnCours: Activite[] = [];
  prochainesActivites: Activite[] = [];

  // Top agents
  topAgents: { agent: Agent; nbActivites: number }[] = [];

  Math = Math;

  constructor(
      private planningService: PlanningService,
      private agentService: AgentService,
      private utilisateurService: UtilisateurService,
      private profilService: ProfilService,
      public authService: AuthService
  ) { }

  ngOnInit(): void {
    this.loadAllData();
  }

  loadAllData(): void {
    this.isLoading = true;
    this.errorMessage = '';

    forkJoin({
      planning: this.planningService.getPlanningMensuel(this.annee, this.mois),
      agents: this.agentService.getAllAgents(),
      utilisateurs: this.utilisateurService.getAllUtilisateurs(),
      profils: this.profilService.getProfilsActifs()
    }).subscribe({
      next: (result) => {
        this.activites = result.planning.activites || [];
        this.agents = result.agents || [];
        this.utilisateurs = result.utilisateurs || [];
        this.profils = result.profils || [];

        this.calculerStatistiques();
        this.calculerActivites();
        this.calculerTopAgents();

        this.isLoading = false;
      },
      error: (err: any) => {
        console.error('Erreur chargement dashboard', err);
        this.errorMessage = 'Erreur lors du chargement des données';
        this.isLoading = false;
      }
    });
  }

  // ============================================================
  // ✅ HELPER : parse une date string en Date ou null si invalide
  // ============================================================
  private parseDate(dateStr: string | undefined | null): Date | null {
    if (!dateStr) return null;
    const d = new Date(dateStr);
    return isNaN(d.getTime()) ? null : d;
  }

  // ============================================================
  // ✅ HELPER : vérifie si l'activité est "en cours" aujourd'hui
  // ============================================================
  private estEnCours(act: Activite): boolean {
    const debut = this.parseDate(act.dateDebut);
    const fin = this.parseDate(act.dateFin);
    if (!debut || !fin) return false;
    return this.aujourdHui >= debut && this.aujourdHui <= fin;
  }

  // ============================================================
  // ✅ HELPER : vérifie si l'activité est future
  // ============================================================
  private estFuture(act: Activite): boolean {
    const debut = this.parseDate(act.dateDebut);
    if (!debut) return false;
    return debut > this.aujourdHui;
  }

  /**
   * Calcule les statistiques globales
   */
  private calculerStatistiques(): void {
    // Agents
    this.stats.totalAgents = this.agents.length;
    this.stats.agentsActifs = this.agents.filter(a => a.actif).length;

    // Agents en mission aujourd'hui
    const agentsEnMissionIds = new Set<string>();
    this.activites.forEach(act => {
      if (this.estEnCours(act)) {
        act.agentIds?.forEach(id => agentsEnMissionIds.add(id));
      }
    });
    this.stats.agentsEnMission = agentsEnMissionIds.size;
    this.stats.agentsDisponibles = this.stats.agentsActifs - this.stats.agentsEnMission;

    // Activités
    this.stats.totalActivites = this.activites.length;
    this.stats.activitesEnCours = this.activites.filter(a => this.estEnCours(a)).length;
    this.stats.activitesPlanifiees = this.activites.filter(a => a.statut === 'PLANIFIEE').length;
    this.stats.activitesTerminees = this.activites.filter(a => a.statut === 'TERMINEE').length;

    // Utilisateurs
    this.stats.totalUtilisateurs = this.utilisateurs.length;
    this.stats.utilisateursActifs = this.utilisateurs.filter(u => u.actif).length;
    this.stats.totalProfils = this.profils.length;

    // Taux d'occupation global
    if (this.stats.agentsActifs > 0) {
      this.stats.tauxOccupation = Math.round(
          (this.stats.agentsEnMission * 100.0 / this.stats.agentsActifs) * 100
      ) / 100;
    }
  }

  /**
   * Trie les activités : en cours, prochaines, récentes
   */
  private calculerActivites(): void {
    // Activités en cours (aujourd'hui dans la période)
    this.activitesEnCours = this.activites
        .filter(a => this.estEnCours(a))
        .slice(0, 3);

    // ✅ Prochaines activités (début dans le futur) - null-safe
    this.prochainesActivites = this.activites
        .filter(a => this.estFuture(a))
        .sort((a, b) => {
          const da = this.parseDate(a.dateDebut)?.getTime() || 0;
          const db = this.parseDate(b.dateDebut)?.getTime() || 0;
          return da - db;
        })
        .slice(0, 3);

    // ✅ Activités récentes - null-safe (brouillons sans date à la fin)
    this.activitesRecentes = [...this.activites]
        .sort((a, b) => {
          const da = this.parseDate(b.dateDebut)?.getTime() || 0;
          const db = this.parseDate(a.dateDebut)?.getTime() || 0;
          return da - db;
        })
        .slice(0, 5);
  }

  /**
   * Top 5 agents avec le plus d'activités ce mois
   */
  private calculerTopAgents(): void {
    const compteur: { [agentId: string]: number } = {};

    this.activites.forEach(act => {
      act.agentIds?.forEach(id => {
        compteur[id] = (compteur[id] || 0) + 1;
      });
    });

    this.topAgents = Object.entries(compteur)
        .map(([agentId, nb]) => {
          const agent = this.agents.find(a => a.id === agentId);
          return agent ? { agent, nbActivites: nb } : null;
        })
        .filter((x): x is { agent: Agent; nbActivites: number } => x !== null)
        .sort((a, b) => b.nbActivites - a.nbActivites)
        .slice(0, 5);
  }

  // ========== UTILITAIRES ==========
  getMoisNom(mois: number): string {
    const moisNoms = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
      'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
    return moisNoms[mois - 1];
  }

  getInitiales(agent: Agent): string {
    const prenom = agent.prenom?.charAt(0)?.toUpperCase() || '';
    const nom = agent.nom?.charAt(0)?.toUpperCase() || '';
    return prenom + nom;
  }

  getStatutLabel(statut?: string): string {
    const labels: { [key: string]: string } = {
      'BROUILLON': 'Brouillon',
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
      'BROUILLON': 'statut-brouillon',
      'PLANIFIEE': 'statut-planifiee',
      'EN_COURS': 'statut-en-cours',
      'TERMINEE': 'statut-terminee',
      'ANNULEE': 'statut-annulee',
      'REPORTEE': 'statut-reportee'
    };
    return classes[statut || 'PLANIFIEE'] || 'statut-planifiee';
  }

  getStatutIcon(statut?: string): string {
    const icons: { [key: string]: string } = {
      'BROUILLON': 'pi pi-pencil',
      'PLANIFIEE': 'pi pi-calendar',
      'EN_COURS': 'pi pi-play-circle',
      'TERMINEE': 'pi pi-check-circle',
      'ANNULEE': 'pi pi-times-circle',
      'REPORTEE': 'pi pi-refresh'
    };
    return icons[statut || 'PLANIFIEE'] || 'pi pi-circle';
  }

  getGreeting(): string {
    const heure = new Date().getHours();
    if (heure < 12) return 'Bonjour';
    if (heure < 18) return 'Bon après-midi';
    return 'Bonsoir';
  }

  // ✅ Accepte undefined / null
  formatDate(date: string | Date | undefined | null): string {
    if (!date) return '-';
    const d = typeof date === 'string' ? new Date(date) : date;
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  // ✅ Accepte undefined / null
  formatDateShort(date: string | Date | undefined | null): string {
    if (!date) return '-';
    const d = typeof date === 'string' ? new Date(date) : date;
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit'
    });
  }
}