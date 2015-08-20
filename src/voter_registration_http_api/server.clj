(ns voter-registration-http-api.server
    (:require [voter-registration-http-api.service :as service]
              [io.pedestal.http :as http]
              [voter-registration-http-api.channels :as channels]
              [voter-registration-http-api.queue :as queue]
              [turbovote.resource-config :refer [config]]
              [clojure.tools.logging :as log]
              [immutant.util :as immutant]))

(defn shutdown [rabbit-resources]
  (channels/close-all!)
  (queue/close-all! rabbit-resources))

(defn start-http-server [& [options]]
  (-> (service/service)
      (merge options)
      http/create-server
      http/start))

(defn -main [& args]
  (let [rabbit-resources (queue/initialize)]
    (start-http-server (config [:server]))
    (immutant/at-exit (partial shutdown rabbit-resources))))
