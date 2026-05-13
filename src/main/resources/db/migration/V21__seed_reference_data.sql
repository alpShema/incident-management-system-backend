-- ── Locations ──────────────────────────────────────────────────────────────
INSERT INTO "Location" (id, name, description, created_at, updated_at)
VALUES
    ('loc-takoradi', 'Takoradi', 'Western Region office',        NOW(), NOW()),
    ('loc-accra',    'Accra',    'Greater Accra Region office',   NOW(), NOW()),
    ('loc-rwanda',   'Rwanda',   'Rwanda office',                 NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Severities ────────────────────────────────────────────────────────────
INSERT INTO "Severity" (id, name, description, created_at, updated_at)
VALUES
    ('sev-low',      'Low',      'Minor issue, no immediate action required', NOW(), NOW()),
    ('sev-moderate', 'Moderate', 'Requires attention but not urgent',         NOW(), NOW()),
    ('sev-high',     'High',     'Requires urgent attention',                 NOW(), NOW()),
    ('sev-critical', 'Critical', 'Immediate response required',               NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Incident Categories ───────────────────────────────────────────────────
INSERT INTO "IncidentCategory" (id, name, description, created_at, updated_at)
VALUES
    ('cat-it',         'IT',         'Information technology and systems',         NOW(), NOW()),
    ('cat-hr',         'HR',         'Human resources and personnel',              NOW(), NOW()),
    ('cat-facilities', 'Facilities', 'Building maintenance and physical security', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── System seed user + agent (needed to satisfy IncidentType FK) ──────────
INSERT INTO "User" (id, email, full_name, created_at, updated_at)
VALUES ('system-seed', 'system@hilfe.internal', 'System', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO "Agent" (id, user_id, status, created_at, updated_at)
VALUES ('agent-seed', 'system-seed', FALSE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Incident Types (Topics) ──────────────────────────────────────────────
-- IT
INSERT INTO "IncidentType" (id, name, description, admin_id, agent_id, visible_to_group, category_id, created_at, updated_at)
VALUES
    ('type-account-issues',  'Account Issues',  'Login problems, password resets, account access',           'system-seed', 'agent-seed', TRUE, 'cat-it', NOW(), NOW()),
    ('type-network-issues',  'Network Issues',  'Wi-Fi, VPN, internet connectivity problems',                'system-seed', 'agent-seed', TRUE, 'cat-it', NOW(), NOW()),
    ('type-software-issues', 'Software Issues', 'Application errors, license issues, software installation', 'system-seed', 'agent-seed', TRUE, 'cat-it', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- HR
INSERT INTO "IncidentType" (id, name, description, admin_id, agent_id, visible_to_group, category_id, created_at, updated_at)
VALUES
    ('type-payroll',           'Payroll',           'Salary queries, deductions, payment issues',            'system-seed', 'agent-seed', TRUE, 'cat-hr', NOW(), NOW()),
    ('type-leave-time-off',    'Leave / Time-Off',  'Leave requests, time-off balance, absence reporting',  'system-seed', 'agent-seed', TRUE, 'cat-hr', NOW(), NOW()),
    ('type-workplace-conduct', 'Workplace Conduct', 'Conduct concerns, grievances, policy violations',      'system-seed', 'agent-seed', TRUE, 'cat-hr', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Facilities
INSERT INTO "IncidentType" (id, name, description, admin_id, agent_id, visible_to_group, category_id, created_at, updated_at)
VALUES
    ('type-maintenance', 'Maintenance', 'Building repairs, plumbing, electrical issues',       'system-seed', 'agent-seed', TRUE, 'cat-facilities', NOW(), NOW()),
    ('type-security',    'Security',    'Access control, theft, unauthorized entry',            'system-seed', 'agent-seed', TRUE, 'cat-facilities', NOW(), NOW()),
    ('type-safety',      'Safety',      'Fire hazards, health and safety, emergency equipment', 'system-seed', 'agent-seed', TRUE, 'cat-facilities', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
