(ns voter-registration-http-api.service-test
  (:require [voter-registration-http-api.service :refer :all]
            [voter-registration-http-api.server :as server]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [cognitect.transit :as transit]
            [clojure.core.async :as async]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [voter-registration-http-api.channels :as channels]
            [voter-registration-http-api.service :as service]
            [bifrost.core :as bifrost])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def test-server-port 56789)

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

(deftest registration-methods-read-test
  (testing "GET to /registration-methods/:state puts appropriate read message
            on registration-methods-read channel"
    (let [http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "registration-methods"
                                                      "co?language=all"])
                                       {:headers {:accept "application/edn"}}))
          [response-ch message] (async/alt!! channels/registration-methods-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :registration-methods {:paper {:acceptable-forms [:nvrf]}
                                           :online {:supports-iframe false
                                                    :url "https://fake.ovr.site"}}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            body-data (-> http-response :body edn/read-string)]
        (assert (not= http-response ::timeout))
        (is (= "co" (:state message)))
        (is (= 200 (:status http-response)))
        (is (= (:registration-methods response) body-data))
        (is (= :all (:language message))))))
  (testing "GET to /registration-methods/:state can respond with Transit"
    (let [http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "registration-methods"
                                                      "ny"])
                                       {:headers {:accept "application/transit+json"}}))
          [response-ch message] (async/alt!! channels/registration-methods-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :registration-methods {:paper {:acceptable-forms [:nvrf]}
                                           :online {:supports-iframe false
                                                    :url "https://fake.nyovr.site"}}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            transit-in (ByteArrayInputStream. (-> http-response
                                                  :body
                                                  (.getBytes "UTF-8")))
            transit-reader (transit/reader transit-in :json)
            body-data (transit/read transit-reader)]
        (assert (not= http-response ::timeout))
        (is (= "ny" (:state message)))
        (is (= 200 (:status http-response)))
        (is (= (:registration-methods response) body-data)))))
  (testing "error from backend service results in HTTP server error response"
    (let [http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "registration-methods"
                                                      "ny"])
                                       {:headers {:accept "application/edn"}
                                        :throw-exceptions false}))
          [response-ch message] (async/alt!! channels/registration-methods-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (async/>!! response-ch {:status :error
                              :error {:type :server}})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 500 (:status http-response))))))
  (testing "no response from backend service results in HTTP gateway timeout error response"
    (let [http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "registration-methods"
                                                      "ok"])
                                       {:headers {:accept "application/edn"}
                                        :throw-exceptions false}))
          [response-ch message] (async/alt!! channels/registration-methods-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1500) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 504 (:status http-response)))))))

(deftest voter-register-test
  (testing "POST to /registrations puts appropriate voter.register message
            on voter-register channel"
    (let [post-data {:state :ok
                     :method :paper
                     :voter {:email "rockiesgm@example.com"
                             :first-name "Walt"
                             :last-name "Weiss"
                             :citizenship "US"
                             :date-of-birth #inst "1963-11-28"
                             :register-address :physical
                             :addresses {:physical {:street "8145 NE 139th St"
                                                    :city "Edmond"
                                                    :state "OK"
                                                    :postal-code "73013"}}}}
          http-response-ch (async/thread
                             (http/post (str/join "/" [root-url
                                                       "/registrations?language=all"])
                                        {:headers {:accept "application/edn"
                                                   :content-type "application/edn"}
                                         :body (pr-str post-data)}))
          [response-ch message] (async/alt!! channels/voter-register ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :next {:paper {:acceptable-forms [:nvrf]}}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            body-data (-> http-response :body edn/read-string)]
        (assert (not= http-response ::timeout))
        (is (= :ok (:state message)))
        (is (= 200 (:status http-response)))
        (is (= (dissoc response :status) body-data))
        (is (= :all (:language message))))))
  (testing "POST to /registrations can receive & respond with Transit"
    (let [post-data {:state :ny
                     :method :paper
                     :voter {:email "rockiesgm@example.com"
                             :first-name "Walt"
                             :last-name "Weiss"
                             :citizenship "US"
                             :date-of-birth #inst "1963-11-28"
                             :register-address :physical
                             :addresses {:physical {:street "8145 NE 139th St"
                                                    :city "Edmond"
                                                    :state "OK"
                                                    :postal-code "73013"}}}}
          transit-out (ByteArrayOutputStream.)
          transit-writer (transit/writer transit-out :json)
          post-body (do (transit/write transit-writer post-data)
                        (.toString transit-out "UTF-8"))
          http-response-ch (async/thread
                             (http/post (str/join "/" [root-url
                                                       "registrations"])
                                        {:headers {:accept "application/transit+json"
                                                   :content-type "application/transit+json"}
                                         :body post-body}))
          [response-ch message] (async/alt!! channels/voter-register ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :next {:paper {:acceptable-forms [:nvrf]}}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            transit-in (ByteArrayInputStream. (-> http-response
                                                  :body
                                                  (.getBytes "UTF-8")))
            transit-reader (transit/reader transit-in :json)
            body-data (transit/read transit-reader)]
        (assert (not= http-response ::timeout))
        (is (= :ny (:state message)))
        (is (= 200 (:status http-response)))
        (is (= (dissoc response :status) body-data))
        (is (not (contains? message :language))))))
  (testing "error from backend service results in HTTP server error response"
    (let [post-data {:state :co
                     :method :online
                     :voter {:email "rockiesgm@example.com"
                             :first-name "Walt"
                             :last-name "Weiss"
                             :citizenship "US"
                             :date-of-birth #inst "1963-11-28"
                             :register-address :physical
                             :addresses {:physical {:street "8145 NE 139th St"
                                                    :city "Edmond"
                                                    :state "OK"
                                                    :postal-code "73013"}}}}
          http-response-ch (async/thread
                             (http/post (str/join "/" [root-url
                                                       "registrations"])
                                        {:headers {:accept "application/edn"
                                                   :content-type "application/edn"}
                                         :body (pr-str post-data)
                                         :throw-exceptions false}))
          [response-ch message] (async/alt!! channels/voter-register ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (async/>!! response-ch {:status :error
                              :error {:type :server}})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 500 (:status http-response))))))
  (testing "no response from backend service results in HTTP gateway timeout error response"
    (let [post-data {:state :co
                     :method :online
                     :voter {:email "rockiesgm@example.com"
                             :first-name "Walt"
                             :last-name "Weiss"
                             :citizenship "US"
                             :date-of-birth #inst "1963-11-28"
                             :register-address :physical
                             :addresses {:physical {:street "8145 NE 139th St"
                                                    :city "Edmond"
                                                    :state "OK"
                                                    :postal-code "73013"}}}}
          http-response-ch (async/thread
                             (http/post (str/join "/" [root-url
                                                       "registrations"])
                                        {:headers {:accept "application/edn"
                                                   :content-type "application/edn"}
                                         :body (pr-str post-data)
                                         :throw-exceptions false}))
          [response-ch message] (async/alt!! channels/voter-register ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1500) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 504 (:status http-response)))))))

(deftest registration-status-read-test
  (testing "GET /status/:user-id puts appropriate message on registration-status-read channel"
    (let [user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "status"
                                                      user-id])
                                       {:headers {:accept "application/edn"}}))
          [response-ch message] (async/alt!! channels/registration-status-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :registration-statuses [{:user-id user-id
                                             :status :foo
                                             :source :bar}
                                            {:user-id user-id
                                             :status :baz
                                             :source :qux}]}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            body-data (-> http-response :body edn/read-string)]
        (assert (not= http-response ::timeout))
        (is (= user-id (:user-id message)))
        (is (= 200 (:status http-response)))
        (is (= body-data (:registration-statuses response))))))
  (testing "GET /status/:user-id/:source puts appropriate message on registration-status-read channel"
    (let [user-id (java.util.UUID/randomUUID)
          source "user"
          http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "status"
                                                      user-id
                                                      source])
                                       {:headers {:accept "application/edn"}}))
          [response-ch message] (async/alt!! channels/registration-status-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :registration-status {:user-id user-id
                                          :status :foo
                                          :source (keyword source)}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            body-data (-> http-response :body edn/read-string)]
        (assert (not= http-response ::timeout))
        (is (= user-id (:user-id message)))
        (is (= (keyword source) (:source message)))
        (is (= 200 (:status http-response)))
        (is (= body-data (:registration-status response)))))))

(deftest registration-status-create-test
  (testing "PUT /status/:user-id/:source w/ valid body puts appropriate message on registration-status-create channel"
    (let [user-id (java.util.UUID/randomUUID)
          source "turbovote"
          status :pending
          put-data {:status status}
          http-response-ch (async/thread
                             (http/put (str/join "/" [root-url
                                                      "status"
                                                      user-id
                                                      source])
                                       {:headers {:content-type "application/edn"
                                                  :accept "application/edn"}
                                        :body (pr-str put-data)}))
          [response-ch message] (async/alt!! channels/registration-status-create ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :created
                    :registration-status {:user-id user-id
                                          :source (keyword source)
                                          :status status}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            body-data (-> http-response :body edn/read-string)]
        (assert (not= http-response ::timeout))
        (is (= {:user-id user-id
                :source (keyword source)
                :status status}
               message))
        (is (= 201 (:status http-response)))
        (is (= body-data (:registration-status response)))))))

(deftest registration-status-delete-test
  (testing "DELETE /status/:user-id/:source puts appropriate message on registration-status-delete channel"
    (let [user-id (java.util.UUID/randomUUID)
          source "turbovote"
          http-response-ch (async/thread
                             (http/delete (str/join "/" [root-url
                                                         "status"
                                                         user-id
                                                         source])
                                          {:headers {:accept "application/edn"}}))
          [response-ch message] (async/alt!! channels/registration-status-delete ([v] v)
                                             (async/timeout 1000) [nil ::timeout])
          response {:status :ok
                    :registration-status {:user-id user-id
                                          :source (keyword source)
                                          :status :foo}}]
      (assert (not= message ::timeout))
      (async/>!! response-ch response)
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            body-data (-> http-response :body edn/read-string)]
        (assert (not= http-response ::timeout))
        (is (= {:user-id user-id
                :source (keyword source)}
               message))
        (is (= 200 (:status http-response)))
        (is (= body-data (:registration-status response)))))))
