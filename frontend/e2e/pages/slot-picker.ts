import { Page } from '@playwright/test';

// The app keeps Angular's default LOCALE_ID (en-US), so its date fields show and parse M/D/YYYY
// whatever the browser or OS locale is. Formatting in UTC keeps the ISO date from shifting a day.
const APP_DATE_FORMAT = new Intl.DateTimeFormat('en-US', { year: 'numeric', month: 'numeric', day: 'numeric', timeZone: 'UTC' });

/** An ISO date (yyyy-MM-dd) as the app's date field displays it. */
export function dateFieldValue(isoDate: string): string {
  return APP_DATE_FORMAT.format(new Date(`${isoDate}T00:00:00Z`));
}

/** Types a date into an app-slot-picker and clicks the time. */
export async function pickSlot(page: Page, dateLabel: string, slot: { date: string; startTime: string }): Promise<void> {
  await page.getByLabel(dateLabel).fill(dateFieldValue(slot.date));
  await page.keyboard.press('Escape'); // close the datepicker popup so the slot grid is clickable
  await page.getByRole('button', { name: slot.startTime.slice(0, 5), exact: true }).click();
}
