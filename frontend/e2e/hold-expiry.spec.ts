import { expect, test } from './fixtures';
import { API_BASE_URL, E2E_BRANCH, findAvailableSlot } from './helpers';
import { BrowsePage } from './pages/browse-page';
import { NewAccountForm } from './pages/new-account-form';

const BRANCH = E2E_BRANCH.holdExpiry;

// An unconfirmed hold expires after the confirmation-token TTL, and the sweep (every 15 s) marks it
// EXPIRED. Reopening its email must then say so instead of re-showing a link that can no longer
// confirm. This test needs the compose stack's 1-minute TTL (APP_CONFIRMATION_TOKEN_TTL_MINUTES=1);
// with a longer one, the poll below times out.

test('reopening the email after the hold expired asks the customer to pick a time again', async ({ page, request }) => {
  // The 1-minute TTL plus up to one 15 s sweep outlasts test.slow()'s 90 s, so give it headroom.
  test.setTimeout(180_000);
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 11);
  const email = `e2e-expiry-${Date.now()}@example.com`;

  const browse = new BrowsePage(page);

  await browse.goto('new-client');
  await new NewAccountForm(page).submit({ fullName: 'Expiry Test', email, phone: '+27825550166' });
  await browse.pick(slot);
  const emailTab = await browse.bookAndOpenEmail();
  await emailTab.close();

  await expect(page.getByText(/^Confirm by \d{2}:\d{2}$/)).toBeVisible();
  const referenceCode = (await page.getByText(/^BR-/).textContent())?.trim() ?? '';
  expect(referenceCode).toMatch(/^BR-/);

  await expect
    .poll(
      async () => {
        const response = await request.get(`${API_BASE_URL}/appointments/lookup`, { params: { reference: referenceCode, email } });
        return response.ok() ? (await response.json()).status : `HTTP ${response.status()}`;
      },
      { timeout: 150_000, intervals: [5_000] },
    )
    .toBe('EXPIRED');

  await page.getByRole('button', { name: 'Reopen the email' }).click();
  await expect(page.getByRole('alert')).toHaveText('Your hold expired, so please pick a time again.');
  await expect(page.getByRole('button', { name: 'Reopen the email' })).toHaveCount(0);
});
