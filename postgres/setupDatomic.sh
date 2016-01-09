#!/bin/bash

psql -f /tmp/postgres-db.sql -U postgres
psql -f /tmp/postgres-table.sql -U postgres -d datomic
psql --set=datomicpass="$DATOMIC_POSTGRES_PASSWORD" -f /tmp/postgres-user.sql -U postgres -d datomic
