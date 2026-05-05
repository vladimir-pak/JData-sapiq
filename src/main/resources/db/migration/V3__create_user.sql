CREATE TABLE IF NOT EXISTS sapiq_metadata.users (
                       id SERIAL PRIMARY KEY,
                       username VARCHAR(50) UNIQUE NOT NULL,
                       password VARCHAR(200) NOT NULL,
                       enabled BOOLEAN DEFAULT true
);
INSERT INTO sapiq_metadata.users (username, password, enabled)
VALUES (
           'admin',
           '$2a$12$RZXV1km2KvXOcOiqpsgqDukCFTmF6WcfX.WgFghmt7Edl57g6fUn6',
           true
       )
    ON CONFLICT (username) DO NOTHING;