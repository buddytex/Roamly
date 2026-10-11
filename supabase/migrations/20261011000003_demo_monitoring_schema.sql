-- ============================================================================
-- ROAMLY: SUPABASE POSTGRESQL SCHEMA MIGRATION
-- Migration: 20261011000003_demo_monitoring_schema.sql
-- Description: Shared Tinkerspace Demo monitoring sessions and timestamped events
-- with Row Level Security (RLS) for Guides and Travellers.
-- ============================================================================

-- 1. SEED TINKERSPACE DEMO TRIP INTO public.trips (REUSE EXISTING TRIPS TABLE)
INSERT INTO public.trips (
    id,
    slug,
    title,
    destination,
    country,
    duration_days,
    status,
    summary
)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'trip_tinkerspace_demo',
    'Outside Tinkerspace',
    'Tinkerspace',
    'India',
    1,
    'Upcoming',
    'A short demonstration trip showing location-aware arrivals, trip progress, and belongings verification.'
)
ON CONFLICT (slug) DO UPDATE
SET title = EXCLUDED.title,
    destination = EXCLUDED.destination,
    country = EXCLUDED.country,
    duration_days = EXCLUDED.duration_days,
    summary = EXCLUDED.summary,
    updated_at = NOW();

-- 2. CREATE DEMO SESSIONS TABLE
CREATE TABLE IF NOT EXISTS public.demo_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(id) ON DELETE CASCADE,
    trip_slug TEXT NOT NULL DEFAULT 'trip_tinkerspace_demo',
    traveller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    traveller_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'NOT_STARTED' CHECK (
        status IN (
            'NOT_STARTED',
            'ACTIVE',
            'DEPARTED',
            'RETURNED',
            'AWAITING_BELONGINGS_CHECK',
            'COMPLETED',
            'LOCATION_UNAVAILABLE'
        )
    ),
    location_available BOOLEAN NOT NULL DEFAULT true,
    last_event TEXT,
    last_event_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    departure_time TIMESTAMPTZ,
    return_time TIMESTAMPTZ,
    belongings_checked BOOLEAN NOT NULL DEFAULT false,
    belongings_checked_time TIMESTAMPTZ,
    is_completed BOOLEAN NOT NULL DEFAULT false,
    completed_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_trip_traveller_session UNIQUE (trip_id, traveller_id)
);

-- 3. CREATE DEMO EVENTS TABLE (FOR TIMESTAMPED EVENT HISTORY)
CREATE TABLE IF NOT EXISTS public.demo_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES public.demo_sessions(id) ON DELETE CASCADE,
    trip_id UUID NOT NULL REFERENCES public.trips(id) ON DELETE CASCADE,
    traveller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (
        event_type IN (
            'DEMO_STARTED',
            'DEPARTED',
            'RETURNED',
            'AWAITING_BELONGINGS_CHECK',
            'BELONGINGS_CHECKED',
            'TRIP_COMPLETED',
            'LOCATION_UNAVAILABLE',
            'LOCATION_RESTORED',
            'DEMO_RESET'
        )
    ),
    description TEXT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_demo_sessions_trip ON public.demo_sessions(trip_id);
CREATE INDEX IF NOT EXISTS idx_demo_sessions_traveller ON public.demo_sessions(traveller_id);
CREATE INDEX IF NOT EXISTS idx_demo_sessions_status ON public.demo_sessions(status);
CREATE INDEX IF NOT EXISTS idx_demo_sessions_updated ON public.demo_sessions(updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_demo_events_session ON public.demo_events(session_id);
CREATE INDEX IF NOT EXISTS idx_demo_events_trip ON public.demo_events(trip_id);
CREATE INDEX IF NOT EXISTS idx_demo_events_traveller ON public.demo_events(traveller_id);
CREATE INDEX IF NOT EXISTS idx_demo_events_timestamp ON public.demo_events(event_timestamp DESC);

-- 5. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.demo_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demo_events ENABLE ROW LEVEL SECURITY;

-- DEMO SESSIONS POLICIES
-- A. Travellers can read their own session; Guides can read sessions for trips they guide or approved guides
DROP POLICY IF EXISTS "demo_sessions_select" ON public.demo_sessions;
CREATE POLICY "demo_sessions_select" ON public.demo_sessions
    FOR SELECT TO authenticated
    USING (
        traveller_id = auth.uid()
        OR public.is_approved_guide(auth.uid())
        OR public.is_trip_guide(auth.uid(), trip_id)
    );

-- B. Travellers can insert their own session (enforce traveller_id = auth.uid() to prevent impersonation)
DROP POLICY IF EXISTS "demo_sessions_insert" ON public.demo_sessions;
CREATE POLICY "demo_sessions_insert" ON public.demo_sessions
    FOR INSERT TO authenticated
    WITH CHECK (
        traveller_id = auth.uid()
    );

-- C. Travellers can update their own session; Guides managing the trip can update if needed
DROP POLICY IF EXISTS "demo_sessions_update" ON public.demo_sessions;
CREATE POLICY "demo_sessions_update" ON public.demo_sessions
    FOR UPDATE TO authenticated
    USING (
        traveller_id = auth.uid()
        OR public.is_approved_guide(auth.uid())
        OR public.is_trip_guide(auth.uid(), trip_id)
    );

-- DEMO EVENTS POLICIES
-- A. Travellers can read their own events; Guides can read events
DROP POLICY IF EXISTS "demo_events_select" ON public.demo_events;
CREATE POLICY "demo_events_select" ON public.demo_events
    FOR SELECT TO authenticated
    USING (
        traveller_id = auth.uid()
        OR public.is_approved_guide(auth.uid())
        OR public.is_trip_guide(auth.uid(), trip_id)
    );

-- B. Travellers insert their own events (enforcing traveller_id = auth.uid())
DROP POLICY IF EXISTS "demo_events_insert" ON public.demo_events;
CREATE POLICY "demo_events_insert" ON public.demo_events
    FOR INSERT TO authenticated
    WITH CHECK (
        traveller_id = auth.uid()
    );

-- 6. REALTIME PUBLICATION ENABLING
-- Enable Realtime publication for demo_sessions and demo_events if supabase_realtime publication exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.demo_sessions;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.demo_events;
    END IF;
END $$;
