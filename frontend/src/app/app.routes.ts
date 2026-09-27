import { Routes } from '@angular/router';
import { HomePage } from './pages/home/home';

// Home is the landing page, so it ships in the initial bundle; every other page loads on demand.
export const routes: Routes = [
  { path: '', component: HomePage, title: 'Demo Booking' },
  {
    path: 'browse/:clientType',
    loadComponent: () => import('./pages/browse/browse').then((m) => m.BrowsePage),
    title: 'Book an appointment | Demo Booking',
  },
  {
    path: 'confirm/:token',
    loadComponent: () => import('./pages/confirm/confirm').then((m) => m.ConfirmPage),
    title: 'Confirm your appointment | Demo Booking',
  },
  {
    path: 'manage',
    loadComponent: () => import('./pages/lookup/lookup').then((m) => m.LookupPage),
    title: 'Look up my booking | Demo Booking',
  },
  {
    path: 'cancellations/:token',
    loadComponent: () => import('./pages/cancellation/cancellation').then((m) => m.CancellationPage),
    title: 'Cancel your appointment | Demo Booking',
  },
  {
    path: 'reschedule-confirm/:token',
    loadComponent: () => import('./pages/reschedule-confirm/reschedule-confirm').then((m) => m.RescheduleConfirmPage),
    title: 'Confirm your new time | Demo Booking',
  },
  // bareLayout renders without the site's nav and footer (see App): this page stands in for a
  // separate email client, not a page of the booking site.
  {
    path: 'simulated-inbox',
    loadComponent: () => import('./pages/simulated-inbox/simulated-inbox').then((m) => m.SimulatedInboxPage),
    title: 'Simulated inbox',
    data: { bareLayout: true },
  },
];
