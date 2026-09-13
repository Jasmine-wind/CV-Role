-- Account identifiers are canonical at every write boundary:
--   username: surrounding whitespace removed and never email-shaped
--   email: surrounding whitespace removed and lower-cased
--
-- Do not guess repairs for ambiguous historical identities. Safe whitespace/case
-- normalization is applied only after proving it cannot merge rows. Email-shaped
-- usernames and invalid historical emails stop the migration for explicit review.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        WHERE regexp_replace(regexp_replace(username, '^[[:space:]]+', ''), '[[:space:]]+$', '') = ''
    ) THEN
        RAISE EXCEPTION 'V37 migration stopped: blank username after normalization';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM users
        WHERE regexp_replace(regexp_replace(username, '^[[:space:]]+', ''), '[[:space:]]+$', '')
            ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ) THEN
        RAISE EXCEPTION 'V37 migration stopped: email-shaped historical username requires explicit repair';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM users
        WHERE lower(regexp_replace(regexp_replace(email, '^[[:space:]]+', ''), '[[:space:]]+$', ''))
            !~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ) THEN
        RAISE EXCEPTION 'V37 migration stopped: invalid historical email requires explicit repair';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM users
        GROUP BY regexp_replace(regexp_replace(username, '^[[:space:]]+', ''), '[[:space:]]+$', '')
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V37 migration stopped: username normalization would merge accounts';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM users
        GROUP BY lower(regexp_replace(regexp_replace(email, '^[[:space:]]+', ''), '[[:space:]]+$', ''))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V37 migration stopped: email normalization would merge accounts';
    END IF;
END $$;

UPDATE users
SET username = regexp_replace(regexp_replace(username, '^[[:space:]]+', ''), '[[:space:]]+$', ''),
    email = lower(regexp_replace(regexp_replace(email, '^[[:space:]]+', ''), '[[:space:]]+$', '')),
    updated_at = CURRENT_TIMESTAMP
WHERE username IS DISTINCT FROM regexp_replace(regexp_replace(username, '^[[:space:]]+', ''), '[[:space:]]+$', '')
   OR email IS DISTINCT FROM lower(regexp_replace(regexp_replace(email, '^[[:space:]]+', ''), '[[:space:]]+$', ''));

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_canonical CHECK (
        username <> ''
        AND username !~ '^[[:space:]]'
        AND username !~ '[[:space:]]$'
        AND username !~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    ADD CONSTRAINT ck_users_email_canonical CHECK (
        email = lower(email)
        AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    );

-- Functional indexes remain defensive even if a future write path is evaluated
-- before a canonical-form CHECK constraint.
CREATE UNIQUE INDEX uk_users_username_canonical
    ON users ((regexp_replace(regexp_replace(username, '^[[:space:]]+', ''), '[[:space:]]+$', '')));

CREATE UNIQUE INDEX uk_users_email_canonical
    ON users ((lower(regexp_replace(regexp_replace(email, '^[[:space:]]+', ''), '[[:space:]]+$', ''))));
