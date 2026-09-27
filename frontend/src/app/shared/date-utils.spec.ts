import { maxBookableDate, toIsoDate } from './date-utils';

describe('date-utils', () => {
  it('formats the local calendar date as yyyy-MM-dd', () => {
    expect(toIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });

  it('allows booking up to 13 days ahead, across a month end', () => {
    expect(toIsoDate(maxBookableDate(new Date(2026, 8, 24)))).toBe('2026-10-07');
  });
});
