(ns atomist.k8s
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [cheshire.core :as json]
            [kubernetes-api.core :as k8s]
            [clj-http.client :as client]
            [taoensso.timbre :as timbre
             :refer [info  warn infof]]
            [clojure.string :as str])
  (:import [java.util Base64]))

(defn user-type 
  [user]
  (cond 
    (:exec user) :exec
    (:auth-provider user) :auth-provider
    :else :default))

(defmulti user->token user-type)

;; get a token by executing a command
(defmethod user->token :exec
  [{{:keys [command args env]} :exec}]
  (let [args (concat [command] args [:env (->> env
                                               (map (fn [{:keys [name value]}]
                                                      [name value]))
                                               (into {})
                                               ((fn [m] (assoc m "PATH" (System/getenv "PATH")))))])
        {:keys [out] :as p} (apply sh/sh args)]
    (str/trim out)))

(defmethod user->token :auth-provider
  [{{:keys [config name]} :auth-provider}]
  (:access-token config))

(defmethod user->token :default 
  [user] 
  (throw (ex-info "no strategy for user" user)))

(defn local-kubectl-config 
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

(defn local-k8s-client [{{:keys [server certificate-authority-data]} :cluster
                         user :user}]
  (let [ca-file (io/file "/tmp/ca-docker.crt")
        c {:token (user->token user)
           :ca-cert (.getPath ca-file)
           :insecure? true}]
    #_(spit ca-file (String. (.decode (Base64/getDecoder) certificate-authority-data)))
    (k8s/client server c)))

(defn local-http-client [{{:keys [server certificate-authority-data]} :cluster
                          user :user
                          :as config}]
  (let [ca-file (io/file "/tmp/ca-docker.crt")]
    #_ (spit ca-file (String. (.decode (Base64/getDecoder) certificate-authority-data)))
    (merge config {:token (user->token user)
                   :server server
                   :type :pure-http
                   :ca-cert (.getPath ca-file)
                   :insecure? true})))

;; for local testing using kubernetes-api.core
(def build-kubectl-client (comp local-k8s-client local-kubectl-config))
(def build-http-kubectl-client (comp local-http-client local-kubectl-config))

;; for testing in a cluster with kubernetes-api.core
(defn build-cluster-client []
  (k8s/client "https://kubernetes.default.svc" 
              {:token (slurp "/var/run/secrets/kubernetes.io/serviceaccount/token")
               :ca-cert "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
               :insecure? true}))

(defn build-http-cluster-client []
  {:type :pure-http
   :server "https://kubernetes.default.svc"
   :token (slurp "/var/run/secrets/kubernetes.io/serviceaccount/token")
   :ca-cert "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
   :insecure? true})

(defn http-get-pod [{:keys [server token]} ns n]
  (let [response (client/get (format "%s/api/v1/namespaces/%s/pods/%s" server ns n)
                             {:headers {"Authorization" (format "Bearer %s" token)}
                              :as :json
                              :insecure? true
                              :throws false})]
    (case (:status response)
      404 (throw (ex-info (format "Pod not found: %s/%s" ns n) (select-keys response [:status])))
      401 (throw (ex-info "Pod get unauthorized" (select-keys response [:status])))
      200 (:body response)
      (throw (ex-info "Pod get unexpected status" (:status response))))))

(defn http-get-node [{:keys [server token]} n]
  (let [response (client/get (format "%s/api/v1/nodes/%s" server n)
                             {:headers {"Authorization" (format "Bearer %s" token)}
                              :insecure? true
                              :as :json
                              :throws false})]
    (case (:status response)
      404 (throw (ex-info (format "Node not found: %s" n) (select-keys response [:status])))
      401 (throw (ex-info "Node get unauthorized" (select-keys response [:status]))) 
      200 (:body response)
      (throw (ex-info "Node get unexpected status" (:status response))))))

(defn get-pod [k8s ns n]
  (case (:type k8s)
    :pure-http (http-get-pod k8s ns n)
    (k8s/invoke k8s {:kind :Pod
                     :action :get
                     :request {:namespace ns
                               :name n}})))

(defn get-node [k8s n]
  (case (:type k8s)
    :pure-http (http-get-node k8s n)
    (k8s/invoke k8s {:kind :Node
                     :action :get
                     :request {:name n}})))

(comment
  (def c (build-http-kubectl-client))
  (pprint c)
  (get-pod c "api-production" "timburr-5fd9c499f6-9rxd5")
  (local-kubectl-config)
  )
