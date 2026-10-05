import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { PermissionService } from '../../core/services/permission.service';
import { PlanningService, PlanningMensuel, Activite } from '../../core/services/planning.service';
import { AgentService, Agent } from '../../core/services/agent.service';
import { UtilisateurService, Utilisateur } from '../../core/services/utilisateur.service';
import { ProfilService, Profil } from '../../core/services/profil.service';
import { AuthService } from '../../core/services/auth.service';
import { RefreshService } from '../../core/services/refresh.service';

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
    tauxOccupation: 0,
    activitesAuProgramme: 0,
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
      public permissionService: PermissionService,
      public authService: AuthService,
      private refreshService: RefreshService
  ) { }

  ngOnInit(): void {
    // ✅ CORRECTION : on ne charge PLUS manuellement au démarrage.
    //    Le BehaviorSubject du RefreshService émet immédiatement sa valeur
    //    initiale (0), ce qui déclenche le 1er chargement.
    //    Cela évite un double appel HTTP (un pour ngOnInit, un pour le BehaviorSubject).
    //    À chaque mutation sur un autre écran, le compteur est incrémenté
    //    et le chargement se refait automatiquement.
    this.refreshService.refresh$.subscribe(() => {
      this.loadAllData();
    });
  }

  loadAllData(): void {
    this.isLoading = true;
    this.errorMessage = '';

    // ✅ TOLÉRANT À TOUTES LES ERREURS (403, 500, etc.)
    forkJoin({
      planning: this.planningService.getPlanningMensuel(this.annee, this.mois).pipe(
          catchError((err) => {
            console.warn('⚠️ [Dashboard] Erreur planning:', err.status);
            return of({
              mois: `${this.annee}-${String(this.mois).padStart(2, '0')}`,
              activites: [],
              planningParAgent: {}
            } as PlanningMensuel);
          })
      ),
      agents: this.agentService.getAllAgents().pipe(
          catchError((err) => {
            console.warn('⚠️ [Dashboard] Erreur agents:', err.status);
            return of([] as Agent[]);
          })
      )
    }).subscribe({
      next: (result) => {
        this.activites = result.planning.activites || [];
        this.agents = result.agents || [];

        this.calculerStatistiques();
        this.calculerActivites();
        this.calculerTopAgents();

        this.isLoading = false;
      },
      error: (err: any) => {
        console.error('❌ Erreur critique dashboard', err);
        this.errorMessage = 'Erreur lors du chargement des données';
        this.isLoading = false;
      }
    });

    // ✅ Charger les utilisateurs SEULEMENT si autorisé
    if (this.permissionService.peutCrudUtilisateurs()) {
      this.utilisateurService.getAllUtilisateurs().pipe(
          catchError(() => of([] as Utilisateur[]))
      ).subscribe({
        next: (data) => {
          this.utilisateurs = data;
          this.calculerStatistiques();
        }
      });
    } else {
      this.utilisateurs = [];
    }

    // ✅ Charger les profils SEULEMENT si autorisé
    if (this.permissionService.peutCrudProfils() ||
        this.permissionService.aAccesAuMenu('AGENTS')) {
      this.profilService.getProfilsActifs().pipe(
          catchError(() => of([] as Profil[]))
      ).subscribe({
        next: (data) => {
          this.profils = data;
          this.calculerStatistiques();
        }
      });
    } else {
      this.profils = [];
    }
  }

  // ============================================================
  // HELPERS : parsing de dates (sans heure)
  // ============================================================
  private parseDate(dateStr: string | undefined | null): Date | null {
    if (!dateStr) return null;
    const d = new Date(dateStr);
    return isNaN(d.getTime()) ? null : d;
  }

  private estEnCours(act: Activite): boolean {
    const debut = this.parseDate(act.dateDebut);
    const fin = this.parseDate(act.dateFin);
    if (!debut || !fin) return false;

    // ✅ Exclure les statuts terminés/annulés/reportés/brouillon
    const statut = act.statut;
    if (statut === 'TERMINEE' || statut === 'ANNULEE'
        || statut === 'REPORTEE' || statut === 'BROUILLON') {
      return false;
    }

    // ✅ Exclure les activités au programme
    if (act.auProgramme === true) {
      return false;
    }

    const today = new Date(this.aujourdHui.getFullYear(),
        this.aujourdHui.getMonth(),
        this.aujourdHui.getDate());
    const d = new Date(debut.getFullYear(), debut.getMonth(), debut.getDate());
    const f = new Date(fin.getFullYear(), fin.getMonth(), fin.getDate());

    return today >= d && today <= f;
  }

  private estFuture(act: Activite): boolean {
    const debut = this.parseDate(act.dateDebut);
    if (!debut) return false;

    // ✅ Exclure les statuts terminés/annulés
    const statut = act.statut;
    if (statut === 'TERMINEE' || statut === 'ANNULEE' || statut === 'BROUILLON') {
      return false;
    }

    const today = new Date(this.aujourdHui.getFullYear(),
        this.aujourdHui.getMonth(),
        this.aujourdHui.getDate());
    const d = new Date(debut.getFullYear(), debut.getMonth(), debut.getDate());

    return d > today;
  }

  private calculerStatistiques(): void {
    // Agents
    this.stats.totalAgents = this.agents.length;
    this.stats.agentsActifs = this.agents.filter(a => a.actif).length;
    this.stats.activitesAuProgramme = this.activites.filter(a => a.auProgramme).length;

    // Agents en mission aujourd'hui
    const agentsEnMissionIds = new Set<string>();
    this.activites.forEach(act => {
      if (this.estEnCours(act)) {
        act.agentIds?.forEach(id => agentsEnMissionIds.add(id));
      }
    });
    this.stats.agentsEnMission = agentsEnMissionIds.size;
    this.stats.agentsDisponibles = Math.max(
        0, this.stats.agentsActifs - this.stats.agentsEnMission);

    // Activités
    this.stats.totalActivites = this.activites.length;
    this.stats.activitesEnCours = this.activites.filter(a => this.estEnCours(a)).length;
    this.stats.activitesPlanifiees = this.activites.filter(a => a.statut === 'PLANIFIEE').length;
    this.stats.activitesTerminees = this.activites.filter(a => a.statut === 'TERMINEE').length;

    // Utilisateurs
    this.stats.totalUtilisateurs = this.utilisateurs.length;
    this.stats.utilisateursActifs = this.utilisateurs.filter(u => u.actif).length;
    this.stats.totalProfils = this.profils.length;

    // Taux d'occupation
    if (this.stats.agentsActifs > 0) {
      this.stats.tauxOccupation = Math.round(
          (this.stats.agentsEnMission * 100.0 / this.stats.agentsActifs) * 100
      ) / 100;
    } else {
      this.stats.tauxOccupation = 0;
    }
  }

  private calculerActivites(): void {
    this.activitesEnCours = this.activites
        .filter(a => this.estEnCours(a))
        .slice(0, 3);

    this.prochainesActivites = this.activites
        .filter(a => this.estFuture(a))
        .sort((a, b) => {
          const da = this.parseDate(a.dateDebut)?.getTime() || 0;
          const db = this.parseDate(b.dateDebut)?.getTime() || 0;
          return da - db;
        })
        .slice(0, 3);

    this.activitesRecentes = [...this.activites]
        .sort((a, b) => {
          const da = this.parseDate(b.dateDebut)?.getTime() || 0;
          const db = this.parseDate(a.dateDebut)?.getTime() || 0;
          return da - db;
        })
        .slice(0, 5);
  }

  private calculerTopAgents(): void {
    const compteur: { [agentId: string]: number } = {};

    // ✅ Ne compter QUE les activités non terminées/annulées/reportées, hors programme
    this.activites
        .filter(a => a.statut !== 'TERMINEE'
            && a.statut !== 'ANNULEE'
            && a.statut !== 'REPORTEE'
            && a.statut !== 'BROUILLON'
            && a.auProgramme !== true)
        .forEach(act => {
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