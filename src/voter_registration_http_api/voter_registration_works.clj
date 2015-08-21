(ns voter-registration-http-api.voter-registration-works
  (:require [kehaar.wire-up :as wire-up]
            [voter-registration-http-api.channels :as channels]))

(def registration-methods-read (wire-up/async->fn channels/registration-methods-read))
(def voter-register (wire-up/async->fn channels/voter-register))
