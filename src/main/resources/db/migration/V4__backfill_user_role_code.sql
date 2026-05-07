UPDATE "User" user_record
SET role_code = CASE
    WHEN user_record.id = '475' THEN 'SUPER_ADMIN'
    WHEN EXISTS (
        SELECT 1
        FROM "Admin" admin_record
        WHERE admin_record.user_id = user_record.id
          AND admin_record.status = TRUE
    ) THEN 'ADMIN'
    WHEN EXISTS (
        SELECT 1
        FROM "Agent" agent_record
        WHERE agent_record.user_id = user_record.id
          AND agent_record.status = TRUE
    ) THEN 'AGENT'
    ELSE 'CLIENT'
END
WHERE user_record.role_code IS NULL;
