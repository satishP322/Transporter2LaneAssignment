CREATE TABLE IF NOT EXISTS input_dataset (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    payload CLOB NOT NULL
);
