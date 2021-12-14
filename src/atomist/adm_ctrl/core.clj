(ns atomist.adm-ctrl.core
  (:require [clojure.pprint :refer [pprint]]))

(def url (System/getenv "ATOMIST_URL"))
(def api-key (System/getenv "ATOMIST_APIKEY"))

(defn handle-admission-control-request
  ""
  [{:keys [body] :as req}]
  ;; request object could be nil if something is being deleted
  ;; TODO check for dry-run
  (pprint body)
  (when (= "Pod" (-> body :request :object :kind))
    (printf "Pod: %s\n" (-> body :request :object)))
  {:apiVersion "admission.k8s.io/v1"
   :kind "AdmissionReview"
   :response {:allowed true
              :uid (-> body :request :uid)}})
