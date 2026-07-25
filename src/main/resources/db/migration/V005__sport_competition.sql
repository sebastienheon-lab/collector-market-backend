-- §7.8: named recurring competitions. Olympics -> sport 'multi' (spans all sports).
CREATE TABLE sport_competition (
    id          SMALLINT     PRIMARY KEY,
    sport_id    SMALLINT     NOT NULL REFERENCES sport(id),
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (sport_id, name)
);

INSERT INTO sport_competition (id, sport_id, name, is_active) VALUES
    (1, (SELECT id FROM sport WHERE code = 'baseball'),   'MLB Postseason',            TRUE),
    (2, (SELECT id FROM sport WHERE code = 'football'),   'NFL Playoffs',              TRUE),
    (3, (SELECT id FROM sport WHERE code = 'basketball'), 'NBA Playoffs',              TRUE),
    (4, (SELECT id FROM sport WHERE code = 'soccer'),     'FIFA World Cup',            TRUE),
    (5, (SELECT id FROM sport WHERE code = 'soccer'),     'UEFA Champions League',     TRUE),
    (6, (SELECT id FROM sport WHERE code = 'multi'),      'Olympics',                  TRUE);
