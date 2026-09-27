-- The existing-client directory, and the FK linking a matched booking's customer record back to
-- it - see docs/SEED-DATA.md § Existing-client demo directory.

CREATE TABLE existing_customer (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name            VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    id_number_hash       VARCHAR(64) NOT NULL,
    account_number_hash  VARCHAR(64) NOT NULL,
    phone_encrypted      VARCHAR(500)
);

CREATE UNIQUE INDEX idx_existing_customer_email ON existing_customer (email);

ALTER TABLE customer ADD COLUMN matched_existing_customer_id UUID REFERENCES existing_customer(id);

-- id_number_hash/account_number_hash are HMAC-SHA256(app.pii-hmac-secret, value) hex digests -
-- never plaintext. Computed with the secret committed as
-- app.pii-hmac-secret's default in application.yml/application-docker.yml/.env.example: unlike
-- the JWT signing secret, this one must stay byte-identical between seed time and query time for
-- the demo directory to keep matching, so every profile deliberately shares the same value.
INSERT INTO existing_customer (full_name, email, id_number_hash, account_number_hash) VALUES
    ('Thandiwe Nkosi', 'thandiwe.demo@example.com',
        '00535f6500cb71dfb14cd0700eea9490efabfe647637489cc2ec20d7f971442e',
        '9b3dade80d3709a93e3f321428d4f1b864fa5314684ab97d59874a963232fc37'),
    ('Johan van der Merwe', 'johan.demo@example.com',
        '7593aa579cc225c7eb2ab1ce6eb357a45b4ab92acd70139dabcd89c65ee226d1',
        '133c89ff7718223045a918ee10149898ad0f7ff668d6237aabad76ddbe119f95'),
    ('Aisha Patel', 'aisha.demo@example.com',
        '34bd896c0ee9e56bc9f4047cdcb7ab007f18a3f42e2b9671f6eb17ceb30b4d3c',
        '41114a3f91512298d6d47e2b1242fc97f452ff1e8e73baa553548d75ea893d95'),
    ('Sipho Dlamini', 'sipho.demo@example.com',
        '3eddf3636c0fb84d9cc2ca4338a802a3c3a112cb1e3ed5e64fb3d334530f28a6',
        '1eabb0bea3192d1cb71e39556f3970f5f7e911e4b36c2bafb862e1688f58ebf8');
