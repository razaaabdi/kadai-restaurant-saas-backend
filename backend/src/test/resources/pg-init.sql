CREATE ROLE restaurant_app LOGIN PASSWORD 'app_secret' NOSUPERUSER NOBYPASSRLS;
GRANT ALL ON SCHEMA public TO restaurant_app;
GRANT CONNECT ON DATABASE test TO restaurant_app;
