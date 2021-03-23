(ns reitit-swagger-playground.multipart
  (:require [reitit.coercion :as coercion]
            [reitit.spec]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.spec.alpha :as s]
            [reitit.impl :as impl]
            [spec-tools.core :as st])
  (:import (java.io File)))

(s/def ::filename string?)
(s/def ::content-type string?)
(s/def ::tempfile (partial instance? File))
(s/def ::bytes bytes?)
(s/def ::size int?)

(s/def ::multipart :reitit.core.coercion/model)
(s/def ::parameters (s/keys :opt-un [::multipart]))

(def temp-file-part
  "Spec for file param created by ring.middleware.multipart-params.temp-file store."
  (st/spec
   {:spec (s/keys :req-un [::filename ::content-type ::tempfile ::size])
    :swagger/type "file"}))

(def bytes-part
  "Spec for file param created by ring.middleware.multipart-params.byte-array store."
  (st/spec
   {:spec (s/keys :req-un [::filename ::content-type ::bytes])
    :swagger/type "file"}))

(defn coerce-request [coercers request]
  (let [result (reduce-kv
                (fn [acc k coercer]
                  (impl/fast-assoc acc k (coercer request)))
                {} coercers)]
    result))

(defn- coerced-request [request coercers]
  (if-let [coerced (if coercers (coercion/coerce-request coercers request))]
    (update request :parameters merge coerced)
    request))

;; add these private funcs
(defn- singleton->vector [x]
  (if (vector? x) x [x]))

(defn- apply-singleton->vector [base key]
  (if-let [val (get base key)]
    (assoc base key (singleton->vector val))
    base))

;;
;; public api
;;


(defn multipart-interceptor
  "Creates a Interceptor to handle the multipart params, based on
  ring.middleware.multipart-params, taking same options. Mounts only
  if endpoint has `[:parameters :multipart]` defined. Publishes coerced
  parameters into `[:parameters :multipart]` under request.

  options:
  - :force-vectorize-keys ... vector of vectorize key
    if you have the parameter gets multiple inputs like :files gets some image files,
    you can use this option like [:files]
  "
  ([]
   (multipart-interceptor nil))
  ([options]
   {:name ::multipart
    :spec ::parameters
    :compile (fn [{:keys [parameters coercion]} opts]
               (if-let [multipart (:multipart parameters)]
                 (let [parameter-coercion {:multipart (coercion/->ParameterCoercion
                                                       :multipart-params :string true true)}
                       opts (assoc opts ::coercion/parameter-coercion parameter-coercion)
                       coercers (if multipart (coercion/request-coercers coercion parameters opts))
                       force-vectorize-keys (map name (:force-vectorize-keys options))]
                   {:data {:swagger {:consumes ^:replace #{"multipart/form-data"}}}
                    :enter (fn [ctx]
                             (let [raw-request (:request ctx)
                                   parsed-request (multipart-params/multipart-params-request raw-request options)
                                   parsed-request (if-let [{:keys [multipart-params]} parsed-request]
                                                    (assoc parsed-request
                                                           :multipart-params
                                                           (loop [mp  multipart-params
                                                                  fvk force-vectorize-keys]
                                                             (if (zero? (count fvk))
                                                               mp
                                                               (recur
                                                                (apply-singleton->vector mp (first fvk))
                                                                (rest fvk)))))
                                                    parsed-request)
                                   request (coerced-request parsed-request coercers)]
                               (assoc ctx :request request)))})))}))
