-- §7.2: sport lookup table (not a Postgres ENUM — see planning.md OQ-12)
CREATE TABLE sport (
    id            SMALLINT     PRIMARY KEY,
    code          VARCHAR(30)  NOT NULL UNIQUE,
    display_name  VARCHAR(50)  NOT NULL,
    icon_key      VARCHAR(50),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO sport (id, code, display_name, icon_key, is_active) VALUES
    (1, 'baseball',   'Baseball',   'baseball',   TRUE),
    (2, 'football',   'Football',   'football',   TRUE),
    (3, 'basketball', 'Basketball', 'basketball', TRUE),
    (4, 'hockey',     'Hockey',     'hockey',     TRUE),
    (5, 'soccer',     'Soccer',     'soccer',     TRUE),
    (6, 'multi',      'Multi-Sport', NULL,        TRUE);