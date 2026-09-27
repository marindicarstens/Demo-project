// Bookings run on a 14-day rolling window (today plus 13 days), matching TimeSlotGenerationService.
const BOOKING_WINDOW_EXTRA_DAYS = 13;

/** The local calendar date as yyyy-MM-dd, the format the API expects for a LocalDate. */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** The last date a slot can be booked for, counted from `from`. */
export function maxBookableDate(from: Date = new Date()): Date {
  const max = new Date(from);
  max.setDate(max.getDate() + BOOKING_WINDOW_EXTRA_DAYS);
  return max;
}
