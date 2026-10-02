-- ====================================================================
-- BlinkWell Supabase Database Schema & Row-Level Security (RLS) Policies
-- Designed by Mitali Purohit / BlinkWell Project
-- ====================================================================

-- Enable UUID extension if not already present
create extension if not exists "pgcrypto";

-- 1. Profiles Table
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  research_consent boolean default false not null,
  created_at timestamptz default now() not null
);

-- 2. Blink Sessions Table
create table if not exists public.blink_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete cascade not null,
  started_at timestamptz not null,
  ended_at timestamptz,
  avg_bpm numeric,
  min_bpm numeric,
  alert_count int default 0 not null,
  monitoring_mode text check (monitoring_mode in ('background', 'app_only')) not null default 'app_only',
  created_at timestamptz default now() not null
);

-- 3. Blink Minute Logs Table
create table if not exists public.blink_minute_log (
  id bigint generated always as identity primary key,
  session_id uuid references public.blink_sessions(id) on delete cascade not null,
  minute_timestamp timestamptz not null,
  bpm numeric not null,
  created_at timestamptz default now() not null
);

-- Indexes for performance
create index if not exists idx_sessions_user_id on public.blink_sessions(user_id);
create index if not exists idx_sessions_started_at on public.blink_sessions(started_at desc);
create index if not exists idx_minute_log_session_id on public.blink_minute_log(session_id);
create index if not exists idx_minute_log_timestamp on public.blink_minute_log(minute_timestamp desc);

-- ====================================================================
-- Row Level Security (RLS) Policies
-- ====================================================================

-- Enable RLS on all tables
alter table public.profiles enable row level security;
alter table public.blink_sessions enable row level security;
alter table public.blink_minute_log enable row level security;

-- Profiles Policies
create policy "Users can read own profile"
  on public.profiles for select
  using (auth.uid() = id);

create policy "Users can insert own profile"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "Users can update own profile"
  on public.profiles for update
  using (auth.uid() = id);

create policy "Researchers can read profiles with research consent"
  on public.profiles for select
  using (
    auth.jwt() ->> 'email' is not null 
    and research_consent = true
  );

-- Blink Sessions Policies
create policy "Users can read own sessions"
  on public.blink_sessions for select
  using (auth.uid() = user_id);

create policy "Users can insert own sessions"
  on public.blink_sessions for insert
  with check (auth.uid() = user_id);

create policy "Users can update own sessions"
  on public.blink_sessions for update
  using (auth.uid() = user_id);

create policy "Researchers can view consented sessions"
  on public.blink_sessions for select
  using (
    auth.jwt() ->> 'email' is not null 
    and exists (
      select 1 from public.profiles
      where profiles.id = blink_sessions.user_id
      and profiles.research_consent = true
    )
  );

-- Blink Minute Log Policies
create policy "Users can read own minute logs"
  on public.blink_minute_log for select
  using (
    exists (
      select 1 from public.blink_sessions
      where blink_sessions.id = blink_minute_log.session_id
      and blink_sessions.user_id = auth.uid()
    )
  );

create policy "Users can insert own minute logs"
  on public.blink_minute_log for insert
  with check (
    exists (
      select 1 from public.blink_sessions
      where blink_sessions.id = blink_minute_log.session_id
      and blink_sessions.user_id = auth.uid()
    )
  );

create policy "Researchers can view consented minute logs"
  on public.blink_minute_log for select
  using (
    auth.jwt() ->> 'email' is not null 
    and exists (
      select 1 from public.blink_sessions
      join public.profiles on profiles.id = blink_sessions.user_id
      where blink_sessions.id = blink_minute_log.session_id
      and profiles.research_consent = true
    )
  );
