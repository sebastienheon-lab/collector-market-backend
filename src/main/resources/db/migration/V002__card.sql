-- §7.1: canonical card catalog
CREATE TABLE card (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_name     VARCHAR(100)  NOT NULL,
    year            SMALLINT      NOT NULL,
    brand           VARCHAR(100),
    set_name        VARCHAR(150),
    card_number     VARCHAR(30),
    sport_id        SMALLINT      NOT NULL REFERENCES sport(id),
    is_rookie       BOOLEAN       DEFAULT FALSE,
    parallel        VARCHAR(100),
    print_run       INT,
    created_at      TIMESTAMPTZ   DEFAULT now(),
    updated_at      TIMESTAMPTZ   DEFAULT now()
);