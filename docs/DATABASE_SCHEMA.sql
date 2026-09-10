-- ====================================================================
-- MovieRoom Database Schema
-- Compatible with PostgreSQL / CockroachDB / Supabase
-- ====================================================================

-- 1. Roles Enum
CREATE TYPE user_role AS ENUM ('user', 'admin');

-- 2. Users Table
CREATE TABLE users (
    uid VARCHAR(128) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    photo_url TEXT,
    role user_role NOT NULL DEFAULT 'user',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Movies Catalog Table
CREATE TABLE movies (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    genres TEXT[] NOT NULL DEFAULT '{}',
    cover_url TEXT NOT NULL,
    video_key TEXT NOT NULL UNIQUE, -- Path inside Cloudflare R2 bucket
    duration_minutes INTEGER NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    release_year INTEGER NOT NULL DEFAULT 2026,
    rating NUMERIC(3, 1) DEFAULT 4.5,
    cast_members TEXT[] DEFAULT '{}',
    is_featured BOOLEAN DEFAULT FALSE,
    uploaded_by VARCHAR(128) REFERENCES users(uid) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_movies_genres ON movies USING GIN (genres);
CREATE INDEX idx_movies_rating ON movies (rating DESC);
CREATE INDEX idx_movies_release_year ON movies (release_year DESC);
CREATE INDEX idx_movies_is_featured ON movies (is_featured);

-- 4. Downloads Audit / Sync (Optional Cloud Sync)
CREATE TABLE user_downloads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    movie_id VARCHAR(64) NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    device_id VARCHAR(128) NOT NULL,
    downloaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE -- For DRM / licensing expiry
);

-- 5. Multipart Upload Sessions
CREATE TABLE upload_sessions (
    upload_id VARCHAR(128) PRIMARY KEY,
    movie_id VARCHAR(64) NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    video_key TEXT NOT NULL,
    total_parts INTEGER NOT NULL,
    completed_parts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS', -- 'IN_PROGRESS', 'COMPLETED', 'ABORTED'
    initiated_by VARCHAR(128) NOT NULL REFERENCES users(uid),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
