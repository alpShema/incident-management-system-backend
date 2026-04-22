-- ============================================================
-- V1__initial_schema.sql
-- Description : Initial database schema for the Hilfe application.
-- Author      : Amalitech Team
-- Date        : 2026-04-22
-- ============================================================

-- Example: Create a basic users table to validate the setup.
-- Replace or extend this with your actual domain entities.

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    full_name  VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index on email for fast lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
