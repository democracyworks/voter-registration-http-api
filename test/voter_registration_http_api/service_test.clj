(ns voter-registration-http-api.service-test
  (:require [voter-registration-http-api.server :as server]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [cognitect.transit :as transit]
            [clojure.core.async :as async]
            [clojure.test :refer :all]))

(def test-server-port 56000) ; FIXME: Pick a port unique to this project

(defn start-test-server [run-tests]
  (server/start-http-server {:io.pedestal.http/port test-server-port})
  (run-tests))

(use-fixtures :once start-test-server)

(def root-url (str "http://localhost:" test-server-port))

(deftest ping-test
  (testing "ping responds with 'OK'"
    (let [response (http/get (str root-url "/ping")
                             {:headers {:accept "text/plain"}})]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response))))))
