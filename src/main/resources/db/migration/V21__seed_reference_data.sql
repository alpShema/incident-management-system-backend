-- ── Locations ──────────────────────────────────────────────────────────────
INSERT INTO "Location" (id, name, description, created_at, updated_at)
VALUES
    ('loc-takoradi', 'Takoradi', 'Western Region office',        NOW(), NOW()),
    ('loc-accra',    'Accra',    'Greater Accra Region office',   NOW(), NOW()),
    ('loc-rwanda',   'Rwanda',   'Rwanda office',                 NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ── Severities ────────────────────────────────────────────────────────────
INSERT INTO "Severity" (id, name, description, created_at, updated_at)
VALUES
    ('sev-low',      'Low',      'Minor issue, no immediate action required', NOW(), NOW()),
    ('sev-moderate', 'Moderate', 'Requires attention but not urgent',         NOW(), NOW()),
    ('sev-high',     'High',     'Requires urgent attention',                 NOW(), NOW()),
    ('sev-critical', 'Critical', 'Immediate response required',               NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ── Incident Categories ───────────────────────────────────────────────────
INSERT INTO "IncidentCategory" (id, name, description, created_at, updated_at)
VALUES
    ('cat-it',         'IT',         'Information technology and systems',         NOW(), NOW()),
    ('cat-hr',         'HR',         'Human resources and personnel',              NOW(), NOW()),
    ('cat-facilities', 'Facilities', 'Building maintenance and physical security', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ── System seed user + agent (needed to satisfy IncidentType FK) ──────────
INSERT INTO "User" (id, email, full_name, created_at, updated_at)
VALUES ('system-seed', 'system@hilfe.internal', 'System', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO "Agent" (id, user_id, status, created_at, updated_at)
VALUES ('agent-seed', 'system-seed', FALSE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Incident Types (Topics) ──────────────────────────────────────────────
-- Look up actual category IDs by name (may differ from our seed IDs if data pre-existed)
INSERT INTO "IncidentType" (id, name, description, admin_id, agent_id, visible_to_group, category_id, created_at, updated_at)
SELECT v.id, v.name, v.description, 'system-seed', 'agent-seed', TRUE,
       (SELECT ic.id FROM "IncidentCategory" ic WHERE ic.name = v.category_name), NOW(), NOW()
FROM (VALUES
    ('type-account-issues',  'Account Issues',  'Login problems, password resets, account access',           'IT'),
    ('type-network-issues',  'Network Issues',  'Wi-Fi, VPN, internet connectivity problems',                'IT'),
    ('type-software-issues', 'Software Issues', 'Application errors, license issues, software installation', 'IT'),
    ('type-payroll',           'Payroll',           'Salary queries, deductions, payment issues',             'HR'),
    ('type-leave-time-off',    'Leave / Time-Off',  'Leave requests, time-off balance, absence reporting',   'HR'),
    ('type-workplace-conduct', 'Workplace Conduct', 'Conduct concerns, grievances, policy violations',       'HR'),
    ('type-maintenance', 'Maintenance', 'Building repairs, plumbing, electrical issues',       'Facilities'),
    ('type-security',    'Security',    'Access control, theft, unauthorized entry',            'Facilities'),
    ('type-safety',      'Safety',      'Fire hazards, health and safety, emergency equipment', 'Facilities')
) AS v(id, name, description, category_name)
ON CONFLICT (name) DO NOTHING;
