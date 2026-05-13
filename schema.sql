-- nistula messaging schema (postgres)
-- gen_random_uuid() needs pgcrypto on some hosts

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- guests: one row per person
CREATE TABLE guests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name    TEXT NOT NULL,
    primary_email   TEXT,
    primary_phone   TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_guests_email_lower ON guests (lower(primary_email)) WHERE primary_email IS NOT NULL;
CREATE INDEX idx_guests_phone ON guests (primary_phone) WHERE primary_phone IS NOT NULL;

-- map whatsapp/airbnb/etc ids -> guest row (dedupe/merge in app)
CREATE TABLE guest_channel_identities (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id           UUID NOT NULL REFERENCES guests (id) ON DELETE CASCADE,
    channel            TEXT NOT NULL CHECK (channel IN ('whatsapp','booking_com','airbnb','instagram','direct')),
    external_user_id   TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (channel, external_user_id)
);

CREATE INDEX idx_guest_channel_guest ON guest_channel_identities (guest_id);

CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id        UUID NOT NULL REFERENCES guests (id) ON DELETE RESTRICT,
    booking_ref     TEXT,
    property_id     TEXT NOT NULL,
    check_in_date   DATE NOT NULL,
    check_out_date  DATE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'confirmed' CHECK (status IN ('inquiry','confirmed','checked_in','checked_out','cancelled')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_reservations_booking_ref ON reservations (booking_ref) WHERE booking_ref IS NOT NULL;

CREATE INDEX idx_reservations_guest ON reservations (guest_id);
CREATE INDEX idx_reservations_property_dates ON reservations (property_id, check_in_date, check_out_date);

-- thread UI scrolls this; reservation_id nullable for pre-booking spam
CREATE TABLE conversations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id         UUID NOT NULL REFERENCES guests (id) ON DELETE RESTRICT,
    reservation_id   UUID REFERENCES reservations (id) ON DELETE SET NULL,
    title            TEXT,
    status           TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','snoozed','archived')),
    last_message_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_guest ON conversations (guest_id);
CREATE INDEX idx_conversations_reservation ON conversations (reservation_id);

CREATE TYPE message_direction AS ENUM ('inbound', 'outbound');

CREATE TYPE outbound_composition AS ENUM (
    'human_composed',
    'ai_draft_auto_sent',
    'ai_draft_agent_sent_unedited',
    'ai_draft_agent_edited'
);

-- one table for all lines in/out. inbound gets classifier fields, outbound gets send metadata.
-- CHECKs stop you accidentally stuffing both sides at once.
CREATE TABLE messages (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id         UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    direction               message_direction NOT NULL,
    channel                 TEXT NOT NULL CHECK (channel IN ('whatsapp','booking_com','airbnb','instagram','direct')),
    body                    TEXT NOT NULL,
    sent_at                 TIMESTAMPTZ NOT NULL,
    external_message_id     TEXT,

    query_type              TEXT CHECK (query_type IS NULL OR query_type IN (
                                'pre_sales_availability',
                                'pre_sales_pricing',
                                'post_sales_checkin',
                                'special_request',
                                'complaint',
                                'general_enquiry'
                             )),
    ai_confidence_score     DOUBLE PRECISION CHECK (ai_confidence_score IS NULL OR (ai_confidence_score BETWEEN 0 AND 1)),

    outbound_composition    outbound_composition,
    ai_draft_snapshot       TEXT,
    agent_user_id           UUID,
    auto_send_policy_score  DOUBLE PRECISION,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_messages_inbound_fields CHECK (
        direction <> 'inbound' OR (outbound_composition IS NULL AND ai_draft_snapshot IS NULL AND agent_user_id IS NULL)
    ),
    CONSTRAINT ck_messages_outbound_fields CHECK (
        direction <> 'outbound' OR (query_type IS NULL AND ai_confidence_score IS NULL)
    ),
    CONSTRAINT ck_messages_outbound_composition CHECK (
        direction <> 'outbound' OR outbound_composition IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_messages_channel_external ON messages (channel, external_message_id)
    WHERE external_message_id IS NOT NULL;

CREATE INDEX idx_messages_conversation_sent ON messages (conversation_id, sent_at);
CREATE INDEX idx_messages_inbound_query ON messages (query_type) WHERE direction = 'inbound';

COMMENT ON TABLE messages IS 'inbound: query_type+score. outbound: composition + optional draft snapshot for audits.';
