-- ====================================================================
-- BlinkWell Supabase Feedback Setup & Permissions Migration
-- Run this script in your Supabase SQL Editor:
-- https://supabase.com/dashboard/project/<your-project-id>/sql/new
-- ====================================================================

-- 1. Ensure UUID extension exists
create extension if not exists "pgcrypto";

-- 2. Create the feedback table if not already created
create table if not exists public.feedback (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete set null,
  usage_duration text not null,
  comfort_improvement text not null,
  reminder_comfort text not null,
  preferred_mode text not null,
  comments text,
  created_at timestamptz default now() not null
);

-- 3. Ensure user_id allows NULL (for users submitting anonymously without auth)
alter table public.feedback alter column user_id drop not null;

-- 4. Create performance indexes
create index if not exists idx_feedback_created_at on public.feedback(created_at desc);
create index if not exists idx_feedback_user_id on public.feedback(user_id);

-- 5. Enable Realtime so incoming feedback updates dashboard immediately
do $$
begin
  alter publication supabase_realtime add table public.feedback;
exception
  when duplicate_object then null;
  when others then null;
end $$;

-- 6. Grant schema and table permissions to anon (mobile app) and authenticated (research portal)
grant usage on schema public to anon, authenticated;
grant all on table public.feedback to anon, authenticated;

-- 7. Enable Row Level Security (RLS)
alter table public.feedback enable row level security;

-- 8. Policies:
-- Allow mobile app users (both anon and authenticated) to submit feedback
drop policy if exists "Users can insert own feedback" on public.feedback;
drop policy if exists "Users can insert feedback" on public.feedback;
create policy "Users can insert feedback"
  on public.feedback for insert
  to anon, authenticated
  with check (true);

-- Allow authenticated researchers on the web dashboard to view all feedback entries
drop policy if exists "Researchers can view feedback" on public.feedback;
create policy "Researchers can view feedback"
  on public.feedback for select
  to authenticated
  using (true);

-- Allow mobile app users to read back their own feedback if user_id matches
drop policy if exists "Users can read own feedback" on public.feedback;
create policy "Users can read own feedback"
  on public.feedback for select
  to anon
  using (auth.uid() = user_id);
