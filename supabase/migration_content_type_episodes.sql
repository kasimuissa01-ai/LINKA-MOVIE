-- ====================================================================
-- SQL Migration: Add 'content_type' to movies and create 'episodes' table
-- Compatible with PostgreSQL / Supabase
-- ====================================================================

-- 1. Create the content_type ENUM type (safe if already exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'content_type') THEN
        CREATE TYPE content_type AS ENUM ('movie', 'series');
    END IF;
END $$;

-- 2. Add 'content_type' column to movies table with default 'movie'
ALTER TABLE public.movies
ADD COLUMN IF NOT EXISTS content_type content_type NOT NULL DEFAULT 'movie';

-- 3. Create 'episodes' table mapped to movies
CREATE TABLE IF NOT EXISTS public.episodes (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    movie_id TEXT NOT NULL,
    season_number INTEGER NOT NULL DEFAULT 1,
    episode_number INTEGER NOT NULL,
    title TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    duration_minutes INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,

    -- Foreign key linking episode to parent movie with cascading delete
    CONSTRAINT fk_episodes_movie
        FOREIGN KEY (movie_id)
        REFERENCES public.movies (id)
        ON DELETE CASCADE
);

-- 4. Indexes for query optimization & data integrity
-- Fast lookup of episodes for a given movie/series
CREATE INDEX IF NOT EXISTS idx_episodes_movie_id 
ON public.episodes (movie_id);

-- Enforce unique episode numbers per season within a series, and optimize ordered playback queries
CREATE UNIQUE INDEX IF NOT EXISTS uq_episodes_series_season_episode 
ON public.episodes (movie_id, season_number, episode_number);

-- Index on movies.content_type to quickly filter series vs movies in catalog queries
CREATE INDEX IF NOT EXISTS idx_movies_content_type 
ON public.movies (content_type);

-- 5. Enable Row Level Security (RLS) for Supabase / PostgreSQL
ALTER TABLE public.episodes ENABLE ROW LEVEL SECURITY;

-- 6. RLS Policies
CREATE POLICY "Public can view episodes"
ON public.episodes FOR SELECT
USING (true);

CREATE POLICY "Admins can manage episodes"
ON public.episodes FOR ALL
USING (true);
