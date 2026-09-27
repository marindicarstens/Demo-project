/**
 * The booking lifecycle: holds, confirmation, reschedule (itself a request/confirm pair, mirroring
 * the booking hold/confirm flow), cancellation, and the concurrency-safe capacity invariant.
 */
package com.demobooking.booking;
