import { HttpErrorResponse } from '@angular/common/http';
import { apiErrorDetail } from './api-error';

describe('apiErrorDetail', () => {
  it('reads detail, errors and suggestNewAccount from a ProblemDetail body', () => {
    const err = new HttpErrorResponse({
      status: 422,
      error: {
        type: 'about:blank',
        title: 'Unprocessable Content',
        status: 422,
        detail: 'No matching client record found for those details',
        errors: { email: 'must be a well-formed email address', ignored: 42 },
        suggestNewAccount: true,
      },
    });

    expect(apiErrorDetail(err)).toEqual({
      status: 422,
      detail: 'No matching client record found for those details',
      errors: { email: 'must be a well-formed email address' },
      suggestNewAccount: true,
    });
  });

  it('reads a string code, and ignores a code of any other type', () => {
    const conflict = new HttpErrorResponse({ status: 409, error: { status: 409, detail: 'Requested slot has no remaining capacity', code: 'SLOT_FULL' } });
    const odd = new HttpErrorResponse({ status: 409, error: { status: 409, code: 7 } });

    expect(apiErrorDetail(conflict)).toEqual({ status: 409, detail: 'Requested slot has no remaining capacity', code: 'SLOT_FULL' });
    expect(apiErrorDetail(odd)).toEqual({ status: 409 });
  });

  it('keeps only the status for a plain string body', () => {
    const err = new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' });

    expect(apiErrorDetail(err)).toEqual({ status: 502 });
  });

  it('reports status 0 and nothing else for a network error', () => {
    const err = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    expect(apiErrorDetail(err)).toEqual({ status: 0 });
  });
});
