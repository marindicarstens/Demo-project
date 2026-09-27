// Load test against the two endpoints with the strictest performance targets:
// availability reads and booking writes, at 50 req/s sustained. Run nightly/main-only - kept
// off the per-PR path since it's slow and the target load is meaningful only against a stack
// close to production sizing, not per-PR CI runners.
//
// Usage: k6 run k6/load-test.js
// The default goes through the Nginx proxy on :4200, the same path the browser takes. Against
// another host: k6 run -e BASE_URL=https://example.test/api/v1 k6/load-test.js

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:4200/api/v1';

// Without this, k6 counts every 409 (a lost capacity race) in http_req_failed, so the gate below
// would fail on correct behaviour. A 429 still counts: run against a stack with raised rate
// limits (see ci.yml's nightly .env), since every request comes from one client IP.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const availabilityReadDuration = new Trend('availability_read_duration', true);
const bookingWriteDuration = new Trend('booking_write_duration', true);

export const options = {
  scenarios: {
    availability_reads: {
      executor: 'constant-arrival-rate',
      exec: 'readAvailability',
      rate: 40, // most of the 50 req/s target - reads dominate real traffic (browse >> book)
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
    booking_writes: {
      executor: 'constant-arrival-rate',
      exec: 'writeBooking',
      rate: 10, // the remaining 10 req/s - writes are the rarer, heavier action
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    // Targets: p95 < 300ms (availability read) / < 500ms (booking write).
    availability_read_duration: ['p(95)<300'],
    booking_write_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'], // a 409 (lost a capacity race) is expected under load, not a failure
  },
};

export function setup() {
  const branches = http.get(`${BASE_URL}/branches`).json();
  const serviceTypes = http.get(`${BASE_URL}/service-types?clientType=NEW_CLIENT`).json();
  const branchId = branches[0].id;
  const serviceTypeId = serviceTypes[0].id;

  // A wide pool of real, currently-available slots spread across the rolling window, so
  // concurrent writers land on different slots instead of all racing the same one - this is a
  // load test of read/write latency, not a repeat of BookingConcurrencyTest's contention test.
  const slotIds = [];
  const today = new Date();
  for (let dayOffset = 1; dayOffset <= 13 && slotIds.length < 500; dayOffset++) {
    const date = new Date(today);
    date.setDate(date.getDate() + dayOffset);
    const isoDate = date.toISOString().slice(0, 10);
    const slots = http.get(`${BASE_URL}/branches/${branchId}/availability?date=${isoDate}&serviceTypeId=${serviceTypeId}`).json();
    for (const slot of slots) {
      slotIds.push(slot.id);
    }
  }

  return { branchId, serviceTypeId, slotIds };
}

export function readAvailability(data) {
  const today = new Date();
  const dayOffset = 1 + Math.floor(Math.random() * 13);
  const date = new Date(today);
  date.setDate(date.getDate() + dayOffset);
  const isoDate = date.toISOString().slice(0, 10);

  const response = http.get(`${BASE_URL}/branches/${data.branchId}/availability?date=${isoDate}&serviceTypeId=${data.serviceTypeId}`);
  availabilityReadDuration.add(response.timings.duration);
  check(response, { 'availability read: 200': (r) => r.status === 200 });
}

export function writeBooking(data) {
  const slotId = data.slotIds[Math.floor(Math.random() * data.slotIds.length)];
  const uniqueSuffix = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    clientType: 'NEW_CLIENT',
    branchId: data.branchId,
    serviceTypeId: data.serviceTypeId,
    slotId,
    fullName: 'Load Test',
    email: `load-test-${uniqueSuffix}@example.com`,
    phone: '+27825550000',
  });

  const response = http.post(`${BASE_URL}/appointments`, payload, { headers: { 'Content-Type': 'application/json' } });
  bookingWriteDuration.add(response.timings.duration);
  // 201 = booked; 409 = lost a capacity race (expected, real behaviour under concurrent load,
  // not a performance failure) - see BookingConcurrencyTest for the dedicated correctness test.
  check(response, { 'booking write: 201 or 409': (r) => r.status === 201 || r.status === 409 });
}
