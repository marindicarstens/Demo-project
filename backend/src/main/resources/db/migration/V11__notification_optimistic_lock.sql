-- Optimistic lock for notification, mirroring V10's on appointment. confirmReschedule and the
-- reschedule-expiry sweep both consume the same RESCHEDULE_REQUEST row, and neither otherwise
-- writes a shared row, so without this both could commit - see Notification.java.
ALTER TABLE notification ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
