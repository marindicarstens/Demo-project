/**
 * The simulated email adapter behind booking's NotificationSender port: what each email says and
 * where its link points. Depends on booking, never the other way round, so a real provider can
 * replace it without touching booking logic.
 */
package com.demobooking.notification;
