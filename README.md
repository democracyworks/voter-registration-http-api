# voter-registration-http-api

TODO: Add description

## Configuration

TODO: Add voter-registration-http-api specific configuration.

* ALLOWED_ORIGINS
    * This env var controls the cross-origin resource sharing (CORS) settings.
    * It should be set to one of the following:
        * `:all` to allow requests from any origin
        * an EDN seq of allowed origin strings
        * an EDN map containing the following keys and values
            * :allowed-origins - sequence of strings
            * :creds - true or false, indicates whether client is allowed to send credentials
            * :max-age - a long, indicates the number of seconds a client should cache the response from a preflight request
            * :methods - a string, indicates the accepted HTTP methods.  Defaults to "GET, POST, PUT, DELETE, HEAD, PATCH, OPTIONS"
    * For example: `ALLOWED_ORIGINS=["http://foo.example.com" "http://bar.example.com"]`

## Usage

Here's a sample CURL for creating a registration:
`curl -X POST "http://localdocker:58080/voter-registration-http-api/registrations" -H "Content-Type: application/edn" -H "Accept: application/edn" -d '{:state :ok :method :by-mail :voter {:user-id #uuid "18570c34-e221-42d4-a0bf-4349dfa1aaad" :email "rockiesgm@example.com" :first-name "Walt" :last-name "Weiss" :party "Green Party" :race "Alien" :phone "(303) 8675-3099" :citizenship "US" :date-of-birth #inst "1963-11-28" :register-address :physical :addresses {:physical {:street "8145 NE 139th St" :city "Edmond" :state "OK" :postal-code "73013"}}}}'`

TODO: Add more usage

## Running

### With docker-compose

Build it:

```
> docker-compose build
```

Run it:

```
> docker-compose up
```

TODO: If any more env vars are needed, add them to docker-compose command above.

### Running in CoreOS

There is a voter-registration-http-api@.service.template file provided in the repo. Look
it over and make any desired customizations before deploying. The
DOCKER_REPO, IMAGE_TAG, and CONTAINER values will all be set by the
build script.

The `script/build` and `script/deploy` scripts are designed to
automate building and deploying to CoreOS.

1. Run `script/build`.
1. Note the resulting image name and push it if needed.
1. Set your FLEETCTL_TUNNEL env var to a node of the CoreOS cluster
   you want to deploy to.
1. Make sure rabbitmq service is running.
1. Run `script/deploy`.

## License

Copyright © 2015 Democracy Works, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
