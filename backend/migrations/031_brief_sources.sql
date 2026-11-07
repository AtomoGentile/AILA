-- Opening the Circulars section acknowledges the existing circulars for this user and class.
CREATE TABLE IF NOT EXISTS aca_brief_circular_reads (
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
 circular_number INTEGER NOT NULL REFERENCES circulars(number) ON DELETE CASCADE,
 PRIMARY KEY(user_id,class_id,circular_number)
);
-- A retry for one class must not resend a notification already delivered to another class.
CREATE TABLE IF NOT EXISTS circular_notification_deliveries (
 circular_number INTEGER NOT NULL REFERENCES circulars(number) ON DELETE CASCADE,
 class_id TEXT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
 PRIMARY KEY(circular_number,class_id)
);
