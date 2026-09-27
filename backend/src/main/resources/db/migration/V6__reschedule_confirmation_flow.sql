-- Reschedule now requires an email-click confirmation, exactly like the original booking
-- hold/confirm flow, instead of applying atomically in one PATCH (a deliberate reversal of the
-- earlier atomic-move design). RESCHEDULE_REQUEST reuses notification's existing token_hash/token_expires_at/
-- token_consumed_at columns exactly like CONFIRMATION_REQUEST already does - see this table's
-- original comment in V3. new_time_slot_id is the one genuinely new piece of data: which slot the
-- customer asked to move to, needed so the confirm step (and the expiry sweep, if the link
-- lapses) knows which held reservation to act on. No other notification type needs a slot
-- reference - the others all describe the appointment's already-current slot.

-- Constraint name verified via psql against the running dev database (\d notification) rather
-- than assumed, since Postgres's auto-generated name isn't guaranteed by anything but convention.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('CONFIRMATION_REQUEST', 'BOOKING_RECEIPT', 'EXPIRY_NOTICE', 'RESCHEDULE_REQUEST'));

ALTER TABLE notification ADD COLUMN new_time_slot_id UUID REFERENCES time_slot(id);
