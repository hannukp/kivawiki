set role kivawiki;
create schema kivawiki;

set role kivawiki;
drop table if exists kivawiki.drafts;

create table kivawiki.drafts (
	id bigserial primary key,
    username varchar(256) not null,
    proj varchar(256) not null,
    uri varchar(256) not null,
    val bytea not null,
    added timestamp with time zone not null default now()
);

drop index if exists drafts_ix1;
create index drafts_ix1 on kivawiki.drafts (username, proj, added);
