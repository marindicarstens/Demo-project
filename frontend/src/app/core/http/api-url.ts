import { environment } from '../../../environments/environment';

// The single place services read the API base URL from. It is the relative /api/v1, which nginx
// (and the dev-server proxy) forwards to the backend, so no interceptor is needed to rewrite it.
export const API = environment.apiBaseUrl;

/** @param path starts with a slash, e.g. '/branches' */
export function apiUrl(path: string): string {
  return `${API}${path}`;
}
