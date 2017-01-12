(ns voter-registration-http-api.channels
  (:require [clojure.core.async :as async]))


(defonce registration-methods-read (async/chan))

(defonce voter-register (async/chan))

(defonce registration-status-read (async/chan))
(defonce registration-status-create (async/chan))
(defonce registration-status-delete (async/chan))

(defn close-all! []
  (doseq [c [registration-methods-read
             voter-register
             registration-status-read
             registration-status-create
             registration-status-delete]]
    (async/close! c)))
