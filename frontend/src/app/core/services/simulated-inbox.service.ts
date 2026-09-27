import { Injectable } from '@angular/core';
import { SimulatedEmail } from '../models/booking.model';

const STORAGE_KEY = 'demo-booking:simulated-email';
// A fixed, non-"_blank" target: subsequent opens navigate this same tab (browsers keep a
// window's name across navigation) instead of piling up a new tab per email - e.g. reopening a
// confirmation-request email (see BrowsePage.reopenEmail()) reuses the tab it originally opened
// in, rather than spawning another one.
const INBOX_WINDOW_NAME = 'demo-booking-simulated-inbox';
// Long enough that refreshing the inbox tab still shows the email; short enough that a used
// token's link does not sit in localStorage for good.
const STORED_EMAIL_TTL_MS = 10 * 60 * 1000;

export interface StoredSimulatedEmail {
  email: SimulatedEmail;
  actionLabel: string;
}

interface StoredEntry extends StoredSimulatedEmail {
  storedAt: number;
}

/**
 * Opens a simulated email in a separate browser tab, closer to "go check your inbox" than an
 * in-page popup. There is no server-side session to fetch it from, so it is handed over through
 * localStorage, which (unlike sessionStorage) all tabs of the origin share; SimulatedInboxPage
 * reads it back.
 */
@Injectable({ providedIn: 'root' })
export class SimulatedInboxService {
  /** @returns false if the browser blocked the new tab (e.g. a popup blocker) - callers show a
   * "reopen manually" fallback in that case, since a direct click always gets through. */
  open(email: SimulatedEmail, actionLabel: string): boolean {
    // If the write below fails, an expired entry must not linger in its place.
    this.purgeExpired();
    const stored: StoredEntry = { email, actionLabel, storedAt: Date.now() };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    } catch {
      // Storage can throw (private browsing, quota) - the new tab just falls back to its own
      // "no simulated email found" state, so there's nothing further to do here.
    }
    return window.open('/simulated-inbox', INBOX_WINDOW_NAME) !== null;
  }

  /** Drops an entry older than the TTL. It runs at app start, on open() and on read(), so an
   * expired link stays in localStorage only until the app next loads in this browser. */
  purgeExpired(): void {
    this.read();
  }

  /** Also drops an entry older than the TTL. */
  read(): StoredSimulatedEmail | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const { email, actionLabel, storedAt } = JSON.parse(raw) as StoredEntry;
      // Written as a negation so an entry without a timestamp also counts as expired.
      if (!(Date.now() - storedAt < STORED_EMAIL_TTL_MS)) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return { email, actionLabel };
    } catch {
      return null;
    }
  }
}
