(ns cjbarre.grain-todo-list
  (:require [ai.obney.grain.code-agent-tools.interface :as code-agent-tools]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.command-request-handler-v2.interface :as crh]
            [ai.obney.grain.datastar.interface :as ds]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.query-processor.interface :as query-processor]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.webserver.interface :as ws]
            [cjbarre.grain-todo-list.todo-list-service.commands]
            [cjbarre.grain-todo-list.todo-list-service.periodic-tasks :as periodic-tasks]
            [cjbarre.grain-todo-list.todo-list-service.queries]
            [cjbarre.grain-todo-list.todo-list-service.read-models]
            [cjbarre.grain-todo-list.todo-list-service.schemas]
            [cjbarre.grain-todo-list.todo-list-service.todo-processors :as todo-processors]
            [clojure.set :as set]
            [com.brunobonacci.mulog :as u]
            [integrant.core :as ig]
            [io.pedestal.http :as http]))

(def tenant-id #uuid "a89f9f58-9761-42c9-bc67-94acba7bd4f2")

(def system
  {::logger {}

   ::event-store {:event-pubsub (ig/ref ::event-pubsub)
                  :conn {:type :in-memory}}

   ::event-pubsub {:type :core-async
                   :topic-fn :event/type}

   ::cache {}

   ::context {:event-store (ig/ref ::event-store)
              :cache (ig/ref ::cache)
              :tenant-id tenant-id
              :event-pubsub (ig/ref ::event-pubsub)}

   ::processors {:event-store (ig/ref ::event-store)
                 :cache (ig/ref ::cache)
                 :tenant-id tenant-id}

   ::periodic-triggers {:event-store (ig/ref ::event-store)
                        :tenant-id tenant-id}

   ::routes {:context (ig/ref ::context)}

   ::webserver {::http/routes (ig/ref ::routes)
                ::http/port 8080
                ::http/join? false
                ::http/resource-path "public"
                ::http/secure-headers {:content-security-policy-settings
                                       {:default-src "'self'"
                                        :script-src "'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net"
                                        :connect-src "'self' https://cdn.jsdelivr.net"
                                        :style-src "'self' 'unsafe-inline'"
                                        :font-src "'self' data:"
                                        :img-src "'self' data:"}}}})

(defn datastar-head
  []
  (list [:link {:rel "stylesheet" :href "/css/main.css"}]))

(defmethod ig/init-key ::logger [_ _]
  (let [console-pub-stop-fn
        (u/start-publisher! {:type :console
                             :pretty? true})]
    (fn []
      (console-pub-stop-fn))))

(defmethod ig/halt-key! ::logger [_ stop-fn]
  (stop-fn))

(defmethod ig/init-key ::event-store [_ config]
  (es/start config))

(defmethod ig/halt-key! ::event-store [_ event-store]
  (es/stop event-store))

(defmethod ig/init-key ::event-pubsub [_ config]
  (ps/start config))

(defmethod ig/halt-key! ::event-pubsub [_ event-pubsub]
  (ps/stop event-pubsub))

(defmethod ig/init-key ::cache [_ _]
  (kv/start
   (lmdb/->KV-Store-LMDB {:storage-dir (str "/tmp/grain-example-" (random-uuid))
                          :db-name "example"})))

(defmethod ig/halt-key! ::cache [_ cache]
  (kv/stop cache))

(defmethod ig/init-key ::context [_ context]
  (assoc context
         :command-registry (cp/global-command-registry)
         :query-registry (query-processor/global-query-registry)))

(defmethod ig/init-key ::processors [_ config]
  (todo-processors/start config))

(defmethod ig/halt-key! ::processors [_ poller]
  (todo-processors/stop poller))

(defmethod ig/init-key ::periodic-triggers [_ config]
  (periodic-tasks/start config))

(defmethod ig/halt-key! ::periodic-triggers [_ triggers]
  (periodic-tasks/stop triggers))

(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   (ds/routes context
              {}
              {:datastar/shim-opts {:head datastar-head
                                     :html-attrs {:data-theme "workshop"}}})
   #{["/actions" :post [(ds/action-handler context {})] :route-name ::actions]
     ["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]
     ["/favicon.ico" :get [(fn [_] {:status 204 :body ""})] :route-name ::favicon]}))

(defmethod ig/init-key ::webserver [_ config]
  (ws/start
   (http/default-interceptors config)))

(defmethod ig/halt-key! ::webserver [_ webserver]
  (ws/stop webserver))

(defn start
  []
  (let [app (ig/init system)]
    (u/set-global-context!
     {:app-name "example-app" :env "dev"})
    (code-agent-tools/install!
     {:system app
      :context (::context app)
      :mode :dev})
    app))

(defn stop
  [app]
  (ig/halt! app))

(comment
  (def app (start))
  (stop app)
  )
