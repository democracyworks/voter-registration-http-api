(ns voter-registration-http-api.queue
  (:require [clojure.tools.logging :as log]
            [langohr.core :as rmq]
            [kehaar.core :as k]
            [kehaar.wire-up :as wire-up]
            [kehaar.rabbitmq]
            [voter-registration-http-api.channels :as channels]
            [voter-registration-http-api.handlers :as handlers]
            [turbovote.resource-config :refer [config]]
            [voter-registration-http-api.service :refer [response-timeout]]))

(defn initialize []
  (let [max-retries 5
        rabbit-config (config [:rabbitmq :connection])
        connection (kehaar.rabbitmq/connect-with-retries rabbit-config max-retries)]
    (let [incoming-events []
          incoming-services [(wire-up/incoming-service
                              connection
                              "voter-registration-http-api.ok"
                              (config [:rabbitmq :queues "voter-registration-http-api.ok"])
                              channels/ok-requests
                              channels/ok-responses)]
          external-services [(wire-up/external-service
                              connection
                              ""
                              "voter-registration-works.registration-methods.read"
                              (config [:rabbitmq :queues "voter-registration-works.registration-methods.read"])
                              response-timeout
                              channels/registration-methods-read)
                             (wire-up/external-service
                              connection
                              ""
                              "voter-registration-works.voter.register"
                              (config [:rabbitmq :queues "voter-registration-works.voter.register"])
                              response-timeout
                              channels/voter-register)]
          outgoing-events []]

      (wire-up/start-responder! channels/ok-requests
                                channels/ok-responses
                                handlers/ok)
      
      {:connections [connection]
       :channels (vec (concat
                       incoming-events
                       incoming-services
                       external-services
                       outgoing-events))})))

(defn close-resources! [resources]
  (doseq [resource resources]
    (when-not (rmq/closed? resource) (rmq/close resource))))

(defn close-all! [{:keys [connections channels]}]
  (close-resources! channels)
  (close-resources! connections))
