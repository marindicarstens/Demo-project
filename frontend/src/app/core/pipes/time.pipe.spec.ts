import { TimePipe } from './time.pipe';

describe('TimePipe', () => {
  it('drops the seconds from an API time', () => {
    expect(new TimePipe().transform('09:30:00')).toBe('09:30');
  });
});
