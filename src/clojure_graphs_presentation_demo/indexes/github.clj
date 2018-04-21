(ns clojure-graphs-presentation-demo.indexes.github
  (:require [clj-http.client :as http]
            [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect.graphql :as pcg]
            [clojure-graphs-presentation-demo.secrets :as secrets]
            [com.wsscode.pathom.graphql :as pg]))

(declare github-resolver)

(def github-url (str "https://api.github.com/graphql?access_token=" secrets/github-token))
(defonce indexes (atom {}))

(defn gql-request [{::keys [url]} query]
  (-> (http/post url
        {:content-type :json
         :as           :json
         :form-params  {:query     query
                        :variables nil}})
      :body))

(defn make-resolver [{::pcg/keys [prefix]
                      ::keys     [url]}]
  (fn graphql-resolver [env ent]
    (let [q  (pcg/build-query (assoc env ::pcg/prefix prefix) ent)
          gq (pcg/query->graphql q)
          {:keys [data errors]} (gql-request (assoc env ::url url) gq)]
      (-> (pcg/parser-item {::p/entity          data
                            ::p/errors*         (::p/errors* env)
                            ::pcg/base-path     (vec (butlast (::p/path env)))
                            ::pcg/graphql-query gq
                            ::pcg/errors        (pcg/index-graphql-errors errors)}
            q)
          (pcg/pull-idents)))))

(defn load-index! []
  (let [schema (-> (gql-request {::url github-url} (pg/query->graphql pcg/schema-query))
                   :data)]
    (reset! indexes
      (pcg/index-schema {::pcg/resolver  `github-resolver
                         ::pcg/prefix    "github"
                         ::pcg/schema    schema
                         ::pcg/ident-map {"login" ["User" "login"]}}))))

(def gql-resolver
  (make-resolver {::pcg/prefix "github"
                  ::url        github-url}))

(comment
  (load-index!)
  (gql-request {::url github-url} (pg/query->graphql pcg/schema-query)))
