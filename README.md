# CCPeople2

## Development

### Prerequisites
- Virtualbox & Vagrant

Choose your favorite Clojure IDE. We provide run configurations for [Cursive](https://cursiveclojure.com/userguide/), making it easiest to start with that.

### Getting started
This part has to be done only once.

- Clone this repo.
- Create account at [Datomic](https://my.datomic.com/account/create)
- Copy `username` and `password` from [Datomic account page](https://my.datomic.com/account) into a `.credentials` file separated by `:`, e.g. `bob@example.com:550e8400-e29b-11d4-a716-446655440000`
- Copy the `.credentials` file into `/app`, `/datomic-console` & `/transactor`.
- Enter the license key received after Datomic registration into `/transactor/config/dev-transactor.properties.sample` and rename it to `/transactor/config/dev-transactor.properties`.

### Working on the project
- Run `vagrant up` inside the top-level folder.
- Run `vagrant ssh` to connect to the VM.
- Run `docker-compose up`. This builds all the docker images for the postgres, datomic, the datomic console and the app itself, and then starts the entire stack. For development, it starts a REPL.
- When adding dependencies to `project.clj`, stop docker-compose and run `docker-compose build` to rebuild the repl image
- Connect to the vagrant REPL by using the IntelliJ run configuration or connect yourself on port 35001
- Inside the connected REPL you start in workspace `user`. Run `(go)` to start the server including a Figwheel server that builds Clojurescript and instantly reloads the client on code changes. 

  



