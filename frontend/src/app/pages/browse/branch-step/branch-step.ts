import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Branch } from '../../../core/models/branch.model';
import { BranchService } from '../../../core/services/branch.service';

/** Typeahead over the branch list. Starts from `selected`, so a re-created step keeps the branch. */
@Component({
  selector: 'app-branch-step',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './branch-step.html',
  styleUrl: '../browse-step.scss',
})
export class BranchStep {
  protected readonly branchService = inject(BranchService);

  readonly stepNumber = input.required<number>();
  readonly selected = input<Branch | null>(null);
  readonly branchSelected = output<Branch>();

  // The autocomplete sets this to the chosen Branch object, and to the typed text otherwise.
  readonly query = linkedSignal<string | Branch>(() => this.selected() ?? '');
  readonly filteredBranches = computed(() => {
    const query = this.query();
    return typeof query === 'string' ? this.branchService.search(query) : [];
  });

  constructor() {
    this.branchService.load();
  }

  displayBranch(branch: Branch | string | null): string {
    if (!branch || typeof branch === 'string') {
      return branch ?? '';
    }
    return `${branch.name} — ${branch.city}`;
  }
}
