-- Full schema, squashed from the former V1-V12 into one baseline. Tables are created in
-- dependency order so no ALTER is needed to add a later FK back onto an earlier table.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE branch (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500) NOT NULL,
    city        VARCHAR(120) NOT NULL,
    phone       VARCHAR(40),
    opens_at    TIME NOT NULL,
    closes_at   TIME NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (opens_at < closes_at)
);

CREATE TABLE service_type (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255) NOT NULL,
    duration_minutes        INT NOT NULL CHECK (duration_minutes > 0),
    applicable_client_type  VARCHAR(20) NOT NULL
        CHECK (applicable_client_type IN ('NEW_CLIENT', 'EXISTING_CLIENT', 'BOTH'))
);

-- booked_count/capacity/version: the concurrency-safety design -
-- the CHECK constraint is the final defense against overbooking, independent of the
-- application-level optimistic lock (version) and the Redis fast-path lock added later.
CREATE TABLE time_slot (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id         UUID NOT NULL REFERENCES branch(id),
    service_type_id   UUID NOT NULL REFERENCES service_type(id),
    slot_date         DATE NOT NULL,
    start_time        TIME NOT NULL,
    capacity          INT NOT NULL CHECK (capacity > 0),
    booked_count      INT NOT NULL DEFAULT 0,
    version           BIGINT NOT NULL DEFAULT 0,
    CHECK (booked_count >= 0 AND booked_count <= capacity),
    UNIQUE (branch_id, service_type_id, slot_date, start_time)
);

CREATE INDEX idx_time_slot_branch_date ON time_slot (branch_id, slot_date);
CREATE INDEX idx_time_slot_service_type ON time_slot (service_type_id);

-- The existing-client directory - see docs/SEED-DATA.md § Existing-client demo directory.
-- id_number/account_number are plaintext: fine for this demo's fictional, already-documented
-- seed data, unlike a real customer directory.
CREATE TABLE existing_customer (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name        VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    id_number        VARCHAR(20) NOT NULL,
    account_number   VARCHAR(20) NOT NULL,
    phone            VARCHAR(500)
);

CREATE UNIQUE INDEX idx_existing_customer_email ON existing_customer (email);

CREATE TABLE customer (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name                      VARCHAR(255) NOT NULL,
    email                          VARCHAR(255) NOT NULL,
    phone                          VARCHAR(500),
    client_type                    VARCHAR(20) NOT NULL CHECK (client_type IN ('NEW_CLIENT', 'EXISTING_CLIENT')),
    matched_existing_customer_id   UUID REFERENCES existing_customer(id)
);

CREATE TABLE appointment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id     UUID NOT NULL REFERENCES time_slot(id),
    customer_id      UUID NOT NULL REFERENCES customer(id),
    status           VARCHAR(25) NOT NULL
        CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    reference_code   VARCHAR(20) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_appointment_reference_code ON appointment (reference_code);
CREATE INDEX idx_appointment_time_slot ON appointment (time_slot_id);

-- type: CONFIRMATION_REQUEST (the magic-link "please confirm" email, sent at hold creation),
-- BOOKING_RECEIPT (sent on confirm, carries the cancellation token),
-- EXPIRY_NOTICE (sent by the sweep job if the hold lapses unconfirmed),
-- RESCHEDULE_REQUEST (the reschedule confirm link; new_time_slot_id is the only type that needs
-- one, since it's the sole type describing a move to a slot other than the appointment's current
-- one).
-- token_hash/token_expires_at/token_consumed_at are nullable because EXPIRY_NOTICE carries no
-- token at all - the columns are reused across types with type-appropriate meaning.
-- version: confirmReschedule and the reschedule-expiry sweep both consume the same
-- RESCHEDULE_REQUEST row and otherwise write no shared row, so this is what makes one of them
-- conflict instead of both committing - see Notification.java.
CREATE TABLE notification (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID NOT NULL REFERENCES appointment(id),
    type                VARCHAR(25) NOT NULL
        CHECK (type IN ('CONFIRMATION_REQUEST', 'BOOKING_RECEIPT', 'EXPIRY_NOTICE', 'RESCHEDULE_REQUEST')),
    channel             VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL_SIMULATED')),
    simulated_payload   TEXT NOT NULL,
    token_hash          VARCHAR(64),
    token_expires_at    TIMESTAMPTZ,
    token_consumed_at   TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    new_time_slot_id    UUID REFERENCES time_slot(id),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notification_appointment ON notification (appointment_id);
-- The sweep job's hot query: find unconsumed, expired CONFIRMATION_REQUEST/RESCHEDULE_REQUEST tokens.
CREATE INDEX idx_notification_token_lookup ON notification (token_hash) WHERE token_hash IS NOT NULL;

-- Realistic demo data - see docs/SEED-DATA.md. Fictional; not real branch addresses, a real
-- service catalogue, or real people. Saturday's earlier closing time (13:00, vs. 16:30 Mon-Fri)
-- is applied as a constant in TimeSlotGenerationService rather than a second per-branch column
-- here, since every branch shares the same Saturday cutoff in this seed set - see that class for
-- the reasoning. Every service type shares the same 30-minute duration so all service types at a
-- branch generate the same aligned start-time grid - simpler for the demo than each service type
-- having its own slot width.
INSERT INTO branch (name, address, city, phone, opens_at, closes_at, active) VALUES
    ('Sandton City Branch', 'Sandton City Shopping Centre, Rivonia Rd, Sandton', 'Johannesburg', '+27 11 555 0101', '08:30', '16:30', TRUE),
    ('Rosebank Branch', 'The Zone @ Rosebank, Cradock Ave, Rosebank', 'Johannesburg', '+27 11 555 0102', '08:30', '16:30', TRUE),
    ('Cape Town CBD Branch', '2 Long Street, Cape Town City Centre', 'Cape Town', '+27 21 555 0103', '08:30', '16:30', TRUE),
    ('Canal Walk Branch', 'Canal Walk Shopping Centre, Century City', 'Cape Town', '+27 21 555 0104', '08:30', '16:30', TRUE),
    ('Gateway Branch', 'Gateway Theatre of Shopping, Umhlanga Ridge', 'Durban', '+27 31 555 0105', '08:30', '16:30', TRUE),
    ('Menlyn Park Branch', 'Menlyn Park Shopping Centre, Atterbury Rd, Menlyn', 'Pretoria', '+27 12 555 0106', '08:30', '16:30', TRUE);

INSERT INTO service_type (name, duration_minutes, applicable_client_type) VALUES
    ('Open a new account', 30, 'NEW_CLIENT'),
    ('New client consultation', 30, 'NEW_CLIENT'),
    ('General enquiry', 30, 'BOTH'),
    ('Card services (replacement/activation)', 30, 'EXISTING_CLIENT'),
    ('Loan consultation', 30, 'EXISTING_CLIENT'),
    ('Dispute resolution', 30, 'EXISTING_CLIENT'),
    ('Account maintenance', 30, 'EXISTING_CLIENT'),
    ('Savings & investment consultation', 30, 'EXISTING_CLIENT');

INSERT INTO existing_customer (full_name, email, id_number, account_number) VALUES
    ('Thandiwe Nkosi', 'thandiwe.demo@example.com', '9203015800082', '4051234567'),
    ('Johan van der Merwe', 'johan.demo@example.com', '8506120123089', '4059876543'),
    ('Aisha Patel', 'aisha.demo@example.com', '9711220456081', '4055551234'),
    ('Sipho Dlamini', 'sipho.demo@example.com', '8809085300084', '4053339876');
