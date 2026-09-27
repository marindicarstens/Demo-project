import { TestBed } from '@angular/core/testing';
import { SimulatedEmail } from '../models/booking.model';
import { SimulatedInboxService } from './simulated-inbox.service';

const EMAIL: SimulatedEmail = {
  subject: 'Please confirm your appointment',
  from: 'bookings@example.com',
  bodyText: 'Click the link to confirm.',
  actionLink: 'http://localhost:4200/confirm/raw-token',
  sentAt: '2026-09-18T07:30:00Z',
};

// An in-memory Storage: the test runtime does not always expose a usable localStorage global.
function memoryStorage(): Storage {
  const items = new Map<string, string>();
  return {
    get length() {
      return items.size;
    },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => Array.from(items.keys())[index] ?? null,
    removeItem: (key) => items.delete(key),
    setItem: (key, value) => items.set(key, String(value)),
  };
}

describe('SimulatedInboxService', () => {
  let service: SimulatedInboxService;

  beforeEach(() => {
    vi.stubGlobal('localStorage', memoryStorage());
    TestBed.configureTestingModule({});
    service = TestBed.inject(SimulatedInboxService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('stores the email for the inbox tab to read back', () => {
    vi.spyOn(window, 'open').mockReturnValue({} as Window);

    service.open(EMAIL, 'Confirm my appointment');

    expect(service.read()).toEqual({ email: EMAIL, actionLabel: 'Confirm my appointment' });
  });

  it('returns true when the new tab opened and false when the browser blocked it', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue({} as Window);
    expect(service.open(EMAIL, 'Confirm my appointment')).toBe(true);
    expect(open).toHaveBeenCalledWith('/simulated-inbox', 'demo-booking-simulated-inbox');

    open.mockReturnValue(null);
    expect(service.open(EMAIL, 'Confirm my appointment')).toBe(false);
  });

  it('reads nothing when no email was stored or the stored value is corrupt', () => {
    expect(service.read()).toBeNull();

    localStorage.setItem('demo-booking:simulated-email', '{not json');
    expect(service.read()).toBeNull();
  });

  it('keeps the email readable for a refresh, then clears it after 10 minutes', () => {
    vi.spyOn(window, 'open').mockReturnValue({} as Window);
    const now = vi.spyOn(Date, 'now').mockReturnValue(1_000_000);
    service.open(EMAIL, 'Confirm my appointment');

    now.mockReturnValue(1_000_000 + 9 * 60 * 1000);
    expect(service.read()?.email).toEqual(EMAIL);

    now.mockReturnValue(1_000_000 + 10 * 60 * 1000);
    expect(service.read()).toBeNull();
    expect(localStorage.getItem('demo-booking:simulated-email')).toBeNull();
  });

  it('purgeExpired() drops an expired entry without it being read', () => {
    vi.spyOn(window, 'open').mockReturnValue({} as Window);
    const now = vi.spyOn(Date, 'now').mockReturnValue(1_000_000);
    service.open(EMAIL, 'Confirm my appointment');

    now.mockReturnValue(1_000_000 + 9 * 60 * 1000);
    service.purgeExpired();
    expect(localStorage.getItem('demo-booking:simulated-email')).not.toBeNull();

    now.mockReturnValue(1_000_000 + 10 * 60 * 1000);
    service.purgeExpired();
    expect(localStorage.getItem('demo-booking:simulated-email')).toBeNull();
  });

  it('open() drops an expired entry even when storing the new one fails', () => {
    vi.spyOn(window, 'open').mockReturnValue({} as Window);
    const now = vi.spyOn(Date, 'now').mockReturnValue(1_000_000);
    service.open(EMAIL, 'Confirm my appointment');

    now.mockReturnValue(1_000_000 + 10 * 60 * 1000);
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    service.open(EMAIL, 'Confirm my appointment');

    expect(localStorage.getItem('demo-booking:simulated-email')).toBeNull();
  });
});
