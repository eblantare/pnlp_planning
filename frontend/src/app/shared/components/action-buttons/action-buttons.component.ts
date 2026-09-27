import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

export type ActionSeverity = 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast';

export interface ActionButton {
    id: string;
    icon: string;
    label: string;
    severity?: ActionSeverity;
    show?: boolean;
}

@Component({
    selector: 'app-action-buttons',
    standalone: true,
    imports: [CommonModule, ButtonModule, TooltipModule],
    template: `
        <div class="action-buttons-container">
            <button *ngFor="let action of visibleActions()"
                    type="button"
                    class="action-btn-native"
                    [ngClass]="'sev-' + (action.severity || 'secondary')"
                    [title]="action.label"
                    (click)="onActionClick(action, $event)">
                <i [class]="action.icon"></i>
            </button>
        </div>
    `,
    styles: [`
        .action-buttons-container {
            display: flex;
            gap: 4px;
            justify-content: center;
            align-items: center;
        }

        .action-btn-native {
            width: 32px;
            height: 32px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 14px;
            transition: all 0.15s ease;
            padding: 0;
            background: #f5f5f5;
            color: #555;
        }

        .action-btn-native:hover {
            transform: translateY(-1px) scale(1.05);
            box-shadow: 0 2px 6px rgba(0,0,0,0.15);
        }

        .action-btn-native:active {
            transform: translateY(0) scale(0.98);
        }

        .action-btn-native i {
            line-height: 1;
        }

        .sev-info      { background: #E3F2FD; color: #1565C0; }
        .sev-info:hover { background: #BBDEFB; }

        .sev-warning   { background: #FFF3E0; color: #E65100; }
        .sev-warning:hover { background: #FFE0B2; }

        .sev-danger    { background: #FFEBEE; color: #C62828; }
        .sev-danger:hover { background: #FFCDD2; }

        .sev-success   { background: #E8F5E9; color: #2E7D32; }
        .sev-success:hover { background: #C8E6C9; }

        .sev-secondary { background: #F5F5F5; color: #555; }
        .sev-secondary:hover { background: #E0E0E0; }
    `]
})
export class ActionButtonsComponent {
    @Input() actions: ActionButton[] = [];
    @Output() actionClick = new EventEmitter<string>();

    visibleActions(): ActionButton[] {
        return this.actions.filter(a => a.show !== false);
    }

    onActionClick(action: ActionButton, event: Event): void {
        console.log('🔘 Action cliquée:', action.id);   // ✅ Debug
        event.stopPropagation();
        event.preventDefault();
        this.actionClick.emit(action.id);
    }
}