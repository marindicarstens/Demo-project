import { routes } from './app.routes';

describe('app routes', () => {
  it('gives every route a title', () => {
    expect(routes.filter((route) => !route.title).map((route) => route.path)).toEqual([]);
  });

  it('loads every page except home on demand', () => {
    expect(routes.filter((route) => !route.loadComponent).map((route) => route.path)).toEqual(['']);
  });
});
