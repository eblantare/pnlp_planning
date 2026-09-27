import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Utilisateur {
    id: string;
    username: string;
    email?: string;
    profilId?: string;
    profilCode?: string;
    profilLibelle?: string;
    agentId?: string;
    agentNom?: string;
    actif: boolean;
    derniereConnexion?: string;
    createdAt?: string;
}

export interface LoginRequest {
    username: string;
    password: string;
}

export interface LoginResponse {
    token: string;
    tokenType: string;
    expiresIn: number;
    utilisateur: Utilisateur;
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private apiUrl = `${environment.apiUrl}/auth`;
    private readonly TOKEN_KEY = 'pnlp_token';
    private readonly USER_KEY = 'pnlp_user';

    private currentUserSubject = new BehaviorSubject<Utilisateur | null>(this.getUserFromStorage());
    public currentUser$ = this.currentUserSubject.asObservable();

    constructor(private http: HttpClient) { }

    login(credentials: LoginRequest): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(`${this.apiUrl}/login`, credentials)
            .pipe(
                tap(response => {
                    this.saveToken(response.token);
                    this.saveUser(response.utilisateur);
                    this.currentUserSubject.next(response.utilisateur);
                })
            );
    }

    logout(): void {
        localStorage.removeItem(this.TOKEN_KEY);
        localStorage.removeItem(this.USER_KEY);
        this.currentUserSubject.next(null);
    }

    getToken(): string | null {
        return localStorage.getItem(this.TOKEN_KEY);
    }

    isAuthenticated(): boolean {
        const token = this.getToken();
        if (!token) return false;

        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            return payload.exp * 1000 > Date.now();
        } catch {
            return false;
        }
    }

    getCurrentUser(): Utilisateur | null {
        return this.currentUserSubject.value;
    }

    hasRole(role: string): boolean {
        const user = this.getCurrentUser();
        return user?.profilCode === role;
    }

    private saveToken(token: string): void {
        localStorage.setItem(this.TOKEN_KEY, token);
    }

    private saveUser(user: Utilisateur): void {
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    }

    private getUserFromStorage(): Utilisateur | null {
        const userStr = localStorage.getItem(this.USER_KEY);
        return userStr ? JSON.parse(userStr) : null;
    }
}