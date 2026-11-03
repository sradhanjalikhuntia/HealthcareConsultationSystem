-- PostgreSQL Database Initialization for ApexCare Microservices
-- Executed automatically on container first startup by /docker-entrypoint-initdb.d/

CREATE DATABASE auth_db;
CREATE DATABASE patient_db;
CREATE DATABASE appointment_db;
CREATE DATABASE billing_db;

GRANT ALL PRIVILEGES ON DATABASE auth_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE patient_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE appointment_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE billing_db TO postgres;

