(ns voter-registration-http-api.queue
  (:require [langohr.core :as rmq]
            [kehaar.configured :as kehaar]
            [kehaar.rabbitmq]
            [turbovote.resource-config :refer [config]]))

(defn initialize []
  (let [max-retries 5
        rabbit-config (config [:rabbitmq :connection])
        connection (kehaar.rabbitmq/connect-with-retries rabbit-config max-retries)
        kehaar-resources (kehaar/init! connection (config [:rabbitmq :kehaar]))]
    {:connections [connection]
     :kehaar-resources kehaar-resources}))

(defn close-resources! [resources]
  (doseq [resource resources]
    (when-not (rmq/closed? resource) (rmq/close resource))))

(defn close-all! [{:keys [connections kehaar-resources]}]
  (kehaar/shutdown! kehaar-resources)
  (close-resources! connections))
