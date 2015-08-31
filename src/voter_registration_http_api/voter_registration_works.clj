(ns voter-registration-http-api.voter-registration-works
  (:require [kehaar.wire-up :as wire-up]
            [voter-registration-http-api.channels :as channels]))

(def registration-methods-read (wire-up/async->fn channels/registration-methods-read))

(def voter-register (wire-up/async->fn channels/voter-register))

(def registration-statuses-read (wire-up/async->fn channels/registration-statuses-read))
(def registration-status-create (wire-up/async->fn channels/registration-status-create))
(def registration-status-delete (wire-up/async->fn channels/registration-status-delete))
