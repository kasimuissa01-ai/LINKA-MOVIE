-- Supabase Database Schema for MovieRoom App
-- Run this in your Supabase SQL Editor: https://supabase.com/dashboard/project/vqgnxqabvmmpfoiceass/sql

-- 1. Movies Table
CREATE TABLE IF NOT EXISTS public.movies (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    genres TEXT[] DEFAULT '{}',
    cover_url TEXT,
    video_key TEXT NOT NULL,
    video_stream_url TEXT,
    duration_minutes INTEGER DEFAULT 120,
    file_size_mb BIGINT DEFAULT 500,
    release_year INTEGER DEFAULT 2026,
    rating NUMERIC(3, 1) DEFAULT 8.0,
    cast_members TEXT[] DEFAULT '{}',
    is_featured BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. User Profiles Table
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    email TEXT,
    display_name TEXT,
    phone_number TEXT,
    role TEXT DEFAULT 'user', -- 'admin' or 'user'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. Upload Sessions Table (for Multipart Upload tracking)
CREATE TABLE IF NOT EXISTS public.upload_sessions (
    upload_id TEXT PRIMARY KEY,
    movie_id TEXT NOT NULL,
    movie_title TEXT NOT NULL,
    video_key TEXT NOT NULL,
    total_bytes BIGINT NOT NULL,
    chunk_size BIGINT NOT NULL,
    parts_json JSONB DEFAULT '[]'::jsonb,
    is_completed BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.movies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.upload_sessions ENABLE ROW LEVEL SECURITY;

-- 5. Policies
CREATE POLICY "Public can view movies" 
ON public.movies FOR SELECT 
USING (true);

CREATE POLICY "Admins can insert and modify movies" 
ON public.movies FOR ALL 
USING (true);

CREATE POLICY "Public profiles access" 
ON public.profiles FOR ALL 
USING (true);

CREATE POLICY "Upload sessions access" 
ON public.upload_sessions FOR ALL 
USING (true);
