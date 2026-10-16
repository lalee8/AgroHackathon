-- Müraciət edən tərəfin hüquqi statusu (fiziki şəxs, hüquqi şəxs, xarici vətəndaş), yerli sakin əlaməti
-- və sahibkar profilində torpaq vəziyyəti: azad edilmiş ərazilərdə şəxsi torpaq mülkiyyəti yoxdur, torpağı dövlət
-- müsabiqə yolu ilə icarəyə verir.

ALTER TABLE users ADD legal_type VARCHAR(20);
ALTER TABLE users ADD voen VARCHAR(10);
ALTER TABLE users ADD company_name VARCHAR(200);
ALTER TABLE users ADD company_form VARCHAR(30);
ALTER TABLE users ADD rep_position VARCHAR(120);
ALTER TABLE users ADD citizenship VARCHAR(60);
ALTER TABLE users ADD passport_no VARCHAR(20);
ALTER TABLE users ADD local_resident BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE users ADD home_district VARCHAR(30);
ALTER TABLE users ADD pref_lang VARCHAR(5);

UPDATE users SET legal_type = 'INDIVIDUAL' WHERE role IN ('INVESTOR', 'BUYER');

UPDATE entrepreneur_profiles SET land_status = 'LEASED' WHERE land_status = 'OWN';
UPDATE entrepreneur_profiles SET land_status = 'APPLYING' WHERE land_status = 'LEASE';

-- Müraciətdə xarici vətəndaşın rekvizitləri
ALTER TABLE applications ADD citizenship VARCHAR(60);
ALTER TABLE applications ADD passport_no VARCHAR(20);
