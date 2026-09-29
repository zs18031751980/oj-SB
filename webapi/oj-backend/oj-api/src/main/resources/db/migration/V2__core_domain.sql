-- 与原 Peewee 模型保持同名表和列名。已有部署上的表不会被重建。
create table if not exists users (
    id serial primary key, username varchar(50) unique, email varchar(100) unique,
    name varchar(100), password_hash varchar(255), is_active boolean not null default true,
    role varchar(20) not null default 'member', provider_role varchar(20), local_role varchar(20),
    last_login timestamp, provider varchar(50), provider_id varchar(255), avatar_url varchar(500),
    bio varchar(500), theme_preference varchar(10) default 'system',
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists problems (
    id serial primary key, title varchar(200) not null, description text not null,
    input_desc text not null default '', output_desc text not null default '',
    difficulty varchar(20) not null default '简单', time_limit integer not null default 1000,
    memory_limit integer not null default 256, created_by integer, is_public boolean not null default true,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists announcements (
    id serial primary key, title varchar(200) not null, content text not null,
    category varchar(50) not null default '系统公告', permission varchar(20) not null default 'member',
    created_by varchar(50), is_published boolean not null default true, published_at timestamp,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists favorites (
    id serial primary key, user_id integer not null references users(id) on delete cascade,
    problem_id integer not null, created_at timestamp not null default now(), updated_at timestamp not null default now(),
    unique(user_id, problem_id)
);
create table if not exists user_codes (
    id serial primary key, user_id integer not null references users(id) on delete cascade,
    problem_id integer not null, language varchar(50) not null, code text not null,
    created_at timestamp not null default now(), updated_at timestamp not null default now(),
    unique(user_id, problem_id, language)
);
create table if not exists learn_favorites (
    id serial primary key, user_id integer not null references users(id) on delete cascade,
    resource_id varchar(100) not null, created_at timestamp not null default now(), updated_at timestamp not null default now(),
    unique(user_id, resource_id)
);
create table if not exists learn_browsing_history (
    id serial primary key, user_id integer not null references users(id) on delete cascade,
    resource_id varchar(100) not null, browsed_at timestamp not null default now(),
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contests (
    id serial primary key, title varchar(200) not null, description text default '', contest_type varchar(50) default 'ACM',
    status varchar(20) default 'upcoming', start_time timestamp, end_time timestamp, created_by integer,
    is_public boolean default true, penalty_time integer default 20, lifecycle_state varchar(20) default 'DRAFT',
    freeze_time timestamp, published_at timestamp, finalized_at timestamp, thawed_at timestamp,
    final_revision integer default 0, rules_version varchar(255) default 'acm-2026-v1',
    allowed_languages text default '["cpp","python","java","go","javascript"]', active_submission_limit integer default 3,
    scoreboard_requested_version integer default 0, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_problems (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    problem_index varchar(10) not null, title varchar(200) not null, description text not null,
    input_desc text default '', output_desc text default '', correct_answer text not null,
    time_limit integer default 1000, memory_limit integer default 256, difficulty varchar(20) default '中等',
    language varchar(20) default 'cpp', samples text default '[]', score integer default 100, sort_order integer default 0,
    validation_version integer default 1, validation_status varchar(20) default 'PENDING', validation_error text,
    checker_config text default '{"checker":"text"}', package_digest varchar(64),
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_testcases (
    id serial primary key, contest_problem_id integer not null references contest_problems(id) on delete cascade,
    input_data text not null, expected_output text not null, is_sample boolean default false, sort_order integer default 0,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_participants (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    user_id integer not null references users(id) on delete cascade, score integer default 0, rank integer,
    created_at timestamp not null default now(), updated_at timestamp not null default now(), unique(contest_id, user_id)
);
create table if not exists submissions (
    id serial primary key, user_id integer references users(id) on delete set null,
    problem_id integer not null references problems(id), code text not null, language varchar(50) not null,
    status varchar(20) not null default 'Pending', time_used integer, memory_used integer, testcase_results text,
    fail_testcase_index integer, job_id varchar(64) unique, attempt_id integer not null default 0,
    idempotency_key varchar(128), created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists submission_outbox (
    id serial primary key, submission_id integer not null unique references submissions(id) on delete cascade,
    state varchar(20) not null default 'PENDING', dispatch_attempts integer not null default 0,
    last_error text, dispatched_at timestamp, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists discussions (
    id serial primary key, title varchar(200) not null, content text not null,
    author_id integer not null references users(id), category varchar(50) default '全部', tags varchar(500),
    reply_count integer default 0, like_count integer default 0, view_count integer default 0,
    is_pinned boolean default false, is_closed boolean default false,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists discussion_replies (
    id serial primary key, discussion_id integer not null references discussions(id) on delete cascade,
    author_id integer not null references users(id), content text not null, like_count integer default 0,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists discussion_likes (
    id serial primary key, discussion_id integer not null references discussions(id) on delete cascade,
    user_id integer not null references users(id) on delete cascade, created_at timestamp not null default now(),
    updated_at timestamp not null default now(), unique(discussion_id, user_id)
);
create table if not exists discussion_reply_likes (
    id serial primary key, reply_id integer not null references discussion_replies(id) on delete cascade,
    user_id integer not null references users(id) on delete cascade, created_at timestamp not null default now(),
    updated_at timestamp not null default now(), unique(reply_id, user_id)
);
create table if not exists auth_sessions (
    id varchar(64) primary key, user_id integer not null references users(id) on delete cascade,
    refresh_hash varchar(64) not null, previous_refresh_hash varchar(64), refresh_request_id varchar(128),
    refresh_retry_ciphertext text, refresh_retry_until timestamp, expires_at timestamp not null, revoked boolean not null default false,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists oauth_grants (
    id varchar(64) primary key, user_id integer not null references users(id) on delete cascade,
    binding_hash varchar(64) not null, expires_at timestamp not null,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists user_judge_stats (
    user_id integer primary key references users(id) on delete cascade, rank integer default 0, solved_count integer default 0,
    rating integer default 0, easy_count integer default 0, medium_count integer default 0, hard_count integer default 0,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_submissions (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    user_id integer not null references users(id) on delete cascade,
    contest_problem_id integer not null references contest_problems(id) on delete cascade,
    problem_index varchar(10) default '', status varchar(32) default 'Pending', verdict varchar(32),
    passed integer default 0, total integer default 0, score integer default 0, language varchar(20) default 'cpp',
    code text default '', judge_submission_id varchar(64) unique, job_id varchar(64) unique,
    attempt_id integer default 1, worker_id varchar(128), queued_at timestamp, judge_started_at timestamp,
    finished_at timestamp, cpu_time integer, wall_time integer, memory bigint, testcase_results text,
    error_message text, idempotency_key varchar(128), received_at timestamp, contest_eligible boolean default true,
    submitted_at timestamp, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_judge_outbox (
    id serial primary key, submission_id integer not null unique references contest_submissions(id) on delete cascade,
    state varchar(20) not null default 'PENDING', dispatch_attempts integer default 0, last_error text,
    dispatched_at timestamp, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_roles (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    user_id integer not null references users(id) on delete cascade, role varchar(50) not null,
    created_at timestamp not null default now(), updated_at timestamp not null default now(), unique(contest_id, user_id)
);
create table if not exists contest_teams (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    captain_id integer not null references users(id), name varchar(120) not null,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_team_members (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    team_id integer not null references contest_teams(id) on delete cascade,
    user_id integer not null references users(id) on delete cascade,
    created_at timestamp not null default now(), updated_at timestamp not null default now(), unique(contest_id, user_id)
);
create table if not exists contest_audit (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    actor_id integer not null references users(id), action varchar(255) not null, reason text not null,
    payload text not null default '{}', created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_events (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    kind varchar(255) not null, audience varchar(50) not null default 'jury', recipient_id integer,
    payload text not null, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_clarifications (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    author_id integer not null references users(id), question text not null, answer text,
    claimed_by integer, answered_by integer, broadcast boolean default false,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_scoreboard_snapshots (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    snapshot_kind varchar(20) not null, payload text not null, scoreboard_version integer default 0,
    event_cursor bigint default 0, created_at timestamp not null default now(), updated_at timestamp not null default now(),
    unique(contest_id, snapshot_kind)
);
create table if not exists rejudge_batches (
    id serial primary key, contest_id integer not null references contests(id) on delete cascade,
    actor_id integer not null references users(id), reason text not null, state varchar(50) not null default 'PENDING',
    reviewed_by integer, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists contest_packages (
    digest varchar(64) primary key, problem_id integer not null references contest_problems(id) on delete cascade,
    payload text not null, actor_id integer, validation_state varchar(50) default 'VALID', validation_error text,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists testcases (
    id serial primary key, problem_id integer not null references problems(id) on delete cascade,
    input_data text not null, output_data text not null, is_sample boolean default false, sort_order integer default 0,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists ranking_projection_state (
    id integer primary key default 1, built_at timestamp,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists judgements (
    id serial primary key, submission_id integer not null references contest_submissions(id) on delete cascade,
    attempt_id integer not null, status varchar(32) not null, payload text not null,
    package_digest varchar(64), batch_id integer, created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists jury_mfa_state (
    user_id integer primary key references users(id) on delete cascade, last_counter bigint not null default -1,
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
create table if not exists reference_validation_jobs (
    id varchar(64) primary key, problem_id integer not null references contest_problems(id) on delete cascade,
    version integer not null, state varchar(20) not null default 'PENDING',
    created_at timestamp not null default now(), updated_at timestamp not null default now()
);
