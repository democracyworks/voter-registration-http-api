FROM quay.io/democracyworks/didor:latest

RUN mkdir -p /usr/src/voter-registration-http-api
WORKDIR /usr/src/voter-registration-http-api

COPY project.clj /usr/src/voter-registration-http-api/

RUN lein deps

COPY . /usr/src/voter-registration-http-api

RUN lein test
RUN lein immutant war --name voter-registration-http-api --destination target --nrepl-port=11201 --nrepl-start --nrepl-host=0.0.0.0
