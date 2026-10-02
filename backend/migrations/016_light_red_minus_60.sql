-- Rosso Chiaro: da -80 a -60. SQLite non permette di cambiare un CHECK, quindi si ricrea la
-- tabella convertendo i voti gia' presenti. Da applicare PRIMA del deploy del Worker.
CREATE TABLE interrogation_votes_new (
    slot_id TEXT NOT NULL REFERENCES interrogation_slots(id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vote_score INTEGER NOT NULL CHECK(vote_score IN (50, 0, -60, -300)),
    PRIMARY KEY (slot_id, student_id)
);

INSERT INTO interrogation_votes_new (slot_id, student_id, vote_score)
SELECT slot_id, student_id, CASE WHEN vote_score = -80 THEN -60 ELSE vote_score END
FROM interrogation_votes;

DROP TABLE interrogation_votes;
ALTER TABLE interrogation_votes_new RENAME TO interrogation_votes;
