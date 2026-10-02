-- ====================================================================
-- BlinkWell Supabase Database Schema & Realtime Setup (Idempotent)
-- Designed by Mitali Purohit / BlinkWell Project
-- Safe to re-run multiple times without errors
-- ====================================================================

-- Enable UUID extension
create extension if not exists "pgcrypto";

-- 1. Profiles Table (for mobile app users)
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  research_consent boolean default false not null,
  cohort_arm text default 'general' not null,
  created_at timestamptz default now() not null
);

-- Ensure cohort_arm column exists if table was created previously
alter table public.profiles add column if not exists cohort_arm text default 'general';

-- 2. Blink Sessions Table (session summaries)
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

-- 3. Blink Minute Logs Table (minute-by-minute metrics)
create table if not exists public.blink_minute_log (
  id bigint generated always as identity primary key,
  session_id uuid references public.blink_sessions(id) on delete cascade not null,
  minute_timestamp timestamptz not null,
  bpm numeric not null,
  created_at timestamptz default now() not null
);

-- 4. Authorized Researchers & Admins Table
create table if not exists public.researchers (
  id uuid primary key references auth.users(id) on delete cascade,
  email text unique not null,
  role text not null check (role in ('admin', 'researcher')) default 'researcher',
  created_at timestamptz default now() not null
);

-- Indexes for lightning-fast queries
create index if not exists idx_sessions_user_id on public.blink_sessions(user_id);
create index if not exists idx_sessions_started_at on public.blink_sessions(started_at desc);
create index if not exists idx_minute_log_session_id on public.blink_minute_log(session_id);
create index if not exists idx_minute_log_timestamp on public.blink_minute_log(minute_timestamp desc);
create index if not exists idx_researchers_email on public.researchers(email);

-- ====================================================================
-- Enable Supabase Realtime (Safe duplicate check)
-- ====================================================================
do $$
begin
  alter publication supabase_realtime add table public.blink_sessions;
exception
  when duplicate_object then null;
  when others then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.blink_minute_log;
exception
  when duplicate_object then null;
  when others then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.profiles;
exception
  when duplicate_object then null;
  when others then null;
end $$;

-- ====================================================================
-- Row Level Security (RLS) Policies
-- ====================================================================

alter table public.profiles enable row level security;
alter table public.blink_sessions enable row level security;
alter table public.blink_minute_log enable row level security;
alter table public.researchers enable row level security;

-- Helper functions to check roles
create or replace function public.is_researcher()
returns boolean as $$
begin
  return exists (
    select 1 from public.researchers
    where id = auth.uid()
  );
end;
$$ language plpgsql security definer;

create or replace function public.is_admin()
returns boolean as $$
begin
  return exists (
    select 1 from public.researchers
    where id = auth.uid() and role = 'admin'
  );
end;
$$ language plpgsql security definer;

-- Profiles Policies (Drop if exists before creating)
drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile"
  on public.profiles for select
  using (auth.uid() = id);

drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile"
  on public.profiles for insert
  with check (auth.uid() = id);

drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile"
  on public.profiles for update
  using (auth.uid() = id);

drop policy if exists "Researchers can read consented profiles" on public.profiles;
create policy "Researchers can read consented profiles"
  on public.profiles for select
  using (public.is_researcher() and research_consent = true);

-- Blink Sessions Policies (Drop if exists before creating)
drop policy if exists "Users can read own sessions" on public.blink_sessions;
create policy "Users can read own sessions"
  on public.blink_sessions for select
  using (auth.uid() = user_id);

drop policy if exists "Users can insert own sessions" on public.blink_sessions;
create policy "Users can insert own sessions"
  on public.blink_sessions for insert
  with check (auth.uid() = user_id);

drop policy if exists "Users can update own sessions" on public.blink_sessions;
create policy "Users can update own sessions"
  on public.blink_sessions for update
  using (auth.uid() = user_id);

drop policy if exists "Researchers can view consented sessions" on public.blink_sessions;
create policy "Researchers can view consented sessions"
  on public.blink_sessions for select
  using (
    public.is_researcher()
    and exists (
      select 1 from public.profiles
      where profiles.id = blink_sessions.user_id
      and profiles.research_consent = true
    )
  );

-- Blink Minute Log Policies (Drop if exists before creating)
drop policy if exists "Users can read own minute logs" on public.blink_minute_log;
create policy "Users can read own minute logs"
  on public.blink_minute_log for select
  using (
    exists (
      select 1 from public.blink_sessions
      where blink_sessions.id = blink_minute_log.session_id
      and blink_sessions.user_id = auth.uid()
    )
  );

drop policy if exists "Users can insert own minute logs" on public.blink_minute_log;
create policy "Users can insert own minute logs"
  on public.blink_minute_log for insert
  with check (
    exists (
      select 1 from public.blink_sessions
      where blink_sessions.id = blink_minute_log.session_id
      and blink_sessions.user_id = auth.uid()
    )
  );

drop policy if exists "Researchers can view consented minute logs" on public.blink_minute_log;
create policy "Researchers can view consented minute logs"
  on public.blink_minute_log for select
  using (
    public.is_researcher()
    and exists (
      select 1 from public.blink_sessions
      join public.profiles on profiles.id = blink_sessions.user_id
      where blink_sessions.id = blink_minute_log.session_id
      and profiles.research_consent = true
    )
  );

-- Researchers Table Policies (Drop if exists before creating)
drop policy if exists "Researchers can read researchers list" on public.researchers;
create policy "Researchers can read researchers list"
  on public.researchers for select
  using (public.is_researcher());

drop policy if exists "Admins can insert new researchers" on public.researchers;
create policy "Admins can insert new researchers"
  on public.researchers for insert
  with check (
    public.is_admin() 
    or not exists (select 1 from public.researchers)
    or (auth.jwt() ->> 'email' = 'viraravil2101@gmail.com')
  );

drop policy if exists "Admins can update researchers" on public.researchers;
create policy "Admins can update researchers"
  on public.researchers for update
  using (
    public.is_admin()
    or (auth.jwt() ->> 'email' = 'viraravil2101@gmail.com')
  );

drop policy if exists "Admins can delete researchers" on public.researchers;
create policy "Admins can delete researchers"
  on public.researchers for delete
  using (public.is_admin());

-- ====================================================================
-- Initial Super-Admin Provisioning
-- Primary Super-Admin: viraravil2101@gmail.com
-- ====================================================================
create or replace function public.handle_new_researcher()
returns trigger as $$
begin
  if new.email = 'viraravil2101@gmail.com' then
    insert into public.researchers (id, email, role)
    values (new.id, new.email, 'admin')
    on conflict (id) do update set role = 'admin';
  end if;
  return new;
end;
$$ language plpgsql security definer;

-- Trigger on auth.users
drop trigger if exists on_auth_user_created_researcher on auth.users;
create trigger on_auth_user_created_researcher
  after insert on auth.users
  for each row execute function public.handle_new_researcher();
