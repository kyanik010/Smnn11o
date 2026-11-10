-- Connected IdealPlayer short-lived pairing table.
--
-- Security model:
-- - Browser clients never access this table directly.
-- - The Edge Function uses SUPABASE_SERVICE_ROLE_KEY and implements the public API contract.
-- - RLS is enabled and no anon/authenticated table policies are granted here.

create table if not exists public.tv_pairings (
    pairing_code text primary key
        check (pairing_code ~ '^[A-HJ-NP-Z2-9]{8}$'),
    status text not null default 'pending'
        check (status in ('pending', 'completed', 'expired', 'error')),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    completed_at timestamptz
);

alter table public.tv_pairings
    add column if not exists completed_at timestamptz;

alter table public.tv_pairings
    alter column payload set default '{}'::jsonb,
    alter column status set default 'pending',
    alter column created_at set default now();

update public.tv_pairings
set payload = '{}'::jsonb
where payload is null;

delete from public.tv_pairings
where created_at < (now() - interval '10 minutes')
    or pairing_code !~ '^[A-HJ-NP-Z2-9]{8}$'
    or status not in ('pending', 'completed', 'expired', 'error');

alter table public.tv_pairings
    alter column payload set not null,
    alter column status set not null,
    alter column created_at set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'tv_pairings_pairing_code_format'
    ) then
        alter table public.tv_pairings
            add constraint tv_pairings_pairing_code_format
            check (pairing_code ~ '^[A-HJ-NP-Z2-9]{8}$');
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'tv_pairings_status_values'
    ) then
        alter table public.tv_pairings
            add constraint tv_pairings_status_values
            check (status in ('pending', 'completed', 'expired', 'error'));
    end if;
end $$;

create index if not exists tv_pairings_created_at_idx
    on public.tv_pairings (created_at);

create index if not exists tv_pairings_status_created_at_idx
    on public.tv_pairings (status, created_at);

alter table public.tv_pairings enable row level security;

drop policy if exists "Allow anonymous insert" on public.tv_pairings;
drop policy if exists "Allow anonymous inserts" on public.tv_pairings;
drop policy if exists "Allow anonymous select" on public.tv_pairings;
drop policy if exists "Allow anonymous selects" on public.tv_pairings;
drop policy if exists "Allow anonymous update" on public.tv_pairings;
drop policy if exists "Allow anonymous updates" on public.tv_pairings;
drop policy if exists "Allow anonymous delete" on public.tv_pairings;
drop policy if exists "Allow public pairing reads" on public.tv_pairings;
drop policy if exists "Allow public pairing writes" on public.tv_pairings;

revoke all on table public.tv_pairings from anon;
revoke all on table public.tv_pairings from authenticated;

comment on table public.tv_pairings is
    'Short-lived Connected IdealPlayer pairing rows. Access only through the idealplayer-remote-setup Edge Function.';
comment on column public.tv_pairings.payload is
    'Transient playlist setup payload. May contain user playlist URLs or credentials; rows expire after 10 minutes and are deleted after TV retrieval.';
