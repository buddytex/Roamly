-- ============================================================================
-- ROAMLY: SUPABASE POSTGRESQL SCHEMA MIGRATION
-- Migration: 20261011000002_remove_guide_approval.sql
-- Description: Remove guide approval requirement so Guides access the Guide portal without admin approval.
-- ============================================================================

-- 1. Update handle_new_user() trigger function so guides are approved on creation
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

-- 2. Update is_approved_guide helper function so existing and new guides have access (unless explicitly rejected)
CREATE OR REPLACE FUNCTION public.is_approved_guide(user_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = user_uuid
          AND role = 'guide'
          AND approval_status <> 'rejected'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- 3. Update is_trip_guide helper function similarly
CREATE OR REPLACE FUNCTION public.is_trip_guide(user_uuid UUID, target_trip UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.trip_members tm
        JOIN public.profiles p ON tm.user_id = p.id
        WHERE tm.trip_id = target_trip
          AND tm.user_id = user_uuid
          AND tm.membership_role IN ('guide', 'lead_guide')
          AND p.approval_status <> 'rejected'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- 4. Automatically approve existing guides who were previously in 'pending' status
UPDATE public.profiles
SET approval_status = 'approved',
    updated_at = NOW()
WHERE role = 'guide'
  AND approval_status = 'pending';
