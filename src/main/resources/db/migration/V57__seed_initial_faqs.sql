-- Initial FAQ seed (embeddings backfilled once API key is available)
INSERT INTO "Faq" (id, question, answer, keywords, category, active, created_at, updated_at)
VALUES
    ('faq-001',
     'How do I reset my password?',
     'To reset your password, click the "Forgot Password" link on the login page. Enter your registered email address and you will receive a reset link within a few minutes. The link expires after 24 hours.',
     ARRAY['password', 'reset', 'forgot', 'login', 'credentials'],
     'Account',
     TRUE,
     NOW(),
     NOW()),

    ('faq-002',
     'How do I report an incident?',
     'To report an incident, log in to the portal and click "Report Incident" from the dashboard. Fill in the incident title, description, location, and topic, then submit. You will receive a confirmation with your incident number.',
     ARRAY['report', 'incident', 'submit', 'create', 'new incident'],
     'Incidents',
     TRUE,
     NOW(),
     NOW()),

    ('faq-003',
     'How do I check the status of my incident?',
     'You can check your incident status by navigating to "My Incidents" on the dashboard. Each incident shows its current status (Open, In Progress, Resolved, or Closed). Click on an incident to see the full timeline and any agent updates.',
     ARRAY['status', 'incident', 'progress', 'update', 'track'],
     'Incidents',
     TRUE,
     NOW(),
     NOW()),

    ('faq-004',
     'Who can I contact for urgent support?',
     'For urgent issues, please raise an incident and set the severity to "Critical" or "High". Our team prioritises high-severity incidents and aims to respond within one hour. For life-threatening emergencies, contact emergency services directly.',
     ARRAY['urgent', 'emergency', 'contact', 'support', 'critical'],
     'Support',
     TRUE,
     NOW(),
     NOW()),

    ('faq-005',
     'How long does it take to resolve an incident?',
     'Resolution times depend on severity. Critical incidents are targeted for resolution within 4 hours, High within 8 hours, Medium within 24 hours, and Low within 72 hours. Complex issues may take longer and an agent will keep you updated.',
     ARRAY['resolve', 'time', 'sla', 'response', 'how long'],
     'Incidents',
     TRUE,
     NOW(),
     NOW())
ON CONFLICT (id) DO NOTHING;
