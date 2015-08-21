(ns voter-registration-http-api.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [ring.util.response :as ring-resp]
            [turbovote.resource-config :refer [config]]
            [pedestal-toolbox.params :refer :all]
            [pedestal-toolbox.content-negotiation :refer :all]
            [kehaar.core :as k]
            [clojure.core.async :refer [chan go alt! timeout]]
            [voter-registration-http-api.voter-registration-works :as vrw]))

(def ping
  (interceptor
   {:enter
    (fn [ctx]
      (assoc ctx :response (ring-resp/response "OK")))}))

(defn rabbit-error->http-status
  [rabbit-error]
  (case (:type rabbit-error)
    :semantic 400
    :validation 400
    :server 500
    :timeout 504
    500))

(defn rabbit-result->http-status
  [rabbit-result]
  (case (:status rabbit-result)
    :error (rabbit-error->http-status (:error rabbit-result))
    500))

(def response-timeout 5000)

(def registration-methods-read
  (interceptor
   {:enter
    (fn [ctx]
      (let [state (get-in ctx [:request :path-params :state])
            result-chan (vrw/registration-methods-read {:state state})]
        (go
          (let [result (alt! (timeout response-timeout) {:status :error
                                                         :error {:type :timeout}}
                             result-chan ([v] v))]
            (if (= :ok (:status result))
              (let [methods (:registration-methods result)]
                (assoc ctx :response
                       (ring-resp/response methods)))
              (let [http-status (rabbit-result->http-status result)]
                (assoc ctx :response
                       (-> result
                           ring-resp/response
                           (ring-resp/status http-status)))))))))}))

(def voter-register
  (interceptor
   {:enter
    (fn [ctx]
      (let [voter (get-in ctx [:request :body-params])
            result-chan (vrw/voter-register voter)]
        (go
          (let [result (alt! (timeout response-timeout) {:status :error
                                                         :error {:type :timeout}}
                             result-chan ([v] v))]
            (if (= :ok (:status result))
              (assoc ctx :response
                     (ring-resp/response (dissoc result :status)))
              (let [http-status (rabbit-result->http-status result)]
                (assoc ctx :response
                       (-> result
                           ring-resp/response
                           (ring-resp/status http-status)))))))))}))

(defroutes routes
  [[["/"
     ^:interceptors [(body-params)
                     (negotiate-response-content-type ["application/edn"
                                                       "application/transit+json"
                                                       "application/transit+msgpack"
                                                       "application/json"
                                                       "text/plain"])]
     ["/ping" {:get [:ping ping]}]
     ["/registration-methods/:state" {:get [:get-registration-methods registration-methods-read]}]
     ["/registrations" {:post [:post-registration voter-register]}]]]])

(defn service []
  {::env :prod
   ::bootstrap/router :linear-search
   ::bootstrap/routes routes
   ::bootstrap/resource-path "/public"
   ::bootstrap/allowed-origins (if (= :all (config [:server :allowed-origins]))
                                 (constantly true)
                                 (config [:server :allowed-origins]))
   ::bootstrap/host (config [:server :hostname])
   ::bootstrap/type :immutant
   ::bootstrap/port (config [:server :port])})
