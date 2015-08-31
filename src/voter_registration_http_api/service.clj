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

(def response-timeout 5000)

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
  "Converts result messages from RabbitMQ into HTTP status codes.
  This currently only works with error responses, which isn't ideal, but making
  it work with :ok responses will require more work outside of this fn. TODO"
  [rabbit-result]
  (case (:status rabbit-result)
    :error (rabbit-error->http-status (:error rabbit-result))
    500))

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
                             result-chan ([v] v))
                result-without-status (dissoc result :status)]
            (if (= :ok (:status result))
              (assoc ctx :response
                     (ring-resp/response result-without-status))
              (let [http-status (rabbit-result->http-status result)]
                (assoc ctx :response
                       (-> result-without-status
                           ring-resp/response
                           (ring-resp/status http-status)))))))))}))

(def registration-statuses-read
  (interceptor
   {:enter
    (fn [ctx]
      (let [user-id (java.util.UUID/fromString
                     (get-in ctx [:request :path-params :user-id]))
            source (get-in ctx [:request :path-params :source])
            result-chan (vrw/registration-statuses-read
                         (merge {:user-id user-id}
                                (when source {:source (keyword source)})))]
        (go
          (let [result (alt! (timeout response-timeout) {:status :error
                                                         :error {:type :timeout}}
                             result-chan ([v] v))]
            (if (= :ok (:status result))
              (let [statuses (or (:registration-statuses result)
                                 (:registration-status result))]
                (assoc ctx :response
                       (ring-resp/response statuses)))
              (let [http-status (rabbit-result->http-status result)]
                (assoc ctx :response
                       (-> result
                           ring-resp/response
                           (ring-resp/status http-status)))))))))}))

(def registration-status-create
  (interceptor
   {:enter
    (fn [ctx]
      (let [user-id (java.util.UUID/fromString
                     (get-in ctx [:request :path-params :user-id]))
            source (keyword (get-in ctx [:request :path-params :source]))
            status (get-in ctx [:request :body-params])
            result-chan (vrw/registration-status-create
                         (merge status {:user-id user-id
                                        :source source}))]
        (go
          (let [result (alt! (timeout response-timeout) {:status :error
                                                         :error {:type :timeout}}
                             result-chan ([v] v))]
            (if (= :ok (:status result))
              (let [created-status (:registration-status result)]
                (assoc ctx :response
                       (-> created-status
                           ring-resp/response
                           (ring-resp/status 201))))
              (let [http-status (rabbit-result->http-status result)]
                (assoc ctx :response
                       (-> result
                           ring-resp/response
                           (ring-resp/status http-status)))))))))}))

(def registration-status-delete
  (interceptor
   {:enter
    (fn [ctx]
      (let [user-id (java.util.UUID/fromString
                     (get-in ctx [:request :path-params :user-id]))
            source (keyword (get-in ctx [:request :path-params :source]))
            result-chan (vrw/registration-status-delete {:user-id user-id
                                                         :source source})]
        (go
          (let [result (alt! (timeout response-timeout) {:status :error
                                                         :error {:type :timeout}}
                             result-chan ([v] v))]
            (if (= :ok (:status result))
              (let [deleted-status (:registration-status result)]
                (assoc ctx :response
                       (ring-resp/response deleted-status)))
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
     ["/registrations" {:post [:post-registration voter-register]}]
     ["/status/:user-id" {:get [:get-registration-statuses registration-statuses-read]}
      ["/:source" {:get [:get-registration-status registration-statuses-read]
                   :put [:put-registration-status registration-status-create]
                   :delete [:delete-registration-status registration-status-delete]}]]]]])

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
