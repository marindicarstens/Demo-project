import { expect, test } from './fixtures';
import { bookAndConfirmViaApi, E2E_BRANCH, findAvailableSlot, holdViaApi } from './helpers';
import { ManagePage } from './pages/manage-page';

const BRANCH = E2E_BRANCH.bookingManagement;

// Every way to manage an existing booking, named in docs/USER-GUIDE.md §5-6: cancel via the
// website, cancel via the emailed receipt link, and reschedule. Each test sets up its own confirmed
// appointment through the API (bookAndConfirmViaApi); golden-path.spec.ts covers the booking flow.

test('cancel from the website via "Look up my booking"', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 2);
  const booking = await bookAndConfirmViaApi(request, slot);
  const manage = new ManagePage(page);

  await manage.lookUp(booking.referenceCode, booking.email);

  await expect(manage.status).toHaveText('Confirmed');
  await manage.cancel();

  await expect(manage.status).toHaveText('Cancelled');
  await expect(page.getByRole('button', { name: 'Reschedule' })).toHaveCount(0);
});

test('cancel from the post-confirmation screen with the access token, after a confirm step', async ({ page, request }) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 8);
  const hold = await holdViaApi(request, slot);
  const manage = new ManagePage(page);

  await page.goto(hold.confirmPath);
  await expect(manage.status).toHaveText('Confirmed');

  // The first click only asks; backing out leaves the appointment alone.
  await page.getByRole('button', { name: 'Cancel appointment' }).click();
  await expect(page.getByText("Are you sure? This can't be undone.")).toBeVisible();
  await page.getByRole('button', { name: 'Keep appointment' }).click();
  await expect(manage.status).toHaveText('Confirmed');

  await manage.cancel();
  await expect(manage.status).toHaveText('Cancelled');
  await expect(page.getByRole('button', { name: 'Reschedule' })).toHaveCount(0);
});

test('cancel via the emailed receipt link, with a safe preview first', async ({ page, context, request }) => {
  const slot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 3);
  const hold = await holdViaApi(request, slot);

  // The receipt is reached the way a customer reaches it: from the confirmed screen.
  await page.goto(hold.confirmPath);
  await expect(new ManagePage(page).status).toHaveText('Confirmed');
  const [receiptTab] = await Promise.all([context.waitForEvent('page'), page.getByRole('button', { name: 'View receipt email' }).click()]);
  await receiptTab.waitForLoadState();
  await expect(receiptTab.getByText('SIMULATED — no message was actually sent')).toBeVisible();
  await receiptTab.getByRole('link', { name: 'Cancel my appointment' }).click();

  await expect(receiptTab.getByText('Cancel your appointment?')).toBeVisible();
  await expect(receiptTab.getByText(hold.referenceCode)).toBeVisible();
  const cancellationUrl = receiptTab.url();

  await receiptTab.getByRole('button', { name: 'Yes, cancel my appointment' }).click();
  await expect(receiptTab.getByText('Your appointment has been cancelled')).toBeVisible();

  // Reusing the same link afterwards is a safe no-op, not an error.
  await receiptTab.goto(cancellationUrl);
  await expect(receiptTab.getByText('This appointment is already cancelled')).toBeVisible();
});

test('reschedule to a different date and time, via the email-confirm flow', async ({ page, request }) => {
  const originalSlot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 4);
  const booking = await bookAndConfirmViaApi(request, originalSlot);
  // Searched only after the booking took originalSlot, so the two can never be the same slot.
  const newSlot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 9);
  const manage = new ManagePage(page);

  await manage.lookUp(booking.referenceCode, booking.email);
  await manage.chooseNewTime(newSlot);

  // Requesting the reschedule only reserves the new slot and sends a simulated "please confirm
  // this reschedule" email in a new tab - see manage-appointment.ts/.html. Nothing about the
  // appointment has moved yet at this point, mirroring golden-path.spec.ts's multi-tab technique for the
  // original booking hold/confirm flow.
  const rescheduleEmailTab = await manage.requestRescheduleAndOpenEmail();

  await expect(rescheduleEmailTab.getByText('SIMULATED — no message was actually sent')).toBeVisible();
  // A plain <a href> in the simulated email - navigates this same tab to
  // /reschedule-confirm/:token, it does not open yet another tab.
  await rescheduleEmailTab.getByRole('link', { name: 'Confirm this reschedule' }).click();

  await expect(rescheduleEmailTab.getByRole('button', { name: 'Confirm my new time' })).toBeVisible();
  await rescheduleEmailTab.getByRole('button', { name: 'Confirm my new time' }).click();

  // Only now, after the explicit click on the reschedule-confirm page, has the appointment
  // actually moved.
  await expect(rescheduleEmailTab.getByText('Your appointment has been moved')).toBeVisible();
  await expect(rescheduleEmailTab.getByText(new RegExp(`at ${newSlot.startTime.slice(0, 5)} on`))).toBeVisible();
  await expect(rescheduleEmailTab.getByText(new RegExp(`Reference ${booking.referenceCode}`))).toBeVisible();
});

test('a second reschedule while one is pending shows the server detail and the reopen-email action', async ({ page, request }) => {
  const originalSlot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 5);
  const booking = await bookAndConfirmViaApi(request, originalSlot);
  // Searched only after the booking took originalSlot, so the two can never be the same slot.
  const newSlot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 10);
  const manage = new ManagePage(page);

  await manage.lookUp(booking.referenceCode, booking.email);
  await manage.chooseNewTime(newSlot);
  await manage.requestRescheduleAndOpenEmail();

  // Leave the first request unconfirmed and ask again.
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(page.getByText('A reschedule is waiting for confirmation')).toBeVisible();
  // A slot takes one booking, so the pending request took newSlot off the grid. The same search
  // now returns the next free time, which the second request asks for.
  const otherSlot = await findAvailableSlot(request, 'NEW_CLIENT', BRANCH, 10);
  await manage.chooseNewTime(otherSlot);
  await page.getByRole('button', { name: 'Confirm new time' }).click();

  await expect(page.getByRole('alert')).toContainText('A reschedule is already pending confirmation');
  await expect(page.getByRole('button', { name: 'Reopen the email' })).toBeVisible();
});
