(ns voter-registration-http-api.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [ring.util.response :as ring-resp]
            [turbovote.resource-config :refer [config]]
            [pedestal-toolbox.cors :as cors]
            [pedestal-toolbox.params :refer :all]
            [pedestal-toolbox.content-negotiation :refer :all]
            [bifrost.core :as bifrost]
            [bifrost.interceptors :as bifrost.i]
            [voter-registration-http-api.channels :as channels]))

(def ping
  (interceptor
   {:enter
    (fn [ctx]
      (assoc ctx :response (ring-resp/response "OK")))}))

(defn language-coercer
  [params]
  (if-let [language (:language params)]
    {:language (keyword language)}
    {}))

(defroutes routes
  [[["/"
     ^:interceptors [(body-params)
                     (negotiate-response-content-type ["application/edn"
                                                       "application/transit+json"
                                                       "application/transit+msgpack"
                                                       "application/json"
                                                       "text/plain"])]
     ["/ping" {:get [:ping ping]}]
     ["/registration-methods/:state"
      {:get [:get-registration-methods (bifrost/interceptor
                                        channels/registration-methods-read
                                        (config [:timeouts :registration-methods-read]))]}
      ^:interceptors [(bifrost.i/update-in-response [:body :registration-methods]
                                                    [:body] identity)
                      (bifrost.i/update-in-request [:query-params]
                                                   language-coercer)]]
     ["/registrations"
      ^:interceptors [(bifrost.i/update-in-request [:query-params]
                                                   language-coercer)]
      {:post [:post-registration (bifrost/interceptor
                                  channels/voter-register
                                  (config [:timeouts :voter-register]))]}]
     ["/status/:user-id"
      {:get [:get-registration-statuses (bifrost/interceptor
                                         channels/registration-status-read
                                         (config [:timeouts :status-read]))]}
      ^:interceptors [(bifrost.i/update-in-request [:path-params :user-id]
                                                   #(java.util.UUID/fromString %))
                      (bifrost.i/update-in-response [:body :registration-statuses]
                                                    [:body] identity)]
      ["/:source"
       {:get [:get-registration-status (bifrost/interceptor
                                        channels/registration-status-read
                                        (config [:timeouts :status-read]))]
        :put [:put-registration-status (bifrost/interceptor
                                        channels/registration-status-create
                                        (config [:timeouts :status-create]))]
        :delete [:delete-registration-status (bifrost/interceptor
                                              channels/registration-status-delete
                                              (config [:timeouts :status-delete]))]}
       ^:interceptors [(bifrost.i/update-in-request [:path-params :source]
                                                    keyword)
                       (bifrost.i/update-in-response [:body :registration-status]
                                                     [:body :registration-statuses]
                                                     identity)]]]]]])

(defn service []
  {::env :prod
   ::bootstrap/router :linear-search
   ::bootstrap/routes routes
   ::bootstrap/resource-path "/public"
   ::bootstrap/allowed-origins (cors/domain-matcher-fn
                                (map re-pattern
                                     (config [:server :allowed-origins])))
   ::bootstrap/host (config [:server :hostname])
   ::bootstrap/type :immutant
   ::bootstrap/port (config [:server :port])})
