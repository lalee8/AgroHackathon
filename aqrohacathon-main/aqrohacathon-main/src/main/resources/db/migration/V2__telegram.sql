-- Telegram bildiriş kanalı: istifadəçinin bot ilə əlaqələndirilmiş chat identifikatoru
ALTER TABLE users ADD COLUMN telegram_chat_id BIGINT;
