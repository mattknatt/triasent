#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    SELECT 'CREATE DATABASE users_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'users_db')\gexec

    SELECT 'CREATE DATABASE messages_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'messages_db')\gexec
EOSQL
