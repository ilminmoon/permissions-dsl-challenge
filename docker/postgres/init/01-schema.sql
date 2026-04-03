create table if not exists users (
    id text primary key check (btrim(id) <> ''),
    email text not null unique check (btrim(email) <> ''),
    name text not null check (btrim(name) <> '')
);

create table if not exists teams (
    id text primary key check (btrim(id) <> ''),
    name text not null check (btrim(name) <> ''),
    plan text not null check (plan in ('free', 'pro', 'enterprise'))
);

create table if not exists projects (
    id text primary key check (btrim(id) <> ''),
    name text not null check (btrim(name) <> ''),
    team_id text not null references teams(id),
    visibility text not null check (visibility in ('private', 'public'))
);

create table if not exists documents (
    id text primary key check (btrim(id) <> ''),
    title text not null check (btrim(title) <> ''),
    project_id text not null references projects(id),
    creator_id text not null references users(id),
    deleted_at timestamptz null,
    public_link_enabled boolean not null
);

create table if not exists team_memberships (
    user_id text not null references users(id),
    team_id text not null references teams(id),
    role text not null check (role in ('viewer', 'editor', 'admin')),
    primary key (user_id, team_id)
);

create table if not exists project_memberships (
    user_id text not null references users(id),
    project_id text not null references projects(id),
    role text not null check (role in ('viewer', 'editor', 'admin')),
    primary key (user_id, project_id)
);

create index if not exists idx_documents_project_id on documents(project_id);
create index if not exists idx_documents_creator_id on documents(creator_id);
create index if not exists idx_projects_team_id on projects(team_id);
create index if not exists idx_team_memberships_lookup on team_memberships(user_id, team_id);
create index if not exists idx_project_memberships_lookup on project_memberships(user_id, project_id);
