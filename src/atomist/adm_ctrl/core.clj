(ns atomist.adm-ctrl.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.datafy :as datafy]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [atomist.k8s :as k8s]
            [clj-http.client :as client]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env merge-config! stacktrace]]
            [taoensso.encore    :as enc :refer [have have? qb]]
            [clojure.string :as str]))

(def url (System/getenv "ATOMIST_URL"))
(def api-key (System/getenv "ATOMIST_APIKEY"))

(defn output-fn
  ([data] ; For partials
   (let [{:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                 timestamp_ ?line]} data]
     (str
      (when-let [ts (force timestamp_)] (str ts " "))
      (str/upper-case (name level))  " "
      (force msg_)
      (when-let [err ?err]
        (str enc/system-newline (stacktrace err)))))))

(merge-config!
  {:output-fn output-fn})

(defn atomist-call [req]
  (let [response (client/post url {:headers {"Authorization" (format "Bearer %s" api-key)}
                                   :content-type :json
                                   :throw-exceptions false
                                   :body (json/generate-string req)})]
    (info (format "status %s, %s" (:status response) (-> response :body)))
    response))

(def k8s (atom nil))

(defn k8s-client []
  (when (nil? @k8s)
    (swap! k8s (fn [& args]
                 (info "... initializing k8s")
                 (if (System/getenv "LOCAL")
                   (k8s/build-kubectl-client)
                   (k8s/build-cluster-client)))))
  @k8s)

(defn log-images [object]
  (let [{{:keys [namespace name]} :metadata spec :spec kind :kind} object
        {{on-node :nodeName} :spec} (k8s/get-pod (k8s-client) namespace name)
        {{{:keys [operatingSystem architecture]} :nodeInfo} :status} (k8s/get-node (k8s-client) on-node)]
    ;; TODO support ephemeral containers
    (doseq [container (concat (:containers spec) (:initContainers spec))]
      (atomist-call {:image {:url (:image container)}
                     :environment {:name namespace}
                     :platform {:os operatingSystem
                                :architecture architecture}}))))

(defn log->tap [object]
  (async/go-loop
   [counter 0]
    (when (and
           (not
            (try
              (log-images object)
              true
              (catch Throwable t
                (let [{ {:keys [namespace name]} :metadata} object]
                  (warn (format "unable to log %s/%s" namespace name))
                  false)))) 
           (< counter 6))
      (async/<! (async/timeout 5000))
      (recur (inc counter)))))

(defn decision 
  [pod]
  ;; TODO
  ;; when not allowed at a :status key
  ;;   https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.23/#status-v1-meta
  {:allowed true})

(defn admission-review
  [uid response]
  {:apiVersion "admission.k8s.io/v1"
   :kind "AdmissionReview"
   :response (merge
              response
              {:uid uid})})

(def create-review (comp #(infof "review: %s" %1) admission-review))

(defn handle-admission-control-request
  ""
  [{:keys [body] :as req}]
  ;; request object could be nil if something is being deleted
  (let [{{:keys [kind] {:keys [namespace name]} :metadata :as object} :object 
         request-kind :kind
         request-resource :resource
         dry-run :dryRun 
         uid :uid 
         operation :operation} (-> body :request)]
    (infof "%-50s%-50s" 
           (format "%s/%s@%s" (:group request-kind) (:kind request-kind) (:version request-kind))
           (format "%s/%s@%s" (:group request-resource) (:resource request-resource) (:version request-resource)))
    (cond
      (#{"Pod" "Deployment" "Job" "DaemonSet" "ReplicaSet" "StatefulSet"} kind)
      (do
        (if dry-run
          (infof "%s dry run for uid %s - %s/%s" kind uid namespace name)
          (infof "%s admission request for uid %s - %s/%s" kind uid namespace name))
        (let [{:keys [allowed] :as resource-decision} (decision object)
              {{:keys [namespace name]} :metadata} object]
          (infof "reviewing %s:%s %s/%s" kind operation namespace name)
          (infof "decision: %s" resource-decision)
          (when (and (not dry-run) (= "Pod" kind)) 
            (log->tap object))
          (create-review uid resource-decision)))
      :else
      (do
        (infof "default decision for %s" kind)
        ;; non-Pod addmission requests are always true
        (create-review uid {:allowed true})))))

