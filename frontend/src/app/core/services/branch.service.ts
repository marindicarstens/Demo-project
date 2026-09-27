import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { shareReplay, tap } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { Branch } from '../models/branch.model';

/**
 * Fetches the branch list once and searches it client-side for the typeahead: the list is small
 * and rarely changes, so a request per keystroke would be wasted.
 */
@Injectable({ providedIn: 'root' })
export class BranchService {
  private readonly http = inject(HttpClient);

  private readonly branchList = signal<Branch[]>([]);
  readonly branches = this.branchList.asReadonly();
  private readonly failed = signal(false);
  /** True after the last load() failed, until the next one starts. */
  readonly loadFailed = this.failed.asReadonly();

  // Replays the one response to every later load(); an error resets it, so the next load() retries.
  private readonly branches$ = this.http.get<Branch[]>(apiUrl('/branches')).pipe(
    tap((branches) => this.branchList.set(branches)),
    shareReplay(1),
  );

  load(): void {
    this.failed.set(false);
    this.branches$.subscribe({
      error: () => {
        this.branchList.set([]);
        this.failed.set(true);
      },
    });
  }

  /** Client-side substring match on name/city. */
  search(query: string): Branch[] {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return this.branchList();
    }
    return this.branchList().filter(
      (branch) => branch.name.toLowerCase().includes(normalized) || branch.city.toLowerCase().includes(normalized),
    );
  }
}
