FROM java:8

ADD target/ccdashboard.jar /pipeline/source/app.jar

EXPOSE 8000 35001

CMD java -Dclojure.server.repl="{:port 35001 :address \"0.0.0.0\" :accept clojure.core.server/repl}" -jar /pipeline/source/app.jar
