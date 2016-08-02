alter table patient add num_studies integer;
update patient set num_studies = (
  select count(*) from study where study.patient_fk=patient.pk and study.rejection_state in (0,1));
alter table location add multi_ref integer, add uidmap_fk bigint, add object_type integer;
update location set object_type = 0;
alter table location modify object_type integer not null;
alter table location modify tsuid drop not null;
create table uidmap (pk bigint not null auto_increment, uidmap longblob not null, primary key (pk));
create index UK_i1lnahmehau3r3j9pdyxg3p3y on location (multi_ref);
alter table location add constraint FK_bfk5vl6eoxaf0hhwiu3rbgmkn foreign key (uidmap_fk) references uidmap;

--to be checked--
create index FK_bfk5vl6eoxaf0hhwiu3rbgmkn on location (uidmap_fk) ;
create sequence uidmap_pk_seq;
--to be checked--
