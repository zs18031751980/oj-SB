-- 判题阶段时间戳与结果指标，用于阶段耗时统计与结果审计。
alter table contest_submissions add column if not exists compile_started_at timestamp;
alter table contest_submissions add column if not exists compile_finished_at timestamp;
alter table contest_submissions add column if not exists execution_started_at timestamp;
alter table contest_submissions add column if not exists execution_finished_at timestamp;
alter table contest_submissions add column if not exists checked_at timestamp;
alter table contest_submissions add column if not exists output_size integer;
alter table contest_submissions add column if not exists exit_code integer;
alter table contest_submissions add column if not exists signal integer;
alter table contest_submissions add column if not exists package_digest varchar(64);
alter table contest_submissions add column if not exists request_digest varchar(64);
alter table contest_submissions add column if not exists team_id integer references contest_teams(id);
