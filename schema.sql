CREATE TABLE users (
    phone_number VARCHAR(11) NOT NULL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_online BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE messages(
    message_id SERIAL PRIMARY KEY,
    sender_phone VARCHAR(11) NOT NULL REFERENCES users(phone_number) ON DELETE CASCADE,
    recipient_phone VARCHAR(11) NOT NULL REFERENCES users(phone_number) ON DELETE CASCADE,
    msg_content text NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);