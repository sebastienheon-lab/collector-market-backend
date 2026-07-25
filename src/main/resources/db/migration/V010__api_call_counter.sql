-- M3: daily eBay API call counter, used as a hard-stop guard against the OQ-1 free-tier quota.
-- One row per (date, api) pair; incremented on outbound send, decremented only if the request
-- never actually left (i.e. we backed out after reserving a slot but before sending).
CREATE TABLE api_call_counter (
    call_date   DATE         NOT NULL,
    api_name    VARCHAR(50)  NOT NULL,
    call_count  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (call_date, api_name)
);
