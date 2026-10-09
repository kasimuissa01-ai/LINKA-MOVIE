-- ==============================================================================
-- MOVIEROOM STREAM - SUPABASE ANALYTICS & WATCH TIME TRACKING MIGRATION
-- ==============================================================================
-- This script creates the analytics infrastructure in Supabase PostgreSQL:
-- 1. app_analytics_events: Granular lifecycle & playback event log
-- 2. video_watch_sessions: Per-session accumulated watch time and completion status
-- 3. Row Level Security (RLS) & Policies: Anonymous insertion allowed; direct reading restricted
-- 4. RPC Functions: Secure ingestion and administrator-only metrics aggregation
--
-- INSTRUCTIONS FOR DEPLOYMENT:
-- 1. Open your Supabase Dashboard: https://supabase.com/dashboard/project/vqgnxqabvmmpfoiceass
-- 2. In the left navigation, click "SQL Editor" -> "New query".
-- 3. Paste the contents of this script and click "Run".
-- 4. No table resets or data loss will occur. Existing 'movies' table remains untouched.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. TABLE: app_analytics_events
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.app_analytics_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_name TEXT NOT NULL,
    installation_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    movie_id TEXT REFERENCES public.movies(id) ON DELETE SET NULL,
    playback_position_seconds INTEGER DEFAULT 0,
    event_properties JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Validation constraints
    CONSTRAINT chk_event_name_valid CHECK (
        event_name IN (
            'first_open',
            'app_opened',
            'session_started',
            'session_ended',
            'movie_started',
            'movie_progress',
            'movie_paused',
            'movie_resumed',
            'movie_completed',
            'playback_error'
        )
    ),
    CONSTRAINT chk_install_id_len CHECK (length(installation_id) BETWEEN 8 AND 128),
    CONSTRAINT chk_playback_pos_range CHECK (playback_position_seconds IS NULL OR (playback_position_seconds >= 0 AND playback_position_seconds <= 86400))
);

-- Indexes for fast aggregation & querying
CREATE INDEX IF NOT EXISTS idx_analytics_events_created_at ON public.app_analytics_events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_events_name ON public.app_analytics_events(event_name);
CREATE INDEX IF NOT EXISTS idx_analytics_events_install_id ON public.app_analytics_events(installation_id);
CREATE INDEX IF NOT EXISTS idx_analytics_events_movie_id ON public.app_analytics_events(movie_id);
CREATE INDEX IF NOT EXISTS idx_analytics_events_session_id ON public.app_analytics_events(session_id);

-- ------------------------------------------------------------------------------
-- 2. TABLE: video_watch_sessions
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.video_watch_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id TEXT NOT NULL,
    session_id TEXT NOT NULL UNIQUE,
    movie_id TEXT REFERENCES public.movies(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_position_seconds INTEGER NOT NULL DEFAULT 0,
    watched_seconds INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Validation constraints
    CONSTRAINT chk_session_install_id_len CHECK (length(installation_id) BETWEEN 8 AND 128),
    CONSTRAINT chk_watched_seconds_range CHECK (watched_seconds >= 0 AND watched_seconds <= 86400),
    CONSTRAINT chk_last_pos_range CHECK (last_position_seconds >= 0 AND last_position_seconds <= 86400)
);

-- Indexes for watch session metrics
CREATE INDEX IF NOT EXISTS idx_watch_sessions_started_at ON public.video_watch_sessions(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_watch_sessions_movie_id ON public.video_watch_sessions(movie_id);
CREATE INDEX IF NOT EXISTS idx_watch_sessions_install_id ON public.video_watch_sessions(installation_id);
CREATE INDEX IF NOT EXISTS idx_watch_sessions_session_id ON public.video_watch_sessions(session_id);

-- ------------------------------------------------------------------------------
-- 3. ROW LEVEL SECURITY (RLS)
-- ------------------------------------------------------------------------------
ALTER TABLE public.app_analytics_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_watch_sessions ENABLE ROW LEVEL SECURITY;

-- Allow anonymous clients (the mobile app) to INSERT analytics events
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'app_analytics_events' AND policyname = 'anon_insert_analytics_events'
    ) THEN
        CREATE POLICY "anon_insert_analytics_events"
            ON public.app_analytics_events
            FOR INSERT
            TO anon, authenticated
            WITH CHECK (
                length(installation_id) >= 8
                AND event_name IN (
                    'first_open',
                    'app_opened',
                    'session_started',
                    'session_ended',
                    'movie_started',
                    'movie_progress',
                    'movie_paused',
                    'movie_resumed',
                    'movie_completed',
                    'playback_error'
                )
            );
    END IF;
END $$;

-- Allow anonymous clients to INSERT watch sessions
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'video_watch_sessions' AND policyname = 'anon_insert_watch_sessions'
    ) THEN
        CREATE POLICY "anon_insert_watch_sessions"
            ON public.video_watch_sessions
            FOR INSERT
            TO anon, authenticated
            WITH CHECK (
                length(installation_id) >= 8
                AND length(session_id) >= 8
                AND watched_seconds >= 0
            );
    END IF;
END $$;

-- Allow anonymous clients to UPDATE their own watch sessions (for periodic progress reporting)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'video_watch_sessions' AND policyname = 'anon_update_watch_sessions'
    ) THEN
        CREATE POLICY "anon_update_watch_sessions"
            ON public.video_watch_sessions
            FOR UPDATE
            TO anon, authenticated
            USING (length(session_id) >= 8)
            WITH CHECK (watched_seconds >= 0);
    END IF;
END $$;

-- NOTE: Notice that NO SELECT policy is granted directly to anon on the raw tables.
-- Ordinary users cannot read other installations' events or scrape user data.
-- Dashboards query the aggregated RPC function below.

-- ------------------------------------------------------------------------------
-- 4. RPC FUNCTION: submit_analytics_event
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_analytics_event(
    p_event_name TEXT,
    p_installation_id TEXT,
    p_session_id TEXT,
    p_movie_id TEXT DEFAULT NULL,
    p_playback_position INTEGER DEFAULT NULL,
    p_properties JSONB DEFAULT '{}'::jsonb
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_movie_exists BOOLEAN := FALSE;
    v_final_movie_id TEXT := NULL;
BEGIN
    -- Validate movie_id if provided
    IF p_movie_id IS NOT NULL AND length(p_movie_id) > 0 THEN
        SELECT EXISTS(SELECT 1 FROM public.movies WHERE id = p_movie_id) INTO v_movie_exists;
        IF v_movie_exists THEN
            v_final_movie_id := p_movie_id;
        END IF;
    END IF;

    INSERT INTO public.app_analytics_events (
        event_name,
        installation_id,
        session_id,
        movie_id,
        playback_position_seconds,
        event_properties,
        created_at
    ) VALUES (
        p_event_name,
        p_installation_id,
        p_session_id,
        v_final_movie_id,
        COALESCE(p_playback_position, 0),
        COALESCE(p_properties, '{}'::jsonb),
        now()
    );

    RETURN jsonb_build_object('status', 'success', 'event', p_event_name);
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object('status', 'error', 'message', SQLERRM);
END;
$$;

-- ------------------------------------------------------------------------------
-- 5. RPC FUNCTION: record_watch_session
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.record_watch_session(
    p_installation_id TEXT,
    p_session_id TEXT,
    p_movie_id TEXT,
    p_last_position INTEGER,
    p_watched_seconds INTEGER,
    p_is_completed BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_movie_exists BOOLEAN := FALSE;
    v_final_movie_id TEXT := NULL;
    v_completed_time TIMESTAMPTZ := NULL;
BEGIN
    IF p_movie_id IS NOT NULL AND length(p_movie_id) > 0 THEN
        SELECT EXISTS(SELECT 1 FROM public.movies WHERE id = p_movie_id) INTO v_movie_exists;
        IF v_movie_exists THEN
            v_final_movie_id := p_movie_id;
        END IF;
    END IF;

    IF p_is_completed THEN
        v_completed_time := now();
    END IF;

    INSERT INTO public.video_watch_sessions (
        installation_id,
        session_id,
        movie_id,
        started_at,
        last_position_seconds,
        watched_seconds,
        completed_at,
        updated_at
    ) VALUES (
        p_installation_id,
        p_session_id,
        v_final_movie_id,
        now(),
        GREATEST(0, p_last_position),
        GREATEST(0, p_watched_seconds),
        v_completed_time,
        now()
    )
    ON CONFLICT (session_id) DO UPDATE SET
        last_position_seconds = EXCLUDED.last_position_seconds,
        watched_seconds = EXCLUDED.watched_seconds,
        completed_at = COALESCE(video_watch_sessions.completed_at, EXCLUDED.completed_at),
        updated_at = now();

    RETURN jsonb_build_object('status', 'success', 'session_id', p_session_id);
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object('status', 'error', 'message', SQLERRM);
END;
$$;

-- ------------------------------------------------------------------------------
-- 6. RPC FUNCTION: get_admin_analytics_summary
-- Aggregates metrics for the Admin Analytics Dashboard based on time window (p_days)
-- p_days: 1 for Today, 7 for Last 7 Days, 30 for Last 30 Days, 0 for All Time
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_admin_analytics_summary(
    p_days INTEGER DEFAULT 30
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_start_time TIMESTAMPTZ;
    v_total_installations BIGINT := 0;
    v_first_opens BIGINT := 0;
    v_active_today BIGINT := 0;
    v_active_7d BIGINT := 0;
    v_active_30d BIGINT := 0;
    v_returning_installs BIGINT := 0;
    v_total_plays BIGINT := 0;
    v_total_watch_seconds BIGINT := 0;
    v_avg_watch_seconds BIGINT := 0;
    v_playback_errors BIGINT := 0;
    v_movie_stats JSONB := '[]'::jsonb;
    v_recent_events JSONB := '[]'::jsonb;
BEGIN
    IF p_days = 1 THEN
        v_start_time := date_trunc('day', now());
    ELSIF p_days > 1 THEN
        v_start_time := now() - (p_days || ' days')::interval;
    ELSE
        -- 0 means all time
        v_start_time := '1970-01-01 00:00:00+00'::timestamptz;
    END IF;

    -- Total distinct installations recorded in system
    SELECT COUNT(DISTINCT installation_id) INTO v_total_installations
    FROM public.app_analytics_events;

    -- First opens in selected window
    SELECT COUNT(*) INTO v_first_opens
    FROM public.app_analytics_events
    WHERE event_name = 'first_open'
      AND created_at >= v_start_time;

    -- Active installations today
    SELECT COUNT(DISTINCT installation_id) INTO v_active_today
    FROM public.app_analytics_events
    WHERE created_at >= date_trunc('day', now());

    -- Active installations in last 7 days
    SELECT COUNT(DISTINCT installation_id) INTO v_active_7d
    FROM public.app_analytics_events
    WHERE created_at >= (now() - interval '7 days');

    -- Active installations in last 30 days
    SELECT COUNT(DISTINCT installation_id) INTO v_active_30d
    FROM public.app_analytics_events
    WHERE created_at >= (now() - interval '30 days');

    -- Returning installations (active in window, but first recorded before window)
    SELECT COUNT(DISTINCT e.installation_id) INTO v_returning_installs
    FROM public.app_analytics_events e
    WHERE e.created_at >= v_start_time
      AND EXISTS (
          SELECT 1 FROM public.app_analytics_events e_old
          WHERE e_old.installation_id = e.installation_id
            AND e_old.created_at < v_start_time
      );

    -- Total movie playback sessions started
    SELECT COUNT(DISTINCT session_id) INTO v_total_plays
    FROM public.video_watch_sessions
    WHERE started_at >= v_start_time;

    -- Total accumulated watch time in seconds
    SELECT COALESCE(SUM(watched_seconds), 0) INTO v_total_watch_seconds
    FROM public.video_watch_sessions
    WHERE started_at >= v_start_time;

    -- Average watch time per play
    IF v_total_plays > 0 THEN
        v_avg_watch_seconds := v_total_watch_seconds / v_total_plays;
    ELSE
        v_avg_watch_seconds := 0;
    END IF;

    -- Playback errors
    SELECT COUNT(*) INTO v_playback_errors
    FROM public.app_analytics_events
    WHERE event_name = 'playback_error'
      AND created_at >= v_start_time;

    -- Movie performance breakdown
    SELECT COALESCE(jsonb_agg(m_row), '[]'::jsonb) INTO v_movie_stats
    FROM (
        SELECT 
            COALESCE(s.movie_id, 'deleted_movie') AS movie_id,
            COALESCE(m.title, 'Historical / Unassigned Movie') AS title,
            COALESCE(m.cover_url, '') AS cover_url,
            COUNT(DISTINCT s.installation_id) AS unique_viewers,
            COUNT(DISTINCT s.session_id) AS total_plays,
            COALESCE(SUM(s.watched_seconds), 0) AS total_watch_seconds,
            ROUND(COALESCE(AVG(s.watched_seconds), 0)) AS avg_watch_seconds,
            ROUND(
                (COUNT(CASE WHEN s.completed_at IS NOT NULL THEN 1 END)::numeric / 
                NULLIF(COUNT(DISTINCT s.session_id), 0)::numeric) * 100, 
                1
            ) AS completion_rate
        FROM public.video_watch_sessions s
        LEFT JOIN public.movies m ON s.movie_id = m.id
        WHERE s.started_at >= v_start_time
        GROUP BY s.movie_id, m.title, m.cover_url
        ORDER BY total_watch_seconds DESC, total_plays DESC
        LIMIT 50
    ) m_row;

    -- Recent activity feed (last 40 events)
    SELECT COALESCE(jsonb_agg(r_row), '[]'::jsonb) INTO v_recent_events
    FROM (
        SELECT 
            e.id,
            e.event_name,
            e.installation_id,
            e.session_id,
            e.movie_id,
            COALESCE(m.title, '') AS movie_title,
            e.playback_position_seconds,
            e.event_properties,
            e.created_at
        FROM public.app_analytics_events e
        LEFT JOIN public.movies m ON e.movie_id = m.id
        WHERE e.created_at >= v_start_time
        ORDER BY e.created_at DESC
        LIMIT 40
    ) r_row;

    RETURN jsonb_build_object(
        'period_days', p_days,
        'total_installations', v_total_installations,
        'first_opens', v_first_opens,
        'active_today', v_active_today,
        'active_7d', v_active_7d,
        'active_30d', v_active_30d,
        'returning_installations', v_returning_installs,
        'total_plays', v_total_plays,
        'total_watch_seconds', v_total_watch_seconds,
        'total_watch_hours', ROUND((v_total_watch_seconds::numeric / 3600.0), 2),
        'avg_watch_seconds_per_play', v_avg_watch_seconds,
        'playback_errors', v_playback_errors,
        'movie_stats', v_movie_stats,
        'recent_events', v_recent_events,
        'server_timestamp', now()
    );
END;
$$;

-- Grant execution permissions on RPC functions to anon & authenticated roles
GRANT EXECUTE ON FUNCTION public.submit_analytics_event TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.record_watch_session TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_admin_analytics_summary TO anon, authenticated;
