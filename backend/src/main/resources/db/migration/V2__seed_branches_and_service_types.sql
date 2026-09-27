-- Realistic demo data - see docs/SEED-DATA.md. Fictional; not real branch addresses or a real
-- service catalogue. Saturday's earlier closing time (13:00, vs. 16:30 Mon-Fri) is applied as a
-- constant in TimeSlotGenerationService rather than a second per-branch column here, since every
-- branch shares the same Saturday cutoff in this seed set - see that class for the reasoning.

INSERT INTO branch (name, address, city, phone, opens_at, closes_at, active) VALUES
    ('Sandton City Branch', 'Sandton City Shopping Centre, Rivonia Rd, Sandton', 'Johannesburg', '+27 11 555 0101', '08:30', '16:30', TRUE),
    ('Rosebank Branch', 'The Zone @ Rosebank, Cradock Ave, Rosebank', 'Johannesburg', '+27 11 555 0102', '08:30', '16:30', TRUE),
    ('Cape Town CBD Branch', '2 Long Street, Cape Town City Centre', 'Cape Town', '+27 21 555 0103', '08:30', '16:30', TRUE),
    ('Canal Walk Branch', 'Canal Walk Shopping Centre, Century City', 'Cape Town', '+27 21 555 0104', '08:30', '16:30', TRUE),
    ('Gateway Branch', 'Gateway Theatre of Shopping, Umhlanga Ridge', 'Durban', '+27 31 555 0105', '08:30', '16:30', TRUE),
    ('Menlyn Park Branch', 'Menlyn Park Shopping Centre, Atterbury Rd, Menlyn', 'Pretoria', '+27 12 555 0106', '08:30', '16:30', TRUE);

INSERT INTO service_type (name, duration_minutes, applicable_client_type) VALUES
    ('Open a new account', 30, 'NEW_CLIENT'),
    ('New client consultation', 20, 'NEW_CLIENT'),
    ('General enquiry', 15, 'BOTH'),
    ('Card services (replacement/activation)', 15, 'EXISTING_CLIENT'),
    ('Loan consultation', 30, 'EXISTING_CLIENT'),
    ('Dispute resolution', 30, 'EXISTING_CLIENT'),
    ('Account maintenance', 20, 'EXISTING_CLIENT'),
    ('Savings & investment consultation', 30, 'EXISTING_CLIENT');
