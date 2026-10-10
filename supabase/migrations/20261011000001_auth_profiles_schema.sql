-- ============================================================================
-- ROAMLY: SUPABASE POSTGRESQL SCHEMA & ROW LEVEL SECURITY
-- Layover 2026 Hackathon (Travel and Tourism Track)
-- Migration: 20261011000001_auth_profiles_schema.sql
-- ============================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. PROFILES TABLE
-- Linked directly to Supabase Auth (auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('traveller', 'guide')),
    approval_status TEXT NOT NULL DEFAULT 'approved' CHECK (approval_status IN ('pending', 'approved', 'rejected')),
    email TEXT,
    phone TEXT,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. TRIPS TABLE (Canonical Singapore Trip)
CREATE TABLE IF NOT EXISTS public.trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    destination TEXT NOT NULL,
    country TEXT NOT NULL,
    duration_days INT NOT NULL DEFAULT 5,
    status TEXT NOT NULL DEFAULT 'Upcoming' CHECK (status IN ('Planning', 'Upcoming', 'In Progress', 'Completed')),
    summary TEXT,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. TRIP MEMBERS (Roster & Guides)
CREATE TABLE IF NOT EXISTS public.trip_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    membership_role TEXT NOT NULL DEFAULT 'traveller' CHECK (membership_role IN ('traveller', 'guide', 'lead_guide')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_trip_user UNIQUE(trip_id, user_id)
);

-- 5. CHECK-INS TABLE
CREATE TABLE IF NOT EXISTS public.check_ins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(id) ON DELETE CASCADE,
    traveller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending', 'Checked In', 'Absent')),
    checked_in_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_trip_traveller_checkin UNIQUE(trip_id, traveller_id)
);

-- 6. ANNOUNCEMENTS TABLE
CREATE TABLE IF NOT EXISTS public.announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.trips(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_approval ON public.profiles(approval_status);
CREATE INDEX IF NOT EXISTS idx_trips_slug ON public.trips(slug);
CREATE INDEX IF NOT EXISTS idx_trip_members_trip ON public.trip_members(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_members_user ON public.trip_members(user_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_trip ON public.check_ins(trip_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_traveller ON public.check_ins(traveller_id);
CREATE INDEX IF NOT EXISTS idx_announcements_trip ON public.announcements(trip_id);

-- 8. HELPER SECURITY FUNCTIONS
-- Checks if user is an approved guide
CREATE OR REPLACE FUNCTION public.is_approved_guide(user_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = user_uuid
          AND role = 'guide'
          AND approval_status = 'approved'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Checks if user is guide for specific trip
CREATE OR REPLACE FUNCTION public.is_trip_guide(user_uuid UUID, target_trip UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.trip_members tm
        JOIN public.profiles p ON tm.user_id = p.id
        WHERE tm.trip_id = target_trip
          AND tm.user_id = user_uuid
          AND tm.membership_role IN ('guide', 'lead_guide')
          AND p.approval_status = 'approved'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Checks if user is member of trip
CREATE OR REPLACE FUNCTION public.is_trip_member(user_uuid UUID, target_trip UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.trip_members
        WHERE trip_id = target_trip
          AND user_id = user_uuid
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- 9. PREVENT PRIVILEGE ESCALATION TRIGGER
-- Prevents standard users from modifying their own 'role' or 'approval_status'
CREATE OR REPLACE FUNCTION public.prevent_role_escalation()
RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.role IS DISTINCT FROM OLD.role OR NEW.approval_status IS DISTINCT FROM OLD.approval_status) THEN
        -- Allow change ONLY if executed by superuser or service_role
        IF current_user <> 'service_role' AND (COALESCE(auth.jwt() ->> 'role', '')) <> 'service_role' THEN
            RAISE EXCEPTION 'Security Violation: Standard users cannot alter their own role or approval status.';
        END IF;
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_prevent_role_escalation ON public.profiles;
CREATE TRIGGER trg_prevent_role_escalation
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_role_escalation();

-- 10. NEW USER AUTH TRIGGER
-- Safely creates profile when user signs up via email/password or Google OAuth
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    requested_role TEXT;
    initial_approval TEXT;
    parsed_full_name TEXT;
BEGIN
    -- Extract requested role from metadata, default to 'traveller'
    requested_role := COALESCE(NEW.raw_user_meta_data->>'role', 'traveller');

    -- Guides must NEVER be self-approved. They start as 'pending'
    IF requested_role = 'guide' THEN
        initial_approval := 'pending';
    ELSE
        requested_role := 'traveller';
        initial_approval := 'approved';
    END IF;

    -- Extract full name
    parsed_full_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        split_part(NEW.email, '@', 1)
    );

    INSERT INTO public.profiles (id, full_name, role, approval_status, email)
    VALUES (
        NEW.id,
        parsed_full_name,
        requested_role,
        initial_approval,
        NEW.email
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        updated_at = NOW();

    -- Assign traveller to canonical Singapore trip automatically
    IF requested_role = 'traveller' THEN
        INSERT INTO public.trip_members (trip_id, user_id, membership_role)
        SELECT id, NEW.id, 'traveller'
        FROM public.trips
        WHERE slug = 'trip_singapore_2026'
        ON CONFLICT (trip_id, user_id) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- 11. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trip_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.check_ins ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;

-- PROFILES POLICIES
DROP POLICY IF EXISTS "profiles_select_own_or_guide" ON public.profiles;
CREATE POLICY "profiles_select_own_or_guide" ON public.profiles
    FOR SELECT TO authenticated
    USING (
        id = auth.uid()
        OR public.is_approved_guide(auth.uid())
    );

DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own" ON public.profiles
    FOR UPDATE TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

-- TRIPS POLICIES
DROP POLICY IF EXISTS "trips_select_all_authenticated" ON public.trips;
CREATE POLICY "trips_select_all_authenticated" ON public.trips
    FOR SELECT TO authenticated
    USING (true); -- Trips catalog is visible to authenticated users

DROP POLICY IF EXISTS "trips_insert_approved_guide" ON public.trips;
CREATE POLICY "trips_insert_approved_guide" ON public.trips
    FOR INSERT TO authenticated
    WITH CHECK (public.is_approved_guide(auth.uid()));

DROP POLICY IF EXISTS "trips_update_trip_guide" ON public.trips;
CREATE POLICY "trips_update_trip_guide" ON public.trips
    FOR UPDATE TO authenticated
    USING (public.is_trip_guide(auth.uid(), id));

-- TRIP MEMBERS POLICIES
DROP POLICY IF EXISTS "trip_members_select_related" ON public.trip_members;
CREATE POLICY "trip_members_select_related" ON public.trip_members
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR public.is_trip_guide(auth.uid(), trip_id)
        OR public.is_approved_guide(auth.uid())
    );

DROP POLICY IF EXISTS "trip_members_manage_guide" ON public.trip_members;
CREATE POLICY "trip_members_manage_guide" ON public.trip_members
    FOR ALL TO authenticated
    USING (public.is_trip_guide(auth.uid(), trip_id));

-- CHECK-INS POLICIES
DROP POLICY IF EXISTS "checkins_select_traveller_or_guide" ON public.check_ins;
CREATE POLICY "checkins_select_traveller_or_guide" ON public.check_ins
    FOR SELECT TO authenticated
    USING (
        traveller_id = auth.uid()
        OR public.is_trip_guide(auth.uid(), trip_id)
    );

DROP POLICY IF EXISTS "checkins_update_traveller_or_guide" ON public.check_ins;
CREATE POLICY "checkins_update_traveller_or_guide" ON public.check_ins
    FOR UPDATE TO authenticated
    USING (
        traveller_id = auth.uid()
        OR public.is_trip_guide(auth.uid(), trip_id)
    );

-- ANNOUNCEMENTS POLICIES
DROP POLICY IF EXISTS "announcements_select_members" ON public.announcements;
CREATE POLICY "announcements_select_members" ON public.announcements
    FOR SELECT TO authenticated
    USING (
        public.is_trip_member(auth.uid(), trip_id)
        OR public.is_approved_guide(auth.uid())
    );

DROP POLICY IF EXISTS "announcements_insert_guide" ON public.announcements;
CREATE POLICY "announcements_insert_guide" ON public.announcements
    FOR INSERT TO authenticated
    WITH CHECK (public.is_trip_guide(auth.uid(), trip_id));

-- 12. SEED CANONICAL SINGAPORE TRIP (NO DUPLICATES)
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
    '00000000-0000-0000-0000-000000000001',
    'trip_singapore_2026',
    'Singapore Explorer',
    'Singapore',
    'Singapore',
    5,
    'Upcoming',
    'Experience the futuristic elegance of Gardens by the Bay, cultural heritage of Chinatown, thrill at Sentosa, and lush flora at Botanic Gardens.'
)
ON CONFLICT (slug) DO UPDATE
SET title = EXCLUDED.title,
    destination = EXCLUDED.destination,
    country = EXCLUDED.country,
    duration_days = EXCLUDED.duration_days,
    summary = EXCLUDED.summary,
    updated_at = NOW();

-- 13. ADMINISTRATIVE GUIDE APPROVAL PROCEDURE (For Docs / SQL Runner)
-- Example for administrator approving a registered guide:
-- UPDATE public.profiles SET approval_status = 'approved' WHERE id = 'GUIDE_USER_UUID';
