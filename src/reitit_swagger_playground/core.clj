(ns reitit-swagger-playground.core
  (:require [reitit.ring :as ring]

            [reitit.http :as http]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            ;; replace it
            [reitit-swagger-playground.multipart :as dev-multipart]
            [reitit.http.interceptors.multipart :as multipart]
            ;; Uncomment to use
            ; [reitit.http.interceptors.dev :as dev]
            ; [reitit.http.spec :as spec]
            ; [spec-tools.spell :as spell]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as m]
            [clojure.string]))

(def app
  (http/ring-handler
   (http/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "playground"
                              :description "with reitit-http"}}
             :handler (swagger/create-swagger-handler)}}]
     ["/names"
      {:post {:summary "list of names"
              :parameters {:multipart {:names [string?]}}
              :handler (fn [input-data]
                         {:status 200
                          :body {:greetings
                                 (mapv #(str "Hello, " %)
                                       (-> input-data :parameters :multipart :names first
                                           ;; string parser will returns "paramA,paramB,...,paramX"
                                           (clojure.string/split #",")))}})}}]
     ["/files"
      {:swagger {:tags ["files"]}}
      ["/uploads"
       {:post {:summary "upload a file"
               :parameters {:multipart {:files [multipart/temp-file-part]}}
               :handler (fn [input-data]
                          {:status 200
                           :body {:file-names (mapv :filename (-> input-data :parameters :multipart :files))}})}}]]]
    {;; :exception pretty/exception
     :data {:coercion reitit.coercion.spec/coercion
            ;;spec-coercion
            :muuntaja m/instance
            :interceptors [swagger/swagger-feature
                           ;;
                           (parameters/parameters-interceptor)
                           ;; content-negotiation
                           (muuntaja/format-negotiate-interceptor)
                           ;; encoding response body
                           (muuntaja/format-response-interceptor)
                           ;; exception handling
                           (exception/exception-interceptor)
                           ;; decoding request body
                           (muuntaja/format-request-interceptor)
                           ;; coercing response bodys
                           (coercion/coerce-response-interceptor)
                           ;; coercing request parameters
                           (coercion/coerce-request-interceptor)
                           ;; multipart
                           (dev-multipart/multipart-interceptor {:force-vectorize-keys [:files :names]})]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))
   {:executor sieppari/executor}))

(defn start []
  (jetty/run-jetty #'app {:port 3030, :join? false, :async true})
  ;(aleph/start-server (aleph/wrap-ring-async-handler #'app) {:port 3000})
  ;; (println "server running in port 3000")
  )

(defn stop [server]
  (.stop server))

;; (def server (start))
;; (stop server)

(defn -main [& args]
  (start))
