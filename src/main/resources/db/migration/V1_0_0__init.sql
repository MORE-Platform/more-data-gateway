-- Simple Auth-Table for standalone Testing
CREATE TABLE IF NOT EXISTS gateway_user_details (
    api_id VARCHAR NOT NULL PRIMARY KEY,
    api_key VARCHAR NOT NULL,
    study_id VARCHAR NOT NULL,
    participant_id VARCHAR NOT NULL
);
