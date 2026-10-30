-- Milestone 1: Baseline schema initialization
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Initial schema metadata to verify database connectivity and Flyway execution
CREATE TABLE IF NOT EXISTS platform_schema_version (
    id SERIAL PRIMARY KEY,
    initialized_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL
);

INSERT INTO platform_schema_version (description)
VALUES ('Milestone 1 - Foundation and Infrastructure Initialized');
