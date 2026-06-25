-- Migrate IncidentCategory.status from VARCHAR('active'/'inactive') to BOOLEAN
ALTER TABLE "IncidentCategory"
    ALTER COLUMN status TYPE BOOLEAN USING (status = 'active');
ALTER TABLE "IncidentCategory"
    ALTER COLUMN status SET DEFAULT true;

-- Migrate IncidentType.status from VARCHAR('active'/'inactive') to BOOLEAN
ALTER TABLE "IncidentType"
    ALTER COLUMN status TYPE BOOLEAN USING (status = 'active');
ALTER TABLE "IncidentType"
    ALTER COLUMN status SET DEFAULT true;
