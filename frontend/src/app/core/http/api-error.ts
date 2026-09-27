import { HttpErrorResponse } from '@angular/common/http';

/** The parts of the backend's ProblemDetail (RFC 9457) body the UI acts on. */
export interface ApiErrorDetail {
  /** 0 when the request never got a response (network error, CORS, aborted). */
  status: number;
  detail?: string;
  /** Field name to message, sent on 400 validation failures. */
  errors?: Record<string, string>;
  /** A stable, machine-readable reason, e.g. SLOT_FULL on a 409 for a full slot. */
  code?: string;
  /** Set on the 422 directory-validation failure, to offer the new-account fallback. */
  suggestNewAccount?: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/**
 * Normalises an HTTP error into the fields the UI needs. Only a ProblemDetail object body
 * contributes a detail: any other body (a plain string, an HTML gateway page, or the ProgressEvent
 * of a network error) is not written for customers, so it is dropped rather than shown.
 */
export function apiErrorDetail(err: HttpErrorResponse): ApiErrorDetail {
  const result: ApiErrorDetail = { status: err.status };
  const body: unknown = err.error;
  if (!isRecord(body)) {
    return result;
  }
  const detail = body['detail'];
  if (typeof detail === 'string') {
    result.detail = detail;
  }
  const code = body['code'];
  if (typeof code === 'string') {
    result.code = code;
  }
  const errors = body['errors'];
  if (isRecord(errors)) {
    result.errors = Object.fromEntries(
      Object.entries(errors).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
    );
  }
  if (body['suggestNewAccount'] === true) {
    result.suggestNewAccount = true;
  }
  return result;
}
