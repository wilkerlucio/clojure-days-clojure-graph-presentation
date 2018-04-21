(ns clojure-graphs-presentation-demo.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as routes]
            [clojure-graphs-presentation-demo.indexes :as indexes]
            [io.pedestal.http.body-params :as body-params]
            [clojure.pprint :refer [pprint]]
            [cognitect.transit :as transit]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.profile :as pp])
  (:import (java.io ByteArrayOutputStream)))

(def parser
  (p/parser {::p/plugins [(p/env-wrap-plugin
                            (fn [env]
                              (assoc env ::p/reader [p/map-reader
                                                     pc/all-readers
                                                     (p/placeholder-reader)]
                                         ::pc/resolver-dispatch indexes/resolve-fn
                                         ::pc/indexes @indexes/indexes)))
                          p/request-cache-plugin
                          p/error-handler-plugin
                          pp/profile-plugin]}))

(def body-params (body-params/body-params))

(defn write-transit [x]
  (let [baos (ByteArrayOutputStream.)
        w    (transit/writer baos :json)
        _    (transit/write w x)
        ret  (.toString baos)]
    (.reset baos)
    ret))

(defn req->output [request body]
  (let [content-type (get-in request [:headers "content-type"])
        out-body     (write-transit body)]
    {:status  200
     :headers {"Content-Type" content-type}
     :body    out-body}))

(defn graph-request [{:keys [edn-params transit-params] :as request}]
  (let [q        (or transit-params edn-params)
        response (parser indexes/env q)]
    (req->output request response)))

(def routes
  #{["/graph" :post [body-params `graph-request]]})

(def server
  (-> {::http/routes          #(routes/expand-routes (deref #'routes))
       ::http/allowed-origins {:creds true :allowed-origins (constantly true)}
       ::http/port            8890
       ::http/type            :jetty
       ::http/join?           false
       ::http/secure-headers  {:content-security-policy-settings {:object-src "none"}}}
      http/default-interceptors
      http/dev-interceptors
      http/create-server))

(comment
  (http/start server)

  (http/stop server))
