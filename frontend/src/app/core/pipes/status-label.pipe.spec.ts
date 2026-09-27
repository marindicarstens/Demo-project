import { StatusLabelPipe } from './status-label.pipe';

describe('StatusLabelPipe', () => {
  const pipe = new StatusLabelPipe();

  it('maps every status to customer-facing wording', () => {
    expect(pipe.transform('PENDING_CONFIRMATION')).toBe('Awaiting confirmation');
    expect(pipe.transform('CONFIRMED')).toBe('Confirmed');
    expect(pipe.transform('CANCELLED')).toBe('Cancelled');
    expect(pipe.transform('EXPIRED')).toBe('Expired');
  });
});
