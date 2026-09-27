-- The column has held plaintext since encryption was removed, so the name now says what it holds.
ALTER TABLE customer RENAME COLUMN phone_encrypted TO phone;
ALTER TABLE existing_customer RENAME COLUMN phone_encrypted TO phone;
