-- Runs only when the postgres data volume is initialised for the first time
CREATE
USER keycloak WITH PASSWORD 'keycloak';
CREATE
DATABASE keycloak OWNER keycloak;
