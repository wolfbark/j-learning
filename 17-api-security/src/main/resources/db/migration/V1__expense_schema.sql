create table expense_report (
    id             bigserial primary key,
    owner_username varchar(64)  not null,
    team           varchar(64)  not null,
    merchant       varchar(128) not null,
    amount_cents   bigint       not null,
    currency       varchar(3)   not null,
    category       varchar(32)  not null,
    status         varchar(16)  not null,
    card_last4     varchar(4),
    receipt_url    varchar(1024),
    receipt_bytes  integer,
    created_at     timestamptz  not null default now()
);

create index idx_expense_report_owner on expense_report (owner_username);
create index idx_expense_report_team on expense_report (team);

create table team_member (
    username varchar(64) primary key,
    team     varchar(64) not null
);

create table team_manager (
    team             varchar(64) not null,
    manager_username varchar(64) not null,
    primary key (team, manager_username)
);
