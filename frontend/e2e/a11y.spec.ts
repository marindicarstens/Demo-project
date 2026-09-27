import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { bookAndConfirmViaApi, E2E_BRANCH, findAvailableSlot } from './helpers';
import { BrowsePage } from './pages/browse-page';
import { ManagePage } from './pages/manage-page';

// WCAG 2.1 A and AA checks with axe-core on the key pages, in the state a customer sees them.
// No rule is excluded: any violation fails the test, listed by rule id and the offending nodes.

const BRANCH = E2E_BRANCH.accessibility;
const WCAG_21_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function expectNoViolations(page: Page): Promise<void> {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_21_AA).analyze();
  const summary = violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(summary).toEqual([]);
}

test('home page', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expectNoViolations(page);
});

test('New Account booking: the details step', async ({ page }) => {
  await page.goto('/browse/new-client');
  await expect(page.getByLabel('Full name')).toBeVisible();
  await expectNoViolations(page);
});

test('Existing Client booking: branch, service and the slot grid with a time picked', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'EXISTING_CLIENT', BRANCH, 1);
  const browse = new BrowsePage(page);
  await browse.goto('existing-client');
  await browse.pick(slot);
  await expect(page.getByLabel('ID number')).toBeVisible();
  await expectNoViolations(page);
});

test('booking lookup page', async ({ page }) => {
  await page.goto('/manage');
  await expect(page.getByLabel('Reference code')).toBeVisible();
  await expectNoViolations(page);
});

test('managing a confirmed booking', async ({ page, request }) => {
  const booking = await bookAndConfirmViaApi(request, await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 2));
  const manage = new ManagePage(page);
  await manage.lookUp(booking.referenceCode, booking.email);
  await expect(manage.status).toHaveText('Confirmed');
  await expectNoViolations(page);
});
