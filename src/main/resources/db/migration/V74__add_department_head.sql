ALTER TABLE "Department"
    ADD COLUMN IF NOT EXISTS head_user_id VARCHAR(255);

ALTER TABLE "Department"
    ADD CONSTRAINT fk_department_head_user
        FOREIGN KEY (head_user_id) REFERENCES "User"(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_department_head_user_id
    ON "Department" (head_user_id);
