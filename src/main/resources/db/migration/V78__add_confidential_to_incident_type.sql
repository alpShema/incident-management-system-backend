-- HV-1619: allow admins to mark an incident topic as confidential.
-- Confidential topics route incidents to their linked agent group (or single agent),
-- keep escalation within that group, and hide incident details from everyone else.

ALTER TABLE "IncidentType"
    ADD COLUMN confidential BOOLEAN NOT NULL DEFAULT false;
