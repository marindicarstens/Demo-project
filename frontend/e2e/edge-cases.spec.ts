import { expect, test } from './fixtures';
import { E2E_BRANCH, exhaustSlotCapacity, findAvailableSlot } from './helpers';
import { BrowsePage } from './pages/browse-page';
import { NewAccountForm } from './pages/new-account-form';
import { dateFieldValue } from './pages/slot-picker';

const BRANCH = E2E_BRANCH.edgeCases;

// Two non-golden-path scenarios: invalid input, and a slot becoming full mid-flow (a real race
// with another customer, not a client-side bug).

test('invalid input: an empty New Account details form blocks progress, not a broken booking', async ({ page }) => {
  const browse = new BrowsePage(page);
  await browse.goto('new-client');
  await new NewAccountForm(page).continue();

  // Client-side validation (Validators.required) keeps the customer on step 1 - the branch
  // search step never appears, and nothing was submitted to the backend.
  await expect(browse.branchField).toHaveCount(0);
  await expect(page.getByText('Enter your full name')).toBeVisible();
  await expect(page.getByText('1. Your details')).toBeVisible();
});

test('a slot taken by someone else mid-flow shows a clear error and lets the customer retry', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 6);
  const browse = new BrowsePage(page);

  await browse.goto('new-client');
  await new NewAccountForm(page).submit({ fullName: 'Sipho Mokoena', email: `e2e-race-${Date.now()}@example.com`, phone: '+27825550188' });
  await browse.pick(slot);

  // The race: other customers claim every remaining spot on this exact slot after the browser
  // already selected it but before this one submits - a real capacity conflict, not simulated
  // client-side. See BookingConcurrencyTest for the same invariant proven under real concurrency.
  await exhaustSlotCapacity(request, slot);

  await browse.bookButton.click();

  await expect(page.getByText('That time was just taken by someone else - please pick another.')).toBeVisible();
  // The stale selection is cleared, not left pointing at a slot that no longer has room.
  await expect(browse.bookButton).toHaveCount(0);
});

test('an existing-client directory mismatch can continue as a New Account, keeping branch and date', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'EXISTING_CLIENT', BRANCH, 3);
  const browse = new BrowsePage(page);

  await browse.goto('existing-client');
  await browse.pick(slot);

  // Well-formed details that match no one in the seeded directory.
  await browse.fillIdentity({ email: `e2e-mismatch-${Date.now()}@example.com`, idNumber: '8001015009087', accountNumber: '1000000001' });
  await browse.bookButton.click();

  await expect(page.getByRole('alert')).toContainText("We couldn't find a client record matching those details.");
  await page.getByRole('button', { name: 'Continue as a New Account instead' }).click();

  await new NewAccountForm(page).submit({ fullName: 'Mismatch Fallback', email: `e2e-fallback-${Date.now()}@example.com`, phone: '+27825550177' });

  await expect(page.getByText('New Account', { exact: true })).toBeVisible();
  await expect(browse.branchField).toHaveValue(new RegExp(slot.branchName));
  // The service type is re-picked from the New Account catalog; the date step then reappears
  // with the date picked before.
  await expect(page.getByText('Choose a service')).toBeVisible();
  await page.getByRole('listbox', { name: 'Service type' }).getByRole('option').first().click();
  await expect(browse.dateField).toHaveValue(dateFieldValue(slot.date));
});
