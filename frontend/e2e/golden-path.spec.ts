import { expect, test } from './fixtures';
import { E2E_BRANCH, findAvailableSlot, holdViaApi, SEEDED_EXISTING_CLIENT } from './helpers';
import { BrowsePage } from './pages/browse-page';
import { ManagePage } from './pages/manage-page';
import { NewAccountForm } from './pages/new-account-form';

const BRANCH = E2E_BRANCH.goldenPath;

// The golden path: search -> book -> see confirmation, for
// both entry flows, run against the real Docker Compose stack (see playwright.config.ts).
//
// The confirmation-request email opens in a genuinely separate browser tab
// (SimulatedInboxService.open -> window.open), and clicking its link navigates that same tab to
// /confirm/:token, which confirms automatically on load (no second in-app click - see
// ConfirmPage) and renders the confirmed screen right there - no second email, no further
// navigation. Only the initial booking opens a new tab.

test('New Account: search, book, and confirm', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 1);
  const browse = new BrowsePage(page);

  await browse.goto('new-client');
  await new NewAccountForm(page).submit({ fullName: 'Lindiwe Dube', email: `e2e-golden-${Date.now()}@example.com`, phone: '+27825550199' });
  await browse.pick(slot);
  const confirmationTab = await browse.bookAndOpenEmail();

  await expect(confirmationTab.getByText('SIMULATED — no message was actually sent')).toBeVisible();
  // A plain <a href> in the simulated email, not a button - see simulated-email-preview.html. It
  // navigates this same tab to /confirm/:token, which confirms automatically on load (no second
  // in-app click - see ConfirmPage) and renders the confirmed screen right there - no receipt
  // email, no further navigation.
  await confirmationTab.getByRole('link', { name: 'Confirm my appointment' }).click();

  await expect(new ManagePage(confirmationTab).status).toHaveText('Confirmed');
  await expect(confirmationTab.getByText(/^Reference BR-/)).toBeVisible();
});

test('New Account: reopening the email from the original tab after confirming elsewhere shows "already confirmed", not a stale error', async ({
  page,
  request,
}) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 2);
  const browse = new BrowsePage(page);

  await browse.goto('new-client');
  await new NewAccountForm(page).submit({ fullName: 'Reopen Test', email: `e2e-reopen-${Date.now()}@example.com`, phone: '+27825550199' });
  await browse.pick(slot);
  const confirmationTab = await browse.bookAndOpenEmail();

  // Confirm from the other tab, same as the golden path - this is the step the original tab
  // (`page`) has no visibility into.
  await confirmationTab.getByRole('link', { name: 'Confirm my appointment' }).click();
  await expect(new ManagePage(confirmationTab).status).toHaveText('Confirmed');

  // Back on the original tab, "Reopen the email" checks the appointment first (see
  // BrowsePage.reopenEmail()), so it reports the confirmation instead of re-showing a used link.
  await page.getByRole('button', { name: 'Reopen the email' }).click();
  await expect(page.getByText('already confirmed from the other tab')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reopen the email' })).toHaveCount(0);
});

test('Existing Client: search, book with a matched directory record, and confirm', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'EXISTING_CLIENT', BRANCH, 3);
  const browse = new BrowsePage(page);

  await browse.goto('existing-client');
  await browse.pick(slot);
  await browse.fillIdentity(SEEDED_EXISTING_CLIENT);
  const confirmationTab = await browse.bookAndOpenEmail();

  // The matched record's own name, never typed by the customer. It appears only in the simulated
  // email on the new tab, not on the booking page.
  await expect(confirmationTab.getByText(new RegExp(`Hi ${SEEDED_EXISTING_CLIENT.name}`))).toBeVisible();
});

test('refreshing the confirmation page shows expired-or-used guidance and a working /manage link', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 7);
  const hold = await holdViaApi(request, slot);
  const manage = new ManagePage(page);

  await page.goto(hold.confirmPath);
  await expect(manage.status).toHaveText('Confirmed');

  // The confirm token is single-use and is not re-issued, so the refresh cannot re-show the booking.
  await page.reload();
  await expect(page.getByText('This link has expired or was already used. Look up your booking to see its status.', { exact: true })).toBeVisible();
  await expect(page.getByText('book again')).toHaveCount(0);

  await page.getByRole('link', { name: 'Look up your booking' }).click();
  await expect(page).toHaveURL(/\/manage$/);
  await manage.fillLookup(hold.referenceCode, hold.email);
  await expect(manage.status).toHaveText('Confirmed');
});
