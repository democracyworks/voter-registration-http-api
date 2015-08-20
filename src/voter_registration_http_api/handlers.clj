(ns voter-registration-http-api.handlers
  (:require [clojure.tools.logging :as log]))

(defn ok
  "A handler that does nothing and responds ok."
  [message]
  (log/info "Received:" message)
  {:status :ok})
