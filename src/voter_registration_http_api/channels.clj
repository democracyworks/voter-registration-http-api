(ns voter-registration-http-api.channels
  (:require [clojure.core.async :as async]))


(defonce ok-requests (async/chan))
(defonce ok-responses (async/chan))

(defonce registration-methods-read (async/chan))

(defonce voter-register (async/chan))

(defn close-all! []
  (doseq [c [ok-requests ok-responses registration-methods-read
             voter-register]]
    (async/close! c)))
