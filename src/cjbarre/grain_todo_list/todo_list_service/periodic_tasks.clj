(ns cjbarre.grain-todo-list.todo-list-service.periodic-tasks
  (:require [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.periodic-task.interface :as pt]))

(defn start
  [{:keys [event-store tenant-id]}]
  (pt/start-periodic-triggers!
   {:append-fn (partial es/append event-store)
    :tenant-ids-fn (constantly #{tenant-id})}))

(defn stop
  [triggers]
  (pt/stop-periodic-triggers! triggers))
