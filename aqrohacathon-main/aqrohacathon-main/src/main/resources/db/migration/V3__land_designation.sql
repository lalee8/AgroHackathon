-- Torpağın hüquqi təyinatı və mənzərə (aqroturizm potensialı) əlamətləri
ALTER TABLE plots ADD COLUMN designation VARCHAR(20) NOT NULL DEFAULT 'EKIN';
ALTER TABLE plots ADD COLUMN scenic BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE plots SET designation = 'COXILLIK' WHERE category IN ('BAGCILIQ', 'UZUMCULUK');
UPDATE plots SET designation = 'OTLAQ' WHERE category IN ('HEYVANDARLIQ', 'ARICILIQ');
