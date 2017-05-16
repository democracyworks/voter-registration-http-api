(defproject voter-registration-http-api "0.1.0-SNAPSHOT"
  :description "FIXME: HTTP API gateway for ..."
  :url "https://github.com/democracyworks/voter-registration-http-api"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [turbovote.resource-config "0.2.0"]
                 [com.novemberain/langohr "3.7.0"]
                 [prismatic/schema "1.1.3"]
                 [ch.qos.logback/logback-classic "1.1.8"]

                 ;; core.async has to come before pedestal or kehaar.wire-up will
                 ;; not compile. Something to do with the try-catch in
                 ;; kehaar.core/go-handler.
                 [org.clojure/core.async "0.2.395"]
                 [democracyworks/kehaar "0.10.3"]

                 [io.pedestal/pedestal.service "0.4.1"]
                 [io.pedestal/pedestal.service-tools "0.4.1"]
                 [democracyworks/pedestal-toolbox "0.7.0"]

                 ;; this has to go before pedestal.immutant
                 ;; until this is fixed:
                 ;; https://github.com/pedestal/pedestal/issues/33
                 [org.immutant/web "2.1.5"]
                 [io.pedestal/pedestal.immutant "0.4.1"]
                 [org.immutant/core "2.1.5"]
                 [democracyworks/bifrost "0.1.5"]]
  :plugins [[lein-immutant "2.1.0"]
            [com.carouselapps/jar-copier "0.3.0"]]
  :java-agents [[com.newrelic.agent.java/newrelic-agent "3.35.1"]]
  :jar-copier {:java-agents true
               :destination "resources/jars"}
  :prep-tasks ["javac" "compile" "jar-copier"]
  :main ^:skip-aot voter-registration-http-api.server
  :uberjar-name "voter-registration-http-api.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["dev-resources"]}
             :test {:dependencies [[clj-http "3.4.1"]]
                    :resource-paths ["test-resources"]
                    :jvm-opts ["-Dlog-level=OFF"]}})
