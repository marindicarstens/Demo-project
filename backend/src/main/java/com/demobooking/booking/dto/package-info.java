/**
 * Booking's request and response shapes, matching docs/api/openapi.yaml.
 *
 * Known trade-off (CB2): this package and {@code booking} depend on each other. The services
 * build these DTOs, and the DTOs' {@code from} factories read {@code Appointment},
 * {@code AppointmentStatus} and {@code SentEmail}. The DTOs were split out so the API shapes can
 * be found in one place, not to be an independent layer, so the cycle is accepted.
 * PackageCycleTest does not see it, because it compares top-level packages only.
 */
package com.demobooking.booking.dto;
