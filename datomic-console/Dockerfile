FROM pointslope/datomic-console:0.9.5130
# the hostname 'db' is created by linking a Datomic Pro Start container with the db alias
# docker-compose does this for us, but it can be done with docker run --link
CMD ["app", "datomic:sql://?jdbc:postgresql://postgres:5432/datomic?user=datomic&password=datomic"]