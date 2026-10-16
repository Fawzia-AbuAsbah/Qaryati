-- REVIEW SCRIPT ONLY: not executed.
-- Run only after approval using psql -X -W -v ON_ERROR_STOP=1
-- -h localhost -p 5432 -U qaryati_schema_test_user -d qaryati_schema_test
-- -f docs/database/tests/V1_data_integrity_tests.sql
-- Exclusive use of the disposable test database is required.
-- Explicit fixture IDs avoid advancing nontransactional identity sequences.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
DO $guard$
DECLARE t record; has_rows boolean;
BEGIN
 IF current_database()<>'qaryati_schema_test'
 OR current_user<>'qaryati_schema_test_user'
 OR session_user<>'qaryati_schema_test_user' THEN
  RAISE EXCEPTION 'Wrong database or role';
 END IF;
 IF current_setting('server_version_num')::integer/10000<>17 THEN
  RAISE EXCEPTION 'PostgreSQL 17 required';
 END IF;
 IF (SELECT count(*) FROM information_schema.tables
     WHERE table_schema='public' AND table_type='BASE TABLE'
       AND table_name<>'flyway_schema_history')<>27 THEN
  RAISE EXCEPTION 'Expected 27 application tables';
 END IF;
 IF (SELECT count(*) FROM public.flyway_schema_history WHERE version IS NOT NULL)<>1
 OR NOT EXISTS(SELECT 1 FROM public.flyway_schema_history
               WHERE version='1' AND type='SQL' AND success) THEN
  RAISE EXCEPTION 'Expected exactly one successful V1';
 END IF;
 IF (SELECT count(*) FROM pg_constraint k
     JOIN pg_class c ON c.oid=k.conrelid
     JOIN pg_namespace n ON n.oid=c.relnamespace
     WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
       AND k.contype='f' AND k.convalidated)<>32 THEN
  RAISE EXCEPTION 'Expected 32 validated application foreign keys';
 END IF;
 IF (SELECT count(*) FROM pg_constraint k
     JOIN pg_class c ON c.oid=k.conrelid
     JOIN pg_namespace n ON n.oid=c.relnamespace
     WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
       AND k.contype='c' AND k.convalidated)<>46 THEN
  RAISE EXCEPTION 'Expected 46 validated application CHECK constraints';
 END IF;
 FOR t IN SELECT table_name FROM information_schema.tables
          WHERE table_schema='public' AND table_type='BASE TABLE'
            AND table_name<>'flyway_schema_history'
 LOOP
  EXECUTE format('SELECT EXISTS(SELECT 1 FROM public.%I)',t.table_name) INTO has_rows;
  IF has_rows THEN RAISE EXCEPTION 'Application tables must be empty: %',t.table_name; END IF;
 END LOOP;
END; $guard$;

-- Report actual catalog constraints for review; do not change definitions.
SELECT c.relname,k.conname,k.contype,k.convalidated,pg_get_constraintdef(k.oid)
FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname<>'flyway_schema_history'
ORDER BY c.relname,k.contype,k.conname;

CREATE TEMP TABLE integrity_results(
 label text PRIMARY KEY,expected_state text NOT NULL,actual_state text NOT NULL,
 actual_constraint text,passed boolean NOT NULL,detail text
) ON COMMIT DROP;

-- EXCEPTION blocks are per-case subtransaction savepoints.
-- Force successful statements to roll back too; fixture state never changes.
CREATE FUNCTION pg_temp.expect(
 test_label text,statement_sql text,expected text DEFAULT '00000',
 expected_constraint text DEFAULT NULL
) RETURNS void LANGUAGE plpgsql AS $fn$
DECLARE actual text; hit text; msg text;
BEGIN
 BEGIN
  EXECUTE statement_sql;
  RAISE EXCEPTION USING ERRCODE='ZX001',MESSAGE='successful test rollback';
 EXCEPTION WHEN OTHERS THEN
  GET STACKED DIAGNOSTICS actual=RETURNED_SQLSTATE,hit=CONSTRAINT_NAME,msg=MESSAGE_TEXT;
  IF actual='ZX001' THEN actual:='00000'; END IF;
 END;
 INSERT INTO pg_temp.integrity_results
 (label,expected_state,actual_state,actual_constraint,passed,detail)
 VALUES(test_label,expected,actual,hit,
        actual=expected AND(expected_constraint IS NULL OR hit=expected_constraint),msg);
END; $fn$;

-- Ordered fixtures: all 27 tables. Test password_hash is not a login credential.
INSERT INTO public.governorates (id,name_ar,code) VALUES
 (-91001,'__it_g1','__IT_G1'),(-91002,'__it_g2','__IT_G2');
INSERT INTO public.local_authorities (id,governorate_id,name_ar,authority_type) VALUES
 (-91001,-91001,'__it_authority','VILLAGE_COUNCIL');
INSERT INTO public.villages (id,governorate_id,local_authority_id,name_ar,location_description,latitude,longitude,area_km2) VALUES
 (-91001,-91001,-91001,'__it_v1','Test',32,35,1),(-91002,-91002,NULL,'__it_v2','Test',NULL,NULL,NULL);
INSERT INTO public.village_names (id,village_id,name,language_code,name_type) VALUES
 (-91001,-91001,'__it_alias','ar','ALTERNATIVE');
INSERT INTO public.village_relations (id,village_id,related_village_id,relation_type,distance_km) VALUES
 (-91001,-91001,-91002,'NEARBY',0);
INSERT INTO public.users (id,username,email,password_hash,display_name) VALUES
 (-91001,'__it_actor','actor@example.invalid','TEST_ONLY_NOT_A_LOGIN_HASH','Actor'),(-91002,'__it_reviewer','reviewer@example.invalid','TEST_ONLY_NOT_A_LOGIN_HASH','Reviewer'),(-91003,'__it_resolver','resolver@example.invalid','TEST_ONLY_NOT_A_LOGIN_HASH','Resolver'),(-91004,'__it_cascade','cascade@example.invalid','TEST_ONLY_NOT_A_LOGIN_HASH','Cascade');
INSERT INTO public.roles (id,code) VALUES
 (-91001,'__IT_ROLE');
INSERT INTO public.user_roles (user_id,role_id) VALUES
 (-91004,-91001);
INSERT INTO public.sources (id,title,source_type,url) VALUES
 (-91001,'Test source 1','CENSUS','https://example.invalid'),(-91002,'Test source 2','BOOK',NULL);
INSERT INTO public.population_records (id,village_id,reference_year,reference_date,population_count,methodology,source_id) VALUES
 (-91001,-91001,2000,'2000-06-01',0,'CENSUS',-91001);
INSERT INTO public.village_histories (id,village_id,overview) VALUES
 (-91001,-91001,'Test history');
INSERT INTO public.historical_events (id,village_id,title,description,event_date,year_from,year_to,date_precision) VALUES
 (-91001,-91001,'Exact','Test','1948-05-15',NULL,NULL,'EXACT'),(-91002,-91001,'Year','Test',NULL,1948,NULL,'YEAR'),(-91003,-91001,'Period','Test',NULL,1900,1950,'PERIOD'),(-91004,-91001,'Unknown','Test',NULL,NULL,NULL,'UNKNOWN');
INSERT INTO public.heritage_sites (id,village_id,name,site_type,latitude,longitude) VALUES
 (-91001,-91001,'__it_site','ARCHAEOLOGICAL',32,35);
INSERT INTO public.cultural_heritage (id,village_id,name,heritage_type,description) VALUES
 (-91001,-91001,'__it_culture','CRAFT','Test');
INSERT INTO public.village_sources (village_id,source_id,claim_note) VALUES
 (-91001,-91001,'Test claim');
INSERT INTO public.history_sources (village_history_id,source_id) VALUES
 (-91001,-91001);
INSERT INTO public.event_sources (event_id,source_id) VALUES
 (-91001,-91001);
INSERT INTO public.heritage_site_sources (heritage_site_id,source_id) VALUES
 (-91001,-91001);
INSERT INTO public.cultural_heritage_sources (cultural_heritage_id,source_id) VALUES
 (-91001,-91001);
INSERT INTO public.agricultural_products (id,name_ar,product_type) VALUES
 (-91001,'__it_product','CROP');
INSERT INTO public.village_agriculture (id,village_id,product_id,importance) VALUES
 (-91001,-91001,-91001,'HIGH');
INSERT INTO public.economic_activities (id,village_id,name,activity_type) VALUES
 (-91001,-91001,'__it_economy','TRADE');
INSERT INTO public.community_facilities (id,village_id,name,facility_type,latitude,longitude) VALUES
 (-91001,-91001,'__it_facility','SCHOOL',32,35);
INSERT INTO public.audit_logs (id,actor_user_id,actor_label,entity_type,entity_id,action) VALUES
 (-91001,-91001,'user:-91001 / Actor','villages',-91001,'CREATE'),(-91002,NULL,'system:integrity-test','villages',-91001,'UPDATE');
INSERT INTO public.verification_records (id,entity_type,entity_id,reviewer_user_id,decision) VALUES
 (-91001,'villages',-91001,-91002,'VERIFIED');
INSERT INTO public.data_conflicts (id,entity_type,entity_id,field_name,conflicting_values,status,created_at,resolved_by,resolved_at,resolution_notes) VALUES
 (-91001,'villages',-91001,'name_ar','["a","b"]','OPEN','2020-01-01 00:00:00+00',NULL,NULL,NULL),(-91002,'villages',-91001,'name_en','["a","b"]','IN_REVIEW','2020-01-01 00:00:00+00',NULL,NULL,NULL),(-91003,'villages',-91001,'area_km2','[1,2]','RESOLVED','2020-01-01 00:00:00+00',-91003,'2020-01-02 00:00:00+00','Reviewed evidence');
INSERT INTO public.geocoding_cache (id,provider,query_hash,response_json,cached_at,expires_at) VALUES
 (-91001,'__it_provider',repeat('a',64),'{}','2020-01-01 00:00:00+00','2020-01-02 00:00:00+00');

SAVEPOINT constraint_cases;
SELECT pg_temp.expect('reject authority type',$test$UPDATE public.local_authorities SET authority_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject village latitude',$test$UPDATE public.villages SET latitude=91 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject village longitude',$test$UPDATE public.villages SET longitude=181 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject village area',$test$UPDATE public.villages SET area_km2=0 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject village status',$test$UPDATE public.villages SET status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject village coordinate pair',$test$UPDATE public.villages SET longitude=NULL WHERE id=-91001$test$,'23514','ck_village_coordinates_pair');
SELECT pg_temp.expect('reject name type',$test$UPDATE public.village_names SET name_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject relation type',$test$UPDATE public.village_relations SET relation_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject relation distance',$test$UPDATE public.village_relations SET distance_km=-1 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject relation both targets',$test$UPDATE public.village_relations SET related_place_name='Extra' WHERE id=-91001$test$,'23514','ck_relation_target');
SELECT pg_temp.expect('reject relation no targets',$test$UPDATE public.village_relations SET related_village_id=NULL WHERE id=-91001$test$,'23514','ck_relation_target');
SELECT pg_temp.expect('reject relation self target',$test$UPDATE public.village_relations SET related_village_id=village_id WHERE id=-91001$test$,'23514','ck_no_self_relation');
SELECT pg_temp.expect('reject source type',$test$UPDATE public.sources SET source_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject source URL',$test$UPDATE public.sources SET url='ftp://example.invalid' WHERE id=-91001$test$,'23514','ck_source_link');
SELECT pg_temp.expect('reject population year',$test$UPDATE public.population_records SET reference_year=1799,reference_date=NULL WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject population count',$test$UPDATE public.population_records SET population_count=-1 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject population methodology',$test$UPDATE public.population_records SET methodology='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject population status',$test$UPDATE public.population_records SET verification_status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject population date mismatch',$test$UPDATE public.population_records SET reference_date='2001-01-01' WHERE id=-91001$test$,'23514','ck_population_year_date');
SELECT pg_temp.expect('reject history status',$test$UPDATE public.village_histories SET verification_status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject event start year',$test$UPDATE public.historical_events SET year_from=0 WHERE id=-91002$test$,'23514');
SELECT pg_temp.expect('reject event end year',$test$UPDATE public.historical_events SET year_to=2201 WHERE id=-91003$test$,'23514');
SELECT pg_temp.expect('reject event precision vocabulary',$test$UPDATE public.historical_events SET date_precision='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject event status',$test$UPDATE public.historical_events SET verification_status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject event reversed period',$test$UPDATE public.historical_events SET year_from=1960 WHERE id=-91003$test$,'23514','ck_history_year_range');
SELECT pg_temp.expect('reject exact missing date',$test$UPDATE public.historical_events SET event_date=NULL WHERE id=-91001$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject exact extra start',$test$UPDATE public.historical_events SET year_from=1948 WHERE id=-91001$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject exact extra years',$test$UPDATE public.historical_events SET year_from=1948,year_to=1948 WHERE id=-91001$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject year missing start',$test$UPDATE public.historical_events SET year_from=NULL WHERE id=-91002$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject year extra date',$test$UPDATE public.historical_events SET event_date='1948-01-01' WHERE id=-91002$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject year extra end',$test$UPDATE public.historical_events SET year_to=1949 WHERE id=-91002$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject period missing end',$test$UPDATE public.historical_events SET year_to=NULL WHERE id=-91003$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject period extra date',$test$UPDATE public.historical_events SET event_date='1948-01-01' WHERE id=-91003$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject unknown extra year',$test$UPDATE public.historical_events SET year_from=1948 WHERE id=-91004$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject unknown extra date',$test$UPDATE public.historical_events SET event_date='1948-01-01' WHERE id=-91004$test$,'23514','ck_history_date_precision');
SELECT pg_temp.expect('reject site type',$test$UPDATE public.heritage_sites SET site_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject site latitude',$test$UPDATE public.heritage_sites SET latitude=-91 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject site longitude',$test$UPDATE public.heritage_sites SET longitude=-181 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject site status',$test$UPDATE public.heritage_sites SET verification_status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject site coordinate pair',$test$UPDATE public.heritage_sites SET longitude=NULL WHERE id=-91001$test$,'23514','ck_heritage_coordinates_pair');
SELECT pg_temp.expect('reject culture type',$test$UPDATE public.cultural_heritage SET heritage_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject culture status',$test$UPDATE public.cultural_heritage SET verification_status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject product type',$test$UPDATE public.agricultural_products SET product_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject agriculture importance',$test$UPDATE public.village_agriculture SET importance='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject economic type',$test$UPDATE public.economic_activities SET activity_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject facility type',$test$UPDATE public.community_facilities SET facility_type='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject facility latitude',$test$UPDATE public.community_facilities SET latitude=91 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject facility longitude',$test$UPDATE public.community_facilities SET longitude=181 WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject facility coordinate pair',$test$UPDATE public.community_facilities SET latitude=NULL WHERE id=-91001$test$,'23514','ck_facility_coordinates_pair');
SELECT pg_temp.expect('reject audit action',$test$UPDATE public.audit_logs SET action='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject audit empty label',$test$UPDATE public.audit_logs SET actor_label='' WHERE id=-91001$test$,'23514','ck_audit_actor');
SELECT pg_temp.expect('reject audit whitespace label',$test$UPDATE public.audit_logs SET actor_label=E' \t\n' WHERE id=-91001$test$,'23514','ck_audit_actor');
SELECT pg_temp.expect('reject verification decision',$test$UPDATE public.verification_records SET decision='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject conflict status',$test$UPDATE public.data_conflicts SET status='INVALID' WHERE id=-91001$test$,'23514');
SELECT pg_temp.expect('reject resolved missing resolver',$test$UPDATE public.data_conflicts SET resolved_by=NULL WHERE id=-91003$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject resolved missing timestamp',$test$UPDATE public.data_conflicts SET resolved_at=NULL WHERE id=-91003$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject resolved missing notes',$test$UPDATE public.data_conflicts SET resolution_notes=NULL WHERE id=-91003$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject resolved blank notes',$test$UPDATE public.data_conflicts SET resolution_notes=E' \t\n' WHERE id=-91003$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject open premature resolver',$test$UPDATE public.data_conflicts SET resolved_by=-91003 WHERE id=-91001$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject open premature timestamp',$test$UPDATE public.data_conflicts SET resolved_at=created_at WHERE id=-91001$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject open premature notes',$test$UPDATE public.data_conflicts SET resolution_notes='Draft' WHERE id=-91001$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject review premature resolver',$test$UPDATE public.data_conflicts SET resolved_by=-91003 WHERE id=-91002$test$,'23514','ck_conflict_resolution');
SELECT pg_temp.expect('reject conflict chronology',$test$UPDATE public.data_conflicts SET resolved_at=created_at-interval '1 second' WHERE id=-91003$test$,'23514','ck_conflict_resolution_chronology');
SELECT pg_temp.expect('reject cache equal expiry',$test$UPDATE public.geocoding_cache SET expires_at=cached_at WHERE id=-91001$test$,'23514','ck_cache_expiry');
SELECT pg_temp.expect('reject cache earlier expiry',$test$UPDATE public.geocoding_cache SET expires_at=cached_at-interval '1 second' WHERE id=-91001$test$,'23514','ck_cache_expiry');
SELECT pg_temp.expect('valid coordinate boundaries',$test$UPDATE public.villages SET latitude=-90,longitude=180 WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid null coordinate pair',$test$UPDATE public.heritage_sites SET latitude=NULL,longitude=NULL WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid external relation',$test$UPDATE public.village_relations SET related_village_id=NULL,related_place_name='External' WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid population lower year',$test$UPDATE public.population_records SET reference_year=1800,reference_date='1800-01-01' WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid population upper year',$test$UPDATE public.population_records SET reference_year=2200,reference_date='2200-01-01' WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid exact date',$test$UPDATE public.historical_events SET event_date='2000-01-01' WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid year lower boundary',$test$UPDATE public.historical_events SET year_from=1 WHERE id=-91002$test$,'00000');
SELECT pg_temp.expect('valid equal period years',$test$UPDATE public.historical_events SET year_from=2000,year_to=2000 WHERE id=-91003$test$,'00000');
SELECT pg_temp.expect('valid resolution at creation',$test$UPDATE public.data_conflicts SET resolved_at=created_at WHERE id=-91003$test$,'00000');
SELECT pg_temp.expect('valid resolve open',$test$UPDATE public.data_conflicts SET status='RESOLVED',resolved_by=-91003,resolved_at=created_at,resolution_notes='Reviewed' WHERE id=-91001$test$,'00000');
SELECT pg_temp.expect('valid reopen row state',$test$UPDATE public.data_conflicts SET status='OPEN',resolved_by=NULL,resolved_at=NULL,resolution_notes=NULL WHERE id=-91003$test$,'00000');
SELECT pg_temp.expect('reject null audit label',$test$UPDATE public.audit_logs SET actor_label=NULL WHERE id=-91001$test$,'23502');
SELECT pg_temp.expect('reject null population source',$test$UPDATE public.population_records SET source_id=NULL WHERE id=-91001$test$,'23502');
SELECT pg_temp.expect('reject null reviewer',$test$UPDATE public.verification_records SET reviewer_user_id=NULL WHERE id=-91001$test$,'23502');
SELECT pg_temp.expect('valid competing population source',$test$INSERT INTO public.population_records(id,village_id,reference_year,population_count,methodology,source_id) VALUES(-91999,-91001,2000,123,'CENSUS',-91002)$test$,'00000');
SELECT pg_temp.expect('reject duplicate governorate name',$test$INSERT INTO public.governorates(id,name_ar) VALUES(-91999,'__it_g1')$test$,'23505');
SELECT pg_temp.expect('reject duplicate governorate code',$test$INSERT INTO public.governorates(id,name_ar,code) VALUES(-91999,'__it_new','__IT_G1')$test$,'23505');
SELECT pg_temp.expect('reject duplicate authority scoped name',$test$INSERT INTO public.local_authorities(id,governorate_id,name_ar,authority_type) VALUES(-91999,-91001,'__it_authority','OTHER')$test$,'23505');
SELECT pg_temp.expect('reject duplicate village scoped name',$test$INSERT INTO public.villages(id,governorate_id,name_ar,location_description) VALUES(-91999,-91001,'__it_v1','Test')$test$,'23505');
SELECT pg_temp.expect('reject duplicate alias tuple',$test$INSERT INTO public.village_names(id,village_id,name,language_code,name_type) VALUES(-91999,-91001,'__it_alias','ar','ALTERNATIVE')$test$,'23505');
SELECT pg_temp.expect('reject duplicate username',$test$INSERT INTO public.users(id,username,email,password_hash,display_name) VALUES(-91999,'__it_actor','new@example.invalid','TEST_ONLY','Test')$test$,'23505');
SELECT pg_temp.expect('reject duplicate case-insensitive email',$test$INSERT INTO public.users(id,username,email,password_hash,display_name) VALUES(-91999,'__it_new','ACTOR@EXAMPLE.INVALID','TEST_ONLY','Test')$test$,'23505');
SELECT pg_temp.expect('reject duplicate role code',$test$INSERT INTO public.roles(id,code) VALUES(-91999,'__IT_ROLE')$test$,'23505');
SELECT pg_temp.expect('reject duplicate population observation',$test$INSERT INTO public.population_records(id,village_id,reference_year,population_count,methodology,source_id) VALUES(-91999,-91001,2000,100,'CENSUS',-91001)$test$,'23505');
SELECT pg_temp.expect('reject duplicate one village history',$test$INSERT INTO public.village_histories(id,village_id,overview) VALUES(-91999,-91001,'Duplicate')$test$,'23505');
SELECT pg_temp.expect('reject duplicate culture tuple',$test$INSERT INTO public.cultural_heritage(id,village_id,name,heritage_type,description) VALUES(-91999,-91001,'__it_culture','CRAFT','Duplicate')$test$,'23505');
SELECT pg_temp.expect('reject duplicate product name',$test$INSERT INTO public.agricultural_products(id,name_ar,product_type) VALUES(-91999,'__it_product','OTHER')$test$,'23505');
SELECT pg_temp.expect('reject duplicate village product',$test$INSERT INTO public.village_agriculture(id,village_id,product_id) VALUES(-91999,-91001,-91001)$test$,'23505');
SELECT pg_temp.expect('reject duplicate economy tuple',$test$INSERT INTO public.economic_activities(id,village_id,name,activity_type) VALUES(-91999,-91001,'__it_economy','TRADE')$test$,'23505');
SELECT pg_temp.expect('reject duplicate cache key',$test$INSERT INTO public.geocoding_cache(id,provider,query_hash,response_json,expires_at) VALUES(-91999,'__it_provider',repeat('a',64),'{}',now()+interval '1 hour')$test$,'23505');
SELECT pg_temp.expect('reject duplicate composite PK user_roles',$test$INSERT INTO public.user_roles(user_id,role_id) SELECT user_id,role_id FROM public.user_roles LIMIT 1$test$,'23505');
SELECT pg_temp.expect('reject duplicate composite PK village_sources',$test$INSERT INTO public.village_sources(village_id,source_id,claim_note) SELECT village_id,source_id,claim_note FROM public.village_sources LIMIT 1$test$,'23505');
SELECT pg_temp.expect('reject duplicate composite PK history_sources',$test$INSERT INTO public.history_sources(village_history_id,source_id) SELECT village_history_id,source_id FROM public.history_sources LIMIT 1$test$,'23505');
SELECT pg_temp.expect('reject duplicate composite PK event_sources',$test$INSERT INTO public.event_sources(event_id,source_id) SELECT event_id,source_id FROM public.event_sources LIMIT 1$test$,'23505');
SELECT pg_temp.expect('reject duplicate composite PK heritage_site_sources',$test$INSERT INTO public.heritage_site_sources(heritage_site_id,source_id) SELECT heritage_site_id,source_id FROM public.heritage_site_sources LIMIT 1$test$,'23505');
SELECT pg_temp.expect('reject duplicate composite PK cultural_heritage_sources',$test$INSERT INTO public.cultural_heritage_sources(cultural_heritage_id,source_id) SELECT cultural_heritage_id,source_id FROM public.cultural_heritage_sources LIMIT 1$test$,'23505');
SELECT pg_temp.expect('reject cross-governorate authority',$test$UPDATE public.villages SET local_authority_id=-91001 WHERE id=-91002$test$,'23503','fk_village_authority_same_governorate');
SELECT pg_temp.expect('reject deleting audit actor',$test$DELETE FROM public.users WHERE id=-91001$test$,'23503');
SELECT pg_temp.expect('reject deleting reviewer',$test$DELETE FROM public.users WHERE id=-91002$test$,'23503');
SELECT pg_temp.expect('reject deleting resolver',$test$DELETE FROM public.users WHERE id=-91003$test$,'23503');
SELECT pg_temp.expect('reject deleting cited source',$test$DELETE FROM public.sources WHERE id=-91001$test$,'23503');
SELECT pg_temp.expect('reject deleting referenced village',$test$DELETE FROM public.villages WHERE id=-91001$test$,'23503');
SELECT pg_temp.expect('valid disable preserves audit identity',$test$DO $case$ BEGIN
 UPDATE public.users SET enabled=false,display_name='Renamed' WHERE id=-91001;
 IF NOT EXISTS(SELECT 1 FROM public.audit_logs a JOIN public.users u ON u.id=a.actor_user_id
 WHERE a.id=-91001 AND NOT u.enabled AND a.actor_label='user:-91001 / Actor') THEN
 RAISE EXCEPTION 'Actor identity not retained'; END IF;
END; $case$$test$,'00000');
SELECT pg_temp.expect('valid user role cascade',$test$DO $case$ BEGIN
 DELETE FROM public.users WHERE id=-91004;
 IF EXISTS(SELECT 1 FROM public.user_roles WHERE user_id=-91004) THEN
 RAISE EXCEPTION 'Role cascade failed'; END IF;
END; $case$$test$,'00000');
SELECT pg_temp.expect('valid citation cascade history_sources',$test$DO $case$ BEGIN
 DELETE FROM public.village_histories WHERE id=-91001;
 IF EXISTS(SELECT 1 FROM public.history_sources WHERE village_history_id=-91001) THEN
 RAISE EXCEPTION 'Citation cascade failed'; END IF;
 END; $case$$test$,'00000');
SELECT pg_temp.expect('valid citation cascade event_sources',$test$DO $case$ BEGIN
 DELETE FROM public.historical_events WHERE id=-91001;
 IF EXISTS(SELECT 1 FROM public.event_sources WHERE event_id=-91001) THEN
 RAISE EXCEPTION 'Citation cascade failed'; END IF;
 END; $case$$test$,'00000');
SELECT pg_temp.expect('valid citation cascade heritage_site_sources',$test$DO $case$ BEGIN
 DELETE FROM public.heritage_sites WHERE id=-91001;
 IF EXISTS(SELECT 1 FROM public.heritage_site_sources WHERE heritage_site_id=-91001) THEN
 RAISE EXCEPTION 'Citation cascade failed'; END IF;
 END; $case$$test$,'00000');
SELECT pg_temp.expect('valid citation cascade cultural_heritage_sources',$test$DO $case$ BEGIN
 DELETE FROM public.cultural_heritage WHERE id=-91001;
 IF EXISTS(SELECT 1 FROM public.cultural_heritage_sources WHERE cultural_heritage_id=-91001) THEN
 RAISE EXCEPTION 'Citation cascade failed'; END IF;
 END; $case$$test$,'00000');
SELECT pg_temp.expect('valid village citation cascade',$test$DO $case$ BEGIN
 INSERT INTO public.villages(id,governorate_id,name_ar,location_description)
 VALUES(-91999,-91001,'__it_cascade','Test');
 INSERT INTO public.village_sources(village_id,source_id) VALUES(-91999,-91001);
 DELETE FROM public.villages WHERE id=-91999;
 IF EXISTS(SELECT 1 FROM public.village_sources WHERE village_id=-91999) THEN
 RAISE EXCEPTION 'Village citation cascade failed'; END IF;
END; $case$$test$,'00000');
SELECT pg_temp.expect('document service gap: verified history without citation',$test$DO $case$ BEGIN
 DELETE FROM public.history_sources WHERE village_history_id=-91001;
 UPDATE public.village_histories SET verification_status='VERIFIED' WHERE id=-91001;
END; $case$$test$,'00000');

-- One missing-parent attempt per actual FK, with explicit generated columns.
-- Overlapping FKs can reject before the named FK. The dedicated authority
-- test above separately proves the composite same-governorate rule.
DO $fk$
DECLARE k record;
BEGIN
 FOR k IN SELECT c.relname,a.attname,f.conname
 FROM pg_constraint f JOIN pg_class c ON c.oid=f.conrelid
 JOIN pg_namespace n ON n.oid=c.relnamespace
 JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=f.conkey[1]
 WHERE n.nspname='public' AND f.contype='f'
   AND c.relname<>'flyway_schema_history'
 ORDER BY c.relname,f.conname
 LOOP
  IF k.relname='data_conflicts' AND k.attname='resolved_by'
     AND k.conname='data_conflicts_resolved_by_fkey' THEN
   -- Use the resolved fixture so resolution CHECKs remain satisfied.
   PERFORM pg_temp.expect('FK missing parent: '||k.relname||'.'||k.conname,
    'UPDATE public.data_conflicts SET resolved_by=-919999 WHERE id=-91003',
    '23503','data_conflicts_resolved_by_fkey');
  ELSE
  PERFORM pg_temp.expect('FK missing parent: '||k.relname||'.'||k.conname,
   format('UPDATE public.%I SET %I=-919999 WHERE ctid=(SELECT ctid FROM public.%I LIMIT 1)',
          k.relname,k.attname,k.relname),'23503');
  END IF;
 END LOOP;
END; $fk$;

-- Duplicate a fixture in every table using explicit catalog-derived columns.
-- A PK or another unique key may reject first; do not assume index order.
DO $pk$
DECLARE t record; cols text;
BEGIN
 FOR t IN SELECT c.oid,c.relname FROM pg_class c
 JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind='r'
   AND c.relname<>'flyway_schema_history' ORDER BY c.relname
 LOOP
  SELECT string_agg(format('%I',a.attname),',' ORDER BY a.attnum) INTO cols
  FROM pg_attribute a WHERE a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped;
  PERFORM pg_temp.expect('duplicate row: '||t.relname,
    format('INSERT INTO public.%I(%s) SELECT %s FROM public.%I LIMIT 1',
           t.relname,cols,cols,t.relname),'23505');
 END LOOP;
END; $pk$;

RELEASE SAVEPOINT constraint_cases;

SELECT label,expected_state,actual_state,actual_constraint,passed,detail
FROM pg_temp.integrity_results ORDER BY passed,label;
SELECT count(*) AS total_tests,count(*) FILTER(WHERE passed) AS passed,
       count(*) FILTER(WHERE NOT passed) AS failed FROM pg_temp.integrity_results;
SELECT coalesce(bool_and(passed),false) AS all_passed FROM pg_temp.integrity_results
\gset

ROLLBACK;
\if :all_passed
 \echo PASS: all integrity cases passed; fixture and helper changes rolled back.
\else
 \echo FAIL: inspect failed cases above; fixture and helper changes rolled back.
DO $failure$ BEGIN RAISE EXCEPTION 'Integrity tests failed; fixture transaction already rolled back'; END; $failure$;
\endif
