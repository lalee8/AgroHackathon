-- İstifadəçi növü (investor / sahibkar), mygov ID identifikatoru və sahibkar profili

ALTER TABLE users ADD user_type VARCHAR(20);
ALTER TABLE users ADD mygov_sub VARCHAR(64);
CREATE UNIQUE INDEX uq_users_mygov ON users(mygov_sub);
UPDATE users SET user_type = 'INVESTOR' WHERE role = 'INVESTOR';

CREATE TABLE entrepreneur_profiles (
    user_id      BIGINT        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    district     VARCHAR(30),
    sector       VARCHAR(30),
    land_status  VARCHAR(20),
    land_ha      DECIMAL(10,2),
    need_amount  DECIMAL(16,2),
    idea         VARCHAR(400),
    updated_at   TIMESTAMP     NOT NULL
);
