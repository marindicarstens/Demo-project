-- Drops the keyed-HMAC design (KeyedHasher, removed) for the existing-client directory's ID
-- number and account number - plaintext is fine for this
-- demo's fictional, already-documented seed data. Renaming the columns (not just restoring
-- plaintext under the old "_hash" names) since a getIdNumberHash() that returns plaintext would
-- be actively misleading to read, unlike e.g. Customer.phoneEncrypted's naming-leftover case.
ALTER TABLE existing_customer RENAME COLUMN id_number_hash TO id_number;
ALTER TABLE existing_customer RENAME COLUMN account_number_hash TO account_number;

-- Real plaintext values for the four seeded rows - already documented (and used to test against)
-- in docs/SEED-DATA.md § Existing-client demo directory; only the stored representation changes.
-- Written before narrowing the column type below: it's still VARCHAR(64) here, holding the old
-- hash values these UPDATEs are replacing - narrowing first would reject those hex digests.
UPDATE existing_customer SET id_number = '9203015800082', account_number = '4051234567' WHERE email = 'thandiwe.demo@example.com';
UPDATE existing_customer SET id_number = '8506120123089', account_number = '4059876543' WHERE email = 'johan.demo@example.com';
UPDATE existing_customer SET id_number = '9711220456081', account_number = '4055551234' WHERE email = 'aisha.demo@example.com';
UPDATE existing_customer SET id_number = '8809085300084', account_number = '4053339876' WHERE email = 'sipho.demo@example.com';

ALTER TABLE existing_customer ALTER COLUMN id_number TYPE VARCHAR(20);
ALTER TABLE existing_customer ALTER COLUMN account_number TYPE VARCHAR(20);
