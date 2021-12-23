(ns atomist.k8s
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [cheshire.core :as json]
            [kubernetes-api.core :as k8s]
            [clojure.string :as str])
  (:import [java.util Base64]))

(defmulti user->token (fn [user] (if (:exec user) :exec)))

(defmethod user->token :exec
  [{{:keys [command args env]} :exec}]
  (let [args (concat [command] args [:env (->> env
                                               (map (fn [{:keys [name value]}]
                                                      [name value]))
                                               (into {})
                                               ((fn [m] (assoc m "PATH" (System/getenv "PATH")))))])
        {:keys [out] :as p} (apply sh/sh args)]
    (str/trim out)))

(defmethod user->token :default 
  [user] 
  (throw (ex-info "no strategy for user" user)))

(defn k8-config 
  "relies on local kubectl install (kubectl must be in the PATH)"
  []
  (let [{:keys [out]} (sh/sh "kubectl" "config" "view" "--raw" "-o" "json")
        config (json/parse-string out keyword)
        context (->> (:contexts config)
                     (filter #(= (:current-context config) (:name %)))
                     first)]
    (merge
      (select-keys context [:name])
      {:cluster (->> (:clusters config) 
                     (filter #(= (-> context :context :cluster) (:name %)))
                     first
                     :cluster)
       :user (->> (:users config)
                  (filter #(= (-> context :context :user) (:name %)))
                  first
                  :user)})))

(defn client [{{:keys [server certificate-authority-data]} :cluster
               user :user}]
  (let [ca-file (io/file "/tmp/ca-docker.crt")
        c {:token (user->token user)
           :ca-cert (.getPath ca-file)
           :insecure? true}]
    #_(spit ca-file (String. (.decode (Base64/getDecoder) certificate-authority-data)))
    (k8s/client server c)))

(def build-kubectl-client (comp client k8-config))

(defn build-cluster-client []
  (k8s/client "https://kubernetes.default.svc" 
              {:token (slurp "/var/run/secrets/kubernetes.io/serviceaccount/token")
               :ca-cert "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
               :insecure? true }))

(defn get-pod [k8s ns n]
  (k8s/invoke k8s {:kind :Pod
                   :action :get
                   :request {:namespace ns
                             :name n}}))

(defn get-node [k8s n]
  (k8s/invoke k8s {:kind :Node
                   :action :get
                   :request {:name n}}))
