-- Optimistic lock for appointment, matching time_slot's existing version column (V1 migration) -
-- see Appointment.java's class Javadoc for the confirm-vs-expiry-sweep race this closes.
ALTER TABLE appointment ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
