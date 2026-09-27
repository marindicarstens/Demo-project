-- Booking lifecycle schema for the New Account flow.
-- EXISTING_CUSTOMER and CUSTOMER.matched_existing_customer_id land with the directory
-- validation flow, not here - nothing in this migration needs them yet.

CREATE TABLE customer (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name        VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    phone_encrypted  VARCHAR(500),
    client_type      VARCHAR(20) NOT NULL CHECK (client_type IN ('NEW_CLIENT', 'EXISTING_CLIENT'))
);

CREATE TABLE appointment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id     UUID NOT NULL REFERENCES time_slot(id),
    customer_id      UUID NOT NULL REFERENCES customer(id),
    status           VARCHAR(25) NOT NULL
        CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    reference_code   VARCHAR(20) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_appointment_reference_code ON appointment (reference_code);
CREATE INDEX idx_appointment_time_slot ON appointment (time_slot_id);

-- type: CONFIRMATION_REQUEST (the magic-link "please confirm" email, sent at hold creation),
-- BOOKING_RECEIPT (sent on confirm, carries the cancellation token),
-- EXPIRY_NOTICE (sent by the sweep job if the hold lapses unconfirmed).
-- token_hash/token_expires_at/token_consumed_at are nullable because EXPIRY_NOTICE carries no
-- token at all - the NOTIFICATION columns are reused across types with type-appropriate meaning.
CREATE TABLE notification (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID NOT NULL REFERENCES appointment(id),
    type                VARCHAR(25) NOT NULL
        CHECK (type IN ('CONFIRMATION_REQUEST', 'BOOKING_RECEIPT', 'EXPIRY_NOTICE')),
    channel             VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL_SIMULATED')),
    simulated_payload   TEXT NOT NULL,
    token_hash          VARCHAR(64),
    token_expires_at    TIMESTAMPTZ,
    token_consumed_at   TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_appointment ON notification (appointment_id);
-- The sweep job's hot query: find unconsumed, expired CONFIRMATION_REQUEST tokens.
CREATE INDEX idx_notification_token_lookup ON notification (token_hash) WHERE token_hash IS NOT NULL;
