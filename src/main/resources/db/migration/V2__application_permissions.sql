GRANT USAGE ON SCHEMA public TO "${applicationUser}";

GRANT SELECT, INSERT, UPDATE ON
    users, user_roles, projects, project_members, task_types, task_priorities, tasks
    TO "${applicationUser}";
GRANT SELECT ON roles TO "${applicationUser}";
GRANT SELECT, INSERT ON comments, time_entries TO "${applicationUser}";
GRANT DELETE ON user_roles, project_members TO "${applicationUser}";

GRANT USAGE, SELECT ON SEQUENCE
    users_id_seq, projects_id_seq, task_types_id_seq, task_priorities_id_seq,
    tasks_id_seq, comments_id_seq, time_entries_id_seq
    TO "${applicationUser}";
