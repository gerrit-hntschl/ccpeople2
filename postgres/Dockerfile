FROM postgres:9.4

ADD setupRemoteConnections.sh /docker-entrypoint-initdb.d/setupRemoteConnections.sh
RUN chmod 755 /docker-entrypoint-initdb.d/setupRemoteConnections.sh
ADD postgres-db.sql /tmp/postgres-db.sql
ADD postgres-table.sql /tmp/postgres-table.sql
ADD postgres-user.sql /tmp/postgres-user.sql
ADD setupDatomic.sh /docker-entrypoint-initdb.d/setupDatomic.sh
RUN chmod 755 /docker-entrypoint-initdb.d/setupDatomic.sh