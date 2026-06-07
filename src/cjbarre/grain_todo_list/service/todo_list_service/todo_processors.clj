(ns cjbarre.grain-todo-list.service.todo-list-service.todo-processors
  (:require [ai.obney.grain.todo-processor-v2.interface :as tp]))

(defn start
  [{:keys [event-store cache tenant-id]}]
  (tp/start-tenant-poller
   {:event-store event-store
    :tenant-ids #{tenant-id}
    :context {:cache cache}
    :poll-interval-ms 250}))

(defn stop
  [poller]
  (tp/stop-tenant-poller poller))
