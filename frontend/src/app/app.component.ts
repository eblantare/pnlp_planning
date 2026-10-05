import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/services/auth.service';
import { PermissionService } from './core/services/permission.service';
import { HasPermissionDirective } from './shared/directives/has-permission.directive';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [
        RouterOutlet,
        RouterLink,
        RouterLinkActive,
        CommonModule,
        HasPermissionDirective     // ✅ AJOUT : la directive de permissions
    ],
    template: `
    <div class="app-container">
      <header class="app-header">
        <div class="header-left">
          <img src="assets/logo_pnlp.png" alt="Logo PNLP" class="app-logo" onerror="this.style.display='none'">
          <div class="header-title">
            <h1>PNLP - Gestion du Planning</h1>
            <span class="header-subtitle">Programme National de Lutte contre le Paludisme</span>
          </div>
        </div>

        <nav class="main-nav">
          <!-- Dashboard -->
          <a *appHasPermission="'menu:DASHBOARD'"
             routerLink="/dashboard" routerLinkActive="active" class="nav-link">
            <i class="pi pi-home"></i>
            <span>Dashboard</span>
          </a>

          <!-- Planning -->
          <a *appHasPermission="'menu:PLANNING'"
             routerLink="/planning" routerLinkActive="active" class="nav-link">
            <i class="pi pi-calendar"></i>
            <span>Planning</span>
          </a>

          <!-- Statistiques -->
          <a *appHasPermission="'menu:STATISTIQUES'"
             routerLink="/statistiques" routerLinkActive="active" class="nav-link">
            <i class="pi pi-chart-bar"></i>
            <span>Statistiques</span>
          </a>

          <!-- Sous-menu Administration : visible si menu AGENTS OU PROFILS OU UTILISATEURS -->
          <div class="nav-dropdown"
               *ngIf="permissionService.aAccesAuMenu('AGENTS') ||
                      permissionService.aAccesAuMenu('PROFILS') ||
                      permissionService.aAccesAuMenu('UTILISATEURS')">
            <button class="nav-link dropdown-toggle"
                    [class.active]="isAdminMenuActive()"
                    (click)="toggleAdminMenu($event)">
              <i class="pi pi-cog"></i>
              <span>Administration</span>
              <i class="pi pi-chevron-down dropdown-arrow"
                 [class.rotated]="isAdminMenuOpen"></i>
            </button>
            <div class="dropdown-menu" *ngIf="isAdminMenuOpen">
              <a *appHasPermission="'menu:AGENTS'"
                 routerLink="/agents" routerLinkActive="active" class="dropdown-item">
                <i class="pi pi-users"></i>
                <span>Agents</span>
              </a>
              <a *appHasPermission="'menu:PROFILS'"
                 routerLink="/profils" routerLinkActive="active" class="dropdown-item">
                <i class="pi pi-id-card"></i>
                <span>Profils</span>
              </a>
              <a *appHasPermission="'menu:UTILISATEURS'"
                 routerLink="/utilisateurs" routerLinkActive="active" class="dropdown-item">
                <i class="pi pi-user"></i>
                <span>Utilisateurs</span>
              </a>
            </div>
          </div>

          <!-- ⏰ HORLOGE DATE/HEURE CIRCULAIRE -->
          <div class="clock-widget" [title]="dateLongue">
            <svg class="clock-ring" viewBox="0 0 44 44">
              <circle class="clock-ring-bg" cx="22" cy="22" r="20"></circle>
              <circle class="clock-ring-progress" cx="22" cy="22" r="20"
                      [style.stroke-dashoffset]="ringOffset"></circle>
            </svg>
            <div class="clock-content">
              <span class="clock-time">{{ heureActuelle }}</span>
              <span class="clock-date">{{ dateCourte }}</span>
            </div>
            <span class="clock-pulse"></span>
          </div>

          <!-- Menu utilisateur -->
          <div class="user-menu" *ngIf="authService.getCurrentUser()">
            <div class="user-info">
              <span class="user-avatar">
                {{ authService.getCurrentUser()?.username?.charAt(0)?.toUpperCase() }}
              </span>
              <div class="user-details">
                <span class="user-name">{{ authService.getCurrentUser()?.username }}</span>
                <span class="user-role">{{ getProfilLibelles() }}</span>
              </div>
            </div>
            <button class="btn-logout" (click)="logout()" title="Se déconnecter">
              <i class="pi pi-sign-out"></i>
            </button>
          </div>
        </nav>
      </header>

      <main class="app-main">
        <router-outlet></router-outlet>
      </main>

      <footer class="app-footer">
        <p>© 2026 PNLP - Tous droits réservés</p>
      </footer>
    </div>
  `,
    styles: [`
    /* ============================================================
       CONTAINER
       ============================================================ */
    .app-container {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background: #f5f5f5;
    }

    /* ============================================================
       HEADER
       ============================================================ */
    .app-header {
      background: linear-gradient(135deg, #1B5E20 0%, #2E7D32 100%);
      color: white;
      padding: 10px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      box-shadow: 0 2px 8px rgba(0,0,0,0.15);
      position: sticky;
      top: 0;
      z-index: 100;
      min-height: 68px;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 14px;
      flex-shrink: 0;
    }

    .app-logo {
      height: 44px;
      width: auto;
      background: white;
      border-radius: 8px;
      padding: 4px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }

    .header-title {
      display: flex;
      flex-direction: column;
      line-height: 1.2;
    }

    .app-header h1 {
      margin: 0;
      font-size: 17px;
      font-weight: 700;
      color: white;
    }

    .header-subtitle {
      font-size: 10px;
      color: rgba(255,255,255,0.75);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 500;
    }

    /* ============================================================
       NAVIGATION
       ============================================================ */
    .main-nav {
      display: flex;
      gap: 4px;
      align-items: center;
    }

    .nav-link {
      color: rgba(255,255,255,0.9);
      text-decoration: none;
      padding: 9px 14px;
      border-radius: 8px;
      font-weight: 500;
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s;
      background: none;
      border: none;
      cursor: pointer;
      font-family: inherit;
      white-space: nowrap;
    }

    .nav-link i {
      font-size: 14px;
      color: rgba(255,255,255,0.85);
    }

    .nav-link:hover {
      background: rgba(255,255,255,0.15);
      color: white;
    }

    .nav-link:hover i {
      color: white;
    }

    .nav-link.active {
      background: rgba(255,255,255,0.22);
      color: white;
      font-weight: 600;
    }

    .nav-link.active i {
      color: white;
    }

    /* ============================================================
       DROPDOWN ADMINISTRATION
       ============================================================ */
    .nav-dropdown {
      position: relative;
    }

    .dropdown-toggle {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .dropdown-arrow {
      font-size: 10px !important;
      transition: transform 0.2s;
      margin-left: 2px;
    }

    .dropdown-arrow.rotated {
      transform: rotate(180deg);
    }

    .dropdown-menu {
      position: absolute;
      top: calc(100% + 6px);
      left: 0;
      background: white;
      border-radius: 10px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.18);
      min-width: 200px;
      padding: 6px;
      z-index: 1000;
      animation: slideDown 0.2s;
    }

    @keyframes slideDown {
      from { opacity: 0; transform: translateY(-6px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .dropdown-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 14px;
      color: #374151;
      text-decoration: none;
      font-size: 13px;
      font-weight: 500;
      border-radius: 6px;
      transition: all 0.15s;
    }

    .dropdown-item i {
      font-size: 14px;
      color: #6B7280;
      width: 16px;
      text-align: center;
    }

    .dropdown-item:hover {
      background: #F3F4F6;
      color: #1B5E20;
    }

    .dropdown-item:hover i {
      color: #1B5E20;
    }

    .dropdown-item.active {
      background: #E8F5E9;
      color: #1B5E20;
      font-weight: 600;
    }

    .dropdown-item.active i {
      color: #1B5E20;
    }

    /* ============================================================
       HORLOGE
       ============================================================ */
    .clock-widget {
      position: relative;
      width: 54px;
      height: 54px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-left: 10px;
      cursor: default;
      user-select: none;
      transition: transform 0.25s ease;
    }

    .clock-widget:hover { transform: scale(1.06); }

    .clock-ring {
      position: absolute;
      top: 0; left: 0;
      width: 100%; height: 100%;
      transform: rotate(-90deg);
      pointer-events: none;
    }

    .clock-ring-bg {
      fill: rgba(0, 0, 0, 0.28);
      stroke: rgba(255, 255, 255, 0.15);
      stroke-width: 1.5;
    }

    .clock-ring-progress {
      fill: none;
      stroke: #4DD0E1;
      stroke-width: 2.5;
      stroke-linecap: round;
      stroke-dasharray: 125.66;
      stroke-dashoffset: 0;
      transition: stroke-dashoffset 0.5s linear;
      filter: drop-shadow(0 0 4px rgba(77, 208, 225, 0.9));
    }

    .clock-content {
      position: relative;
      z-index: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }

    .clock-time {
      font-size: 11px;
      font-weight: 800;
      color: #FFFFFF;
      letter-spacing: 0.3px;
      font-variant-numeric: tabular-nums;
    }

    .clock-date {
      font-size: 8px;
      font-weight: 700;
      color: #4DD0E1;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-top: 2px;
    }

    .clock-pulse {
      position: absolute;
      top: 2px; right: 2px;
      width: 8px; height: 8px;
      border-radius: 50%;
      background: #4DD0E1;
      box-shadow: 0 0 0 0 rgba(77, 208, 225, 0.7);
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% { box-shadow: 0 0 0 0 rgba(77, 208, 225, 0.7); }
      70% { box-shadow: 0 0 0 8px rgba(77, 208, 225, 0); }
      100% { box-shadow: 0 0 0 0 rgba(77, 208, 225, 0); }
    }

    /* ============================================================
       MENU UTILISATEUR
       ============================================================ */
    .user-menu {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 4px 6px 4px 12px;
      border-radius: 24px;
      background: rgba(255,255,255,0.15);
      margin-left: 8px;
      border: 1px solid rgba(255,255,255,0.1);
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .user-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: white;
      color: #1B5E20;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 13px;
      flex-shrink: 0;
    }

    .user-details {
      display: flex;
      flex-direction: column;
      line-height: 1.15;
    }

    .user-name {
      font-size: 12px;
      font-weight: 700;
      color: white;
    }

    .user-role {
      font-size: 9px;
      color: rgba(255,255,255,0.75);
      text-transform: uppercase;
      letter-spacing: 0.4px;
      font-weight: 600;
    }

    .btn-logout {
      background: rgba(255,255,255,0.18);
      border: none;
      color: white;
      width: 30px;
      height: 30px;
      border-radius: 50%;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
      font-size: 13px;
      flex-shrink: 0;
    }

    .btn-logout:hover {
      background: #EF5350;
      transform: scale(1.08);
    }

    /* ============================================================
       MAIN & FOOTER
       ============================================================ */
    .app-main {
      flex: 1;
      padding: 20px;
    }

    .app-footer {
      background: #2E2E2E;
      color: white;
      text-align: center;
      padding: 14px;
      font-size: 13px;
    }

    .app-footer p {
      margin: 0;
      opacity: 0.85;
    }

    /* ============================================================
       RESPONSIVE
       ============================================================ */
    @media (max-width: 1100px) {
      .header-subtitle { display: none; }
      .nav-link span { display: none; }
      .nav-link { padding: 10px; }
      .nav-link i { font-size: 16px; }
      .user-details { display: none; }
      .user-menu { padding: 4px 6px; }
    }

    @media (max-width: 768px) {
      .app-header {
        padding: 8px 12px;
        flex-direction: column;
        gap: 10px;
        align-items: stretch;
      }
      .header-left { justify-content: center; }
      .main-nav {
        justify-content: center;
        flex-wrap: wrap;
      }
      .clock-widget {
        width: 46px;
        height: 46px;
        margin-left: 4px;
      }
      .clock-time { font-size: 10px; }
      .clock-date { font-size: 7px; }
    }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
    title = 'PNLP Planning';
    isAdminMenuOpen = false;

    // ⏰ Horloge
    heureActuelle = '';
    dateCourte = '';
    dateLongue = '';
    ringOffset = 0;

    private timerId: any;

    constructor(
        public authService: AuthService,
        public permissionService: PermissionService,   // ✅ NOUVEAU : public pour le template
        private router: Router
    ) {}

    ngOnInit(): void {
        this.mettreAJourHorloge();
        this.timerId = setInterval(() => this.mettreAJourHorloge(), 1000);

        // ✅ NOUVEAU : charger les permissions si l'utilisateur est déjà connecté
        if (this.authService.isAuthenticated()) {
            this.permissionService.chargerMesPermissions().subscribe({
                error: (err) => console.error('Erreur chargement permissions', err)
            });
        }
    }

    ngOnDestroy(): void {
        if (this.timerId) {
            clearInterval(this.timerId);
        }
    }

    /**
     * ✅ NOUVEAU : retourne les libellés de profils formatés
     * (ex: "Administrateur, Planificateur")
     */
    getProfilLibelles(): string {
        const libelles = this.authService.getCurrentUser()?.profilLibelles;
        if (!libelles || libelles.length === 0) return '-';
        return libelles.join(', ');
    }

    private mettreAJourHorloge(): void {
        const now = new Date();
        this.heureActuelle = now.toLocaleTimeString('fr-FR', {
            hour: '2-digit',
            minute: '2-digit'
        });
        this.dateCourte = now.toLocaleDateString('fr-FR', {
            day: '2-digit',
            month: 'short'
        }).replace('.', '').toUpperCase();
        this.dateLongue = now.toLocaleDateString('fr-FR', {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
            year: 'numeric'
        });
        const secondes = now.getSeconds();
        const circonference = 2 * Math.PI * 20;
        this.ringOffset = circonference - (circonference * secondes / 60);
    }

    toggleAdminMenu(event: Event): void {
        event.stopPropagation();
        this.isAdminMenuOpen = !this.isAdminMenuOpen;
    }

    isAdminMenuActive(): boolean {
        const path = window.location.pathname;
        return path.includes('/agents') ||
            path.includes('/profils') ||
            path.includes('/utilisateurs');
    }

    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        const target = event.target as HTMLElement;
        if (!target.closest('.nav-dropdown')) {
            this.isAdminMenuOpen = false;
        }
    }

    logout(): void {
        if (confirm('Voulez-vous vous déconnecter ?')) {
            this.permissionService.vider();   // ✅ NOUVEAU : vider les permissions
            this.authService.logout();
            this.router.navigate(['/login']);
        }
    }
}