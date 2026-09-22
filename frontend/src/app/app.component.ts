import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterOutlet, RouterLink, RouterLinkActive],
    template: `
    <div class="app-container">
      <header class="app-header">
        <div class="header-left">
          <img src="assets/logo_pnlp.png" alt="Logo PNLP" class="app-logo" onerror="this.style.display='none'">
          <h1>PNLP - Gestion du Planning</h1>
        </div>
        <nav class="main-nav">
          <a routerLink="/dashboard" routerLinkActive="active" class="nav-link">
            <span class="nav-icon">📊</span> Dashboard
          </a>
          <a routerLink="/planning" routerLinkActive="active" class="nav-link">
            <span class="nav-icon">📅</span> Planning
          </a>
          <a routerLink="/parametres" routerLinkActive="active" class="nav-link">
            <span class="nav-icon">⚙️</span> Paramètres
          </a>
          <a routerLink="/administration" routerLinkActive="active" class="nav-link">
            <span class="nav-icon">👤</span> Administration
          </a>
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
    .app-container {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background: #f5f5f5;
    }
    .app-header {
      background: linear-gradient(135deg, #1B5E20 0%, #2E7D32 100%);
      color: white;
      padding: 12px 30px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      box-shadow: 0 2px 8px rgba(0,0,0,0.15);
    }
    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .app-logo {
      height: 48px;
      width: auto;
      background: white;
      border-radius: 8px;
      padding: 4px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }
    .app-header h1 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }
    .main-nav {
      display: flex;
      gap: 8px;
    }
    .nav-link {
      color: white;
      text-decoration: none;
      padding: 10px 18px;
      border-radius: 8px;
      font-weight: 500;
      font-size: 14px;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s;
    }
    .nav-link:hover,
    .nav-link.active {
      background: rgba(255,255,255,0.2);
    }
    .nav-icon {
      font-size: 16px;
    }
    .app-main {
      flex: 1;
      padding: 20px;
    }
    .app-footer {
      background: #333;
      color: white;
      text-align: center;
      padding: 15px;
      font-size: 14px;
    }
  `]
})
export class AppComponent {
    title = 'PNLP Planning';
}