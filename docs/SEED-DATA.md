# Seed Data

Concrete, realistic-looking demo data for local development and the review — loaded via Flyway
migration on first startup (`backend/src/main/resources/db/migration/`). None of this is
real: branch names reference well-known South African shopping centres/areas as a realistic
naming convention, not actual branch addresses of any real institution, and the "existing client"
records are entirely fictional. **Treat everything below as placeholder data you can freely
replace** — it exists to make the app demonstrable end-to-end, not as a considered business
decision about real branch locations, hours, or a real service catalogue.

---

## Branches

| Name | Address | City | Hours (Mon–Fri) | Hours (Sat) |
|---|---|---|---|---|
| Sandton City Branch | Sandton City Shopping Centre, Rivonia Rd, Sandton | Johannesburg | 08:30–16:30 | 08:30–13:00 |
| Rosebank Branch | The Zone @ Rosebank, Cradock Ave, Rosebank | Johannesburg | 08:30–16:30 | 08:30–13:00 |
| Cape Town CBD Branch | 2 Long Street, Cape Town City Centre | Cape Town | 08:30–16:30 | 08:30–13:00 |
| Canal Walk Branch | Canal Walk Shopping Centre, Century City | Cape Town | 08:30–16:30 | 08:30–13:00 |
| Gateway Branch | Gateway Theatre of Shopping, Umhlanga Ridge | Durban | 08:30–16:30 | 08:30–13:00 |
| Menlyn Park Branch | Menlyn Park Shopping Centre, Atterbury Rd, Menlyn | Pretoria | 08:30–16:30 | 08:30–13:00 |

All branches closed Sunday. Same hours across all branches for demo simplicity — a real deployment
would seed per-branch hours independently; the schema already supports this.

## Service types

| Name | Applicable to | Duration |
|---|---|---|
| Open a new account | New Account | 30 min |
| New client consultation | New Account | 30 min |
| General enquiry | Both | 30 min |
| Card services (replacement/activation) | Existing Client | 30 min |
| Loan consultation | Existing Client | 30 min |
| Dispute resolution | Existing Client | 30 min |
| Account maintenance | Existing Client | 30 min |
| Savings & investment consultation | Existing Client | 30 min |

Every service type deliberately shares the same 30-minute duration (see `V8__uniform_service_type_duration.sql`)
so all service types at a branch generate the same aligned start-time grid - simpler for the demo
than each service type having its own slot width.

## Existing-client demo directory

Seeded for testing the directory-validation flow — also listed in
[README.md § Demo data](../README.md#demo-data), kept in sync with this file.

| Name | Email | ID number | Account number |
|---|---|---|---|
| Thandiwe Nkosi | `thandiwe.demo@example.com` | `9203015800082` | `4051234567` |
| Johan van der Merwe | `johan.demo@example.com` | `8506120123089` | `4059876543` |
| Aisha Patel | `aisha.demo@example.com` | `9711220456081` | `4055551234` |
| Sipho Dlamini | `sipho.demo@example.com` | `8809085300084` | `4053339876` |

All three fields you enter (email, ID number and account number) must match the same row — a
partial match (e.g. right email, wrong account number) is treated identically to no match at all,
by design: the response never reveals which field was wrong, so the endpoint can't be used to
enumerate valid records.

## Time slots

Not seeded as static data — generated for a rolling window of 14 calendar days including today,
at startup and then daily at 01:00 (server time), per branch/service-type combination, using each
service type's duration and the branch's operating hours (no slots on Sundays). Each slot has a
fixed capacity of exactly 1 appointment - deliberately simplified for the demo (a slot is either
bookable or it isn't, with no "2 of 3 remaining" concept) until real capacity planning data exists.
