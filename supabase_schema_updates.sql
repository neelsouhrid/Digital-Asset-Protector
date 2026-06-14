-- ==============================================================================
-- DIGITAL ASSET PROTECTOR: SUPABASE SCHEMA UPDATES
-- ==============================================================================
-- Run these SQL commands in your Supabase SQL Editor to prepare your database
-- for the Admin Panel, Tickets, and new Ownership Transfer logic.
-- ==============================================================================

-- 1. Create Support Tickets Table
CREATE TABLE IF NOT EXISTS public.support_tickets (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid, -- Optional: link to auth.users if using Supabase Auth
    user_email text NOT NULL,
    user_name text,
    description text NOT NULL,
    cloudinary_image_url text,
    status text DEFAULT 'open',
    raised_by_gemini boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT support_tickets_pkey PRIMARY KEY (id)
);

-- Enable RLS for support_tickets
ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;

-- Allow anyone (or authenticated users) to insert tickets
CREATE POLICY "Allow public insert to support_tickets" ON public.support_tickets
    FOR INSERT WITH CHECK (true);

-- Allow admins to view all tickets (Replace with your actual admin policy)
CREATE POLICY "Allow public view to support_tickets" ON public.support_tickets
    FOR SELECT USING (true);
    
-- Allow admins to update tickets (e.g. resolve)
CREATE POLICY "Allow public update to support_tickets" ON public.support_tickets
    FOR UPDATE USING (true);


-- 2. Add Admin flag to Profiles
-- Note: Assuming you already have a `profiles` table. If not, this will error.
-- If you don't have a profiles table, create one, or just manage admins differently.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_schema='public' AND table_name='profiles' AND column_name='is_admin') THEN
        ALTER TABLE public.profiles ADD COLUMN is_admin boolean DEFAULT false;
    END IF;
END
$$;

-- 3. Ensure 'assets' table has 'is_enforced' boolean
-- This is used to track if sighting devices should blur the asset.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_schema='public' AND table_name='assets' AND column_name='is_enforced') THEN
        ALTER TABLE public.assets ADD COLUMN is_enforced boolean DEFAULT false;
    END IF;
END
$$;

-- 4. Set a specific user as an admin (Replace with your actual email)
-- UPDATE public.profiles SET is_admin = true WHERE email = 'your.email@gmail.com';
