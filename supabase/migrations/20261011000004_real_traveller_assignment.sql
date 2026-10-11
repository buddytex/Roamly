-- ============================================================================
-- ROAMLY: SUPABASE POSTGRESQL SCHEMA MIGRATION
-- Migration: 20261011000004_real_traveller_assignment.sql
-- Description: Connect registered Travellers to authorized Guides via trips,
-- enforce strict Row Level Security (RLS) so Guides only access authorized
-- Travellers, and update handle_new_user() trigger for automatic trip roster enrollment.
-- ============================================================================

-- 1. UPDATE handle_new_user() TRIGGER TO AUTOMATICALLY ENROLL USERS INTO CANONICAL TRIPS
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    requested_role TEXT;
    parsed_full_name TEXT;
BEGIN
    -- Extract requested role from metadata, default to 'traveller'
    requested_role := LOWER(TRIM(COALESCE(NEW.raw_user_meta_data->>'role', 'traveller')));
    IF requested_role <> 'guide' THEN
        requested_role := 'traveller';
    END IF;

    -- Extract full name
    parsed_full_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        split_part(NEW.email, '@', 1)
    );

    -- Upsert profile
    INSERT INTO public.profiles (id, full_name, role, approval_status, email)
    VALUES (
        NEW.id,
        parsed_full_name,
        requested_role,
        'approved',
        NEW.email
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        full_name = EXCLUDED.full_name,
        updated_at = NOW();

    -- Assign user to canonical trip rosters
    IF requested_role = 'traveller' THEN
        INSERT INTO public.trip_members (trip_id, user_id, membership_role)
        SELECT id, NEW.id, 'traveller'
        FROM public.trips
        WHERE slug IN ('trip_singapore_2026', 'trip_tinkerspace_demo')
        ON CONFLICT (trip_id, user_id) DO NOTHING;
    ELSIF requested_role = 'guide' THEN
        INSERT INTO public.trip_members (trip_id, user_id, membership_role)
        SELECT id, NEW.id, 'lead_guide'
        FROM public.trips
        WHERE slug IN ('trip_singapore_2026', 'trip_tinkerspace_demo')
        ON CONFLICT (trip_id, user_id) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- 2. BACKFILL CANONICAL ROSTERS FOR EXISTING ACCOUNTS
-- Ensure all existing travellers are associated with the canonical trips
INSERT INTO public.trip_members (trip_id, user_id, membership_role)
SELECT t.id, p.id, 'traveller'
FROM public.profiles p
CROSS JOIN public.trips t
WHERE p.role = 'traveller'
  AND t.slug IN ('trip_singapore_2026', 'trip_tinkerspace_demo')
ON CONFLICT (trip_id, user_id) DO NOTHING;

-- Ensure all existing guides are associated with the canonical trips
INSERT INTO public.trip_members (trip_id, user_id, membership_role)
SELECT t.id, p.id, 'lead_guide'
FROM public.profiles p
CROSS JOIN public.trips t
WHERE p.role = 'guide'
  AND t.slug IN ('trip_singapore_2026', 'trip_tinkerspace_demo')
ON CONFLICT (trip_id, user_id) DO NOTHING;

-- 3. REINFORCE STRICT ROW LEVEL SECURITY ON PROFILES
-- Travellers may ONLY read their own profile.
-- Guides may ONLY read profiles of Travellers assigned to trips the Guide manages,
-- or Travellers in demo sessions for trips the Guide manages.
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profiles_select_own_or_guide" ON public.profiles;
CREATE POLICY "profiles_select_own_or_guide" ON public.profiles
    FOR SELECT TO authenticated
    USING (
        id = auth.uid()
        OR (
            public.is_approved_guide(auth.uid())
            AND (
                -- Guide can only see travellers assigned to trips the guide manages
                EXISTS (
                    SELECT 1 FROM public.trip_members guide_tm
                    JOIN public.trip_members traveller_tm ON guide_tm.trip_id = traveller_tm.trip_id
                    WHERE guide_tm.user_id = auth.uid()
                      AND guide_tm.membership_role IN ('guide', 'lead_guide')
                      AND traveller_tm.user_id = profiles.id
                      AND profiles.role = 'traveller'
                )
                OR
                -- Or travellers with demo sessions for trips the guide manages
                EXISTS (
                    SELECT 1 FROM public.demo_sessions ds
                    JOIN public.trip_members guide_tm ON guide_tm.trip_id = ds.trip_id
                    WHERE guide_tm.user_id = auth.uid()
                      AND guide_tm.membership_role IN ('guide', 'lead_guide')
                      AND ds.traveller_id = profiles.id
                )
            )
        )
    );

-- 4. REINFORCE TRIP MEMBERS ROW LEVEL SECURITY
ALTER TABLE public.trip_members ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "trip_members_select_related" ON public.trip_members;
CREATE POLICY "trip_members_select_related" ON public.trip_members
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR public.is_trip_guide(auth.uid(), trip_id)
        OR public.is_approved_guide(auth.uid())
    );

-- 5. REINFORCE DEMO SESSIONS ROW LEVEL SECURITY
ALTER TABLE public.demo_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "demo_sessions_select" ON public.demo_sessions;
CREATE POLICY "demo_sessions_select" ON public.demo_sessions
    FOR SELECT TO authenticated
    USING (
        traveller_id = auth.uid()
        OR public.is_trip_guide(auth.uid(), trip_id)
        OR (
            public.is_approved_guide(auth.uid())
            AND EXISTS (
                SELECT 1 FROM public.trip_members guide_tm
                WHERE guide_tm.user_id = auth.uid()
                  AND guide_tm.trip_id = demo_sessions.trip_id
                  AND guide_tm.membership_role IN ('guide', 'lead_guide')
            )
        )
    );
