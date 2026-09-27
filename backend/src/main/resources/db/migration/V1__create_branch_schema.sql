-- Schema for the browse flow. APPOINTMENT/CUSTOMER/NOTIFICATION/EXISTING_CUSTOMER land later.

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
