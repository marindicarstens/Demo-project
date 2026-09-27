import { API, apiUrl } from './api-url';

describe('apiUrl', () => {
  it('prefixes the path with the relative API base URL', () => {
    expect(API).toBe('/api/v1');
    expect(apiUrl('/branches')).toBe('/api/v1/branches');
  });
});
