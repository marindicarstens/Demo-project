import { test as base } from '@playwright/test';
import { cancelCreatedBookings, createdBookings } from './helpers';

export { expect } from '@playwright/test';

/** Records the holds the browser creates and cancels every booking the test made once it ends,
 * pass or fail - see cancelCreatedBookings. The API helpers record their own bookings.
 *
 * The browser's booking request is routed through Playwright and passed on unchanged, because
 * that is the reliable way to read its response: a response event's body can already be gone by
 * the time it is read, once the app has opened the email tab. */
export const test = base.extend<{ cleanUpBookings: void }>({
  cleanUpBookings: [
    async ({ context, request }, use) => {
      await context.route('**/api/v1/appointments', async (route) => {
        if (route.request().method() !== 'POST') {
          await route.fallback();
          return;
        }
        const response = await route.fetch();
        if (response.status() === 201) {
          const hold = await response.json();
          const { email } = route.request().postDataJSON();
          createdBookings.push({ appointmentId: hold.appointmentId, referenceCode: hold.referenceCode, email });
        }
        await route.fulfill({ response });
      });
      await use();
      await cancelCreatedBookings(request);
    },
    { auto: true },
  ],
});
