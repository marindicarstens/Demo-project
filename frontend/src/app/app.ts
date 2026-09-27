import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

function isBareLayout(router: Router): boolean {
  let route = router.routerState.snapshot.root;
  while (route.firstChild) {
    route = route.firstChild;
  }
  return !!route.data['bareLayout'];
}

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);

  // True on routes marked `data: { bareLayout: true }` (currently just /simulated-inbox) -
  // renders without this component's nav/footer, so that page can look like a wholly separate
  // email client rather than a screen inside the banking site.
  readonly bareLayout = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => isBareLayout(this.router)),
    ),
    { initialValue: isBareLayout(this.router) },
  );
}
