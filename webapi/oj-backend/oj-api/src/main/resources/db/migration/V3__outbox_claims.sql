alter table submission_outbox add column if not exists claimed_at timestamp;
alter table submission_outbox add column if not exists claim_owner varchar(128);
alter table contest_judge_outbox add column if not exists claimed_at timestamp;
alter table contest_judge_outbox add column if not exists claim_owner varchar(128);
alter table contest_submissions add column if not exists rejudge_of integer references contest_submissions(id);
alter table contest_submissions add column if not exists rejudge_base_attempt integer;
create table if not exists judge_dead_letters (
    id serial primary key, queue varchar(100) not null, job_id varchar(64) not null,
    reason text not null, state varchar(20) not null default 'OPEN',
    created_at timestamp not null default now(), resolved_at timestamp
);
