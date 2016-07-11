# ccDashboard

## Development

### Prerequisites
- Virtualbox & Vagrant
- Ansible
- Leiningen

Choose your favorite Clojure IDE. We provide run configurations for [Cursive](https://cursiveclojure.com/userguide/), making it easiest to start with that.

### Getting started
This part has to be done only once.

- Clone this repo.
- Copy `provision/private_vars/env.yml.sample` to `provision/private_vars/env.yml` and fill out the placeholders:
    * `JIRA_TEMPO`, `JIRA_BASE_URL` and `JIRA_CONSUMER_PRIVATE_KEY` can be provided by your colleagues. Make sure that there is no trailing `/` on the `JIRA_BASE_URL`.
    * `DATOMIC_USER` and `DATOMIC_PASSWORD`: Create an account at [Datomic](https://my.datomic.com/account/create) and copy `username` and `password` from [Datomic account page](https://my.datomic.com/account)
    * `DATOMIC_POSTGRES_PASSWORD`, `APP_HOSTNAME`, `JWS_TOKEN_SECRET`: you can leave them as they are.
    * `DATOMIC_LICENSE_KEY`: the license key received after Datomic registration
    * `CCARTUSER`: Artifactory Username
    * `CCARTPASS`: Encrypted Artifactory Password, you can get it under your Artifactory Settings
    * `JIRA_ACCESS_TOKEN`: See next section.

#### Getting a Jira Access Token
To access Jira data, the dashboard-server uses OAuth. Jira access tokens are bound to a specific user and cannot be shared across multiple installations. On the plus side you need to get a token only once as it is valid for a long time, however, to get a token you need to perform these steps:

Requirement: your user needs to be in the `jira-developers` group to be able to perform all required queries. Check with your Jira admin.

- Start the VM: `vagrant up`. Log into the VM: `vagrant ssh`. Then start the server within the VM: `docker-compose up` or `u`, connect your IDE to the server: for Cursive use the `Vagrant REPL` run-configuration. If the run configurations cannot be found, check your GIT log in app/.idea/runConfigurations and restore the deleted xml files.
- In the REPL switch to the `ccdashboard.oauth.core` namespace: `(ns ccdashboard.oauth.core)`.
- Invoke the `request-token` function and store the result: `(def rt (request-token))` then call `rt`.
- The function returns a map containing an `:authorize-url` key. Copy that URL and open it in the browser. You will be prompted to log-in. After logging-in the browser displays a verifier string. Copy that string.
- Invoke the `access-token` function using the request-token and verifier string: `(access-token rt "<paste copied verifier string here>")`.
- The function returns the Jira access token which you should paste into `provision/private_vars/env.yml`.
- Important: run `vagrant provision` afterwards to make sure the VM picks up the access token.

### Working on the project
Note: Make sure that you completed the `env.yml` setup before. If you missed something and noticed it after running `vagrant up`, run `vagrant provision` so that the environment variables inside the Vagrant VM are updated.

- Run `vagrant up` inside the top-level folder.
- Run `vagrant ssh` to connect to the VM.
- Run `docker-compose up` (aliased to `u`). This builds all the docker images for postgres, datomic, the datomic console and the app itself, and then starts the entire stack. For development, it starts a REPL.
- When adding dependencies to `project.clj`, stop docker-compose and run `docker-compose build` (aliased to `b`) to rebuild the repl image.
- Connect to the vagrant REPL by using the IntelliJ run configuration or connect yourself on port 35001
- Inside the connected REPL you start in workspace `user`. Run `(go)` to start the server.
- The ClojureScript-client is compiled incrementally by Figwheel and instantly reloaded on code changes. To start it use the `figwheel` run-configuration which uses the approach described [here](https://github.com/bhauman/lein-figwheel/wiki/Running-figwheel-in-a-Cursive-Clojure-REPL).
- The app is hosted at [https://localhost:9090](https://localhost:9090). (https only)

  



