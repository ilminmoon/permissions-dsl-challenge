insert into users (id, email, name) values
    ('u1', 'user@example.com', 'Scenario User'),
    ('u2', 'creator@example.com', 'Document Creator')
on conflict (id) do nothing;

insert into teams (id, name, plan) values
    ('t1', 'Team t1', 'pro'),
    ('t2', 'Team t2', 'pro'),
    ('t3', 'Team t3', 'free'),
    ('t4', 'Team t4', 'pro'),
    ('t5', 'Team t5', 'pro'),
    ('t6', 'Team t6', 'pro')
on conflict (id) do nothing;

insert into projects (id, name, team_id, visibility) values
    ('p1', 'Project p1', 't1', 'private'),
    ('p2', 'Project p2', 't2', 'private'),
    ('p3', 'Project p3', 't3', 'public'),
    ('p4', 'Project p4', 't4', 'private'),
    ('p5', 'Project p5', 't5', 'private'),
    ('p6', 'Project p6', 't6', 'private')
on conflict (id) do nothing;

insert into documents (id, title, project_id, creator_id, deleted_at, public_link_enabled) values
    ('d1', 'Document d1', 'p1', 'u2', null, false),
    ('d2', 'Document d2', 'p2', 'u1', '2026-03-31T12:00:00Z', false),
    ('d3', 'Document d3', 'p3', 'u2', null, false),
    ('d4', 'Document d4', 'p4', 'u2', null, false),
    ('d5', 'Document d5', 'p5', 'u2', null, false),
    ('d6', 'Document d6', 'p6', 'u2', null, true)
on conflict (id) do nothing;

insert into team_memberships (user_id, team_id, role) values
    ('u1', 't1', 'viewer'),
    ('u1', 't2', 'admin'),
    ('u1', 't3', 'viewer'),
    ('u1', 't4', 'admin'),
    ('u1', 't5', 'editor')
on conflict (user_id, team_id) do nothing;

insert into project_memberships (user_id, project_id, role) values
    ('u1', 'p1', 'editor'),
    ('u1', 'p2', 'admin'),
    ('u1', 'p3', 'admin')
on conflict (user_id, project_id) do nothing;
