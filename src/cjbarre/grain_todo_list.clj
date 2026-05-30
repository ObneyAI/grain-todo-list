(ns cjbarre.grain-todo-list
  (:require [integrant.core :as ig]
            [com.brunobonacci.mulog :as u]
            [clojure.set :as set]
            [clojure.string :as string]
            [io.pedestal.http :as http]
            ;; Grain 
            [ai.obney.grain.code-agent-tools.interface :as code-agent-tools]
            [ai.obney.grain.command-processor-v2.interface :as cp :refer [defcommand]]
            [ai.obney.grain.query-processor.interface :as query-processor :refer [defquery]]
            [ai.obney.grain.datastar.interface :as ds]
            [ai.obney.grain.event-store-v3.interface :as es :refer [->event]]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.periodic-task.interface :as pt]
            [ai.obney.grain.command-request-handler-v2.interface :as crh]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.webserver.interface :as ws]
            [cjbarre.grain-todo-list.ui :as ui]
            [cognitect.anomalies :as anom])
  (:import [java.time OffsetDateTime]))

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

;; ---------------------- ;;
;; Datastar Setup         ;;
;; ---------------------- ;;

(defn datastar-head
  []
  (list [:link {:rel "stylesheet" :href "/css/main.css"}]))

;; -------------- ;;
;; Integrant Keys ;;
;; -------------- ;;

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

(defmethod ig/init-key ::processors [_ {:keys [event-store cache tenant-id]}]
  (tp/start-tenant-poller
   {:event-store event-store
    :tenant-ids #{tenant-id}
    :context {:cache cache}
    :poll-interval-ms 250}))

(defmethod ig/halt-key! ::processors [_ poller]
  (tp/stop-tenant-poller poller))

(defmethod ig/init-key ::periodic-triggers [_ {:keys [event-store tenant-id]}]
  (pt/start-periodic-triggers!
   {:append-fn (partial es/append event-store)
    :tenant-ids-fn (constantly #{tenant-id})}))

(defmethod ig/halt-key! ::periodic-triggers [_ triggers]
  (pt/stop-periodic-triggers! triggers))

(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   (ds/routes context
              {}
              {:datastar/shim-opts {:head datastar-head
                                     :html-attrs {:data-theme "workshop"}}})
   #{["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]
     ["/favicon.ico" :get [(fn [_] {:status 204 :body ""})] :route-name ::favicon]
     ["/actions" :post [(ds/action-handler context {})] :route-name ::datastar-actions]}))

(defmethod ig/init-key ::webserver [_ config]
  (ws/start
   (http/default-interceptors config)))

(defmethod ig/halt-key! ::webserver [_ webserver]
  (ws/stop webserver))

;; ------------------- ;;
;; Lifecycle functions ;;
;; ------------------- ;;

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

;; ------- ;;
;; Schemas ;;
;; ------- ;;

(def buckets #{:inbox :next :waiting :someday})
(def task-statuses #{:active :completed :canceled :archived})
(def project-statuses #{:active :completed :canceled})

(defn non-blank-string?
  [x]
  (and (string? x) (seq (string/trim x))))

(defn offset-date-time-string?
  [x]
  (and (string? x)
       (try
         (OffsetDateTime/parse x)
         true
         (catch Exception _
           false))))

(defn offset-date-time-input?
  [x]
  (or (instance? OffsetDateTime x)
      (offset-date-time-string? x)))

(defn coerce-offset-date-time
  [x]
  (cond
    (instance? OffsetDateTime x) x
    (string? x) (OffsetDateTime/parse x)
    :else x))

(defschemas todo-schemas
  {::non-blank-string [:fn {:error/message "Must not be blank"} non-blank-string?]
   ::offset-date-time-input [:fn {:error/message "Must be an offset date time"} offset-date-time-input?]
   ::bucket [:enum :inbox :next :waiting :someday]
   ::task-status [:enum :active :completed :canceled :archived]
   ::project-status [:enum :active :completed :canceled]
   ::order number?

   :todo/task-captured
   [:map [:task-id :uuid]
    [:title ::non-blank-string]
    [:bucket ::bucket]
    [:status ::task-status]
    [:order ::order]
    [:project-id {:optional true} :uuid]
    [:due-at {:optional true} :time/offset-date-time]
    [:defer-until {:optional true} :time/offset-date-time]]
   :todo/task-renamed [:map [:task-id :uuid] [:title ::non-blank-string]]
   :todo/task-moved-to-bucket [:map [:task-id :uuid] [:bucket ::bucket] [:order ::order]]
   :todo/task-assigned-to-project [:map [:task-id :uuid] [:project-id :uuid]]
   :todo/task-removed-from-project [:map [:task-id :uuid] [:project-id :uuid]]
   :todo/task-due-at-set [:map [:task-id :uuid] [:due-at :time/offset-date-time]]
   :todo/task-due-at-cleared [:map [:task-id :uuid]]
   :todo/task-deferred [:map [:task-id :uuid] [:defer-until :time/offset-date-time]]
   :todo/task-defer-date-cleared [:map [:task-id :uuid]]
   :todo/task-completed [:map [:task-id :uuid]]
   :todo/task-archived [:map [:task-id :uuid]]
   :todo/task-canceled [:map [:task-id :uuid]]
   :todo/task-reactivated [:map [:task-id :uuid]]
   :todo/task-reordered [:map [:task-id :uuid] [:order ::order]]

   :todo/project-created [:map [:project-id :uuid] [:name ::non-blank-string] [:status ::project-status]]
   :todo/project-renamed [:map [:project-id :uuid] [:name ::non-blank-string]]
   :todo/project-completed [:map [:project-id :uuid]]
   :todo/project-canceled [:map [:project-id :uuid]]
   :todo/project-reactivated [:map [:project-id :uuid]]

   :todo/weekly-review-started [:map [:review-id :uuid] [:started-at :time/offset-date-time]]
   :todo/project-reviewed [:map [:review-id :uuid] [:project-id :uuid]]
   :todo/bucket-reviewed [:map [:review-id :uuid] [:bucket ::bucket]]
   :todo/weekly-review-completed [:map [:review-id :uuid] [:completed-at :time/offset-date-time]]

   :todo/capture-task
   [:map [:title ::non-blank-string]
    [:task-id {:optional true} :uuid]
    [:bucket {:optional true} ::bucket]
    [:project-id {:optional true} :uuid]
    [:due-at {:optional true} ::offset-date-time-input]
    [:defer-until {:optional true} ::offset-date-time-input]
    [:order {:optional true} ::order]]
   :todo/rename-task [:map [:task-id :uuid] [:title ::non-blank-string]]
   :todo/move-task-to-bucket [:map [:task-id :uuid] [:bucket ::bucket] [:order {:optional true} ::order]]
   :todo/assign-task-to-project [:map [:task-id :uuid] [:project-id :uuid]]
   :todo/remove-task-from-project [:map [:task-id :uuid]]
   :todo/set-task-due-at [:map [:task-id :uuid] [:due-at ::offset-date-time-input]]
   :todo/clear-task-due-at [:map [:task-id :uuid]]
   :todo/defer-task [:map [:task-id :uuid] [:defer-until ::offset-date-time-input]]
   :todo/clear-task-defer-date [:map [:task-id :uuid]]
   :todo/complete-task [:map [:task-id :uuid]]
   :todo/archive-task [:map [:task-id :uuid]]
   :todo/cancel-task [:map [:task-id :uuid]]
   :todo/reactivate-task [:map [:task-id :uuid]]
   :todo/reorder-task [:map [:task-id :uuid] [:order ::order]]

   :todo/create-project [:map [:project-id {:optional true} :uuid] [:name ::non-blank-string]]
   :todo/rename-project [:map [:project-id :uuid] [:name ::non-blank-string]]
   :todo/complete-project [:map [:project-id :uuid]]
   :todo/cancel-project [:map [:project-id :uuid]]
   :todo/reactivate-project [:map [:project-id :uuid]]

   :todo/start-weekly-review [:map [:review-id {:optional true} :uuid]]
   :todo/mark-project-reviewed [:map [:review-id :uuid] [:project-id :uuid]]
   :todo/mark-bucket-reviewed [:map [:review-id :uuid] [:bucket ::bucket]]
   :todo/complete-weekly-review [:map [:review-id :uuid]]

   :todo/home-page [:map]
   :todo/tasks-page [:map [:bucket {:optional true} ::bucket]]
   :todo/projects-page [:map]
   :todo/project-page [:map [:project-id :uuid]]
   :todo/review-page [:map]})

;; ----------- ;;
;; Read Models ;;
;; ----------- ;;

(def task-event-types
  #{:todo/task-captured
    :todo/task-renamed
    :todo/task-moved-to-bucket
    :todo/task-assigned-to-project
    :todo/task-removed-from-project
    :todo/task-due-at-set
    :todo/task-due-at-cleared
    :todo/task-deferred
    :todo/task-defer-date-cleared
    :todo/task-completed
    :todo/task-archived
    :todo/task-canceled
    :todo/task-reactivated
    :todo/task-reordered})

(def project-event-types
  #{:todo/project-created
    :todo/project-renamed
    :todo/project-completed
    :todo/project-canceled
    :todo/project-reactivated})

(def review-event-types
  #{:todo/weekly-review-started
    :todo/project-reviewed
    :todo/bucket-reviewed
    :todo/weekly-review-completed})

(defmulti tasks* (fn [_state event] (:event/type event)))

(defmethod tasks* :todo/task-captured
  [state {:keys [task-id title bucket status order project-id due-at defer-until]}]
  (assoc state task-id
         (cond-> {:task-id task-id
                  :title title
                  :bucket bucket
                  :status status
                  :order order}
           project-id (assoc :project-id project-id)
           due-at (assoc :due-at due-at)
           defer-until (assoc :defer-until defer-until))))

(defmethod tasks* :todo/task-renamed
  [state {:keys [task-id title]}]
  (assoc-in state [task-id :title] title))

(defmethod tasks* :todo/task-moved-to-bucket
  [state {:keys [task-id bucket order]}]
  (-> state
      (assoc-in [task-id :bucket] bucket)
      (assoc-in [task-id :order] order)))

(defmethod tasks* :todo/task-assigned-to-project
  [state {:keys [task-id project-id]}]
  (assoc-in state [task-id :project-id] project-id))

(defmethod tasks* :todo/task-removed-from-project
  [state {:keys [task-id]}]
  (update state task-id dissoc :project-id))

(defmethod tasks* :todo/task-due-at-set
  [state {:keys [task-id due-at]}]
  (assoc-in state [task-id :due-at] due-at))

(defmethod tasks* :todo/task-due-at-cleared
  [state {:keys [task-id]}]
  (update state task-id dissoc :due-at))

(defmethod tasks* :todo/task-deferred
  [state {:keys [task-id defer-until]}]
  (assoc-in state [task-id :defer-until] defer-until))

(defmethod tasks* :todo/task-defer-date-cleared
  [state {:keys [task-id]}]
  (update state task-id dissoc :defer-until))

(defmethod tasks* :todo/task-completed
  [state {:keys [task-id]}]
  (assoc-in state [task-id :status] :completed))

(defmethod tasks* :todo/task-archived
  [state {:keys [task-id]}]
  (assoc-in state [task-id :status] :archived))

(defmethod tasks* :todo/task-canceled
  [state {:keys [task-id]}]
  (assoc-in state [task-id :status] :canceled))

(defmethod tasks* :todo/task-reactivated
  [state {:keys [task-id]}]
  (assoc-in state [task-id :status] :active))

(defmethod tasks* :todo/task-reordered
  [state {:keys [task-id order]}]
  (assoc-in state [task-id :order] order))

(defmethod tasks* :default [state _event] state)

(defreadmodel :todo tasks
  {:events task-event-types :version 1}
  [state event]
  (tasks* state event))

(defmulti projects* (fn [_state event] (:event/type event)))

(defmethod projects* :todo/project-created
  [state {:keys [project-id name status]}]
  (assoc state project-id {:project-id project-id :name name :status status}))

(defmethod projects* :todo/project-renamed
  [state {:keys [project-id name]}]
  (assoc-in state [project-id :name] name))

(defmethod projects* :todo/project-completed
  [state {:keys [project-id]}]
  (assoc-in state [project-id :status] :completed))

(defmethod projects* :todo/project-canceled
  [state {:keys [project-id]}]
  (assoc-in state [project-id :status] :canceled))

(defmethod projects* :todo/project-reactivated
  [state {:keys [project-id]}]
  (assoc-in state [project-id :status] :active))

(defmethod projects* :default [state _event] state)

(defreadmodel :todo projects
  {:events project-event-types :version 1}
  [state event]
  (projects* state event))

(defmulti weekly-review* (fn [_state event] (:event/type event)))

(defmethod weekly-review* :todo/weekly-review-started
  [_state {:keys [review-id started-at]}]
  {:review-id review-id
   :status :active
   :started-at started-at
   :reviewed-project-ids #{}
   :reviewed-buckets #{}})

(defmethod weekly-review* :todo/project-reviewed
  [state {:keys [project-id]}]
  (update state :reviewed-project-ids (fnil conj #{}) project-id))

(defmethod weekly-review* :todo/bucket-reviewed
  [state {:keys [bucket]}]
  (update state :reviewed-buckets (fnil conj #{}) bucket))

(defmethod weekly-review* :todo/weekly-review-completed
  [state {:keys [completed-at]}]
  (assoc state :status :completed :completed-at completed-at))

(defmethod weekly-review* :default [state _event] state)

(defreadmodel :todo weekly-review
  {:events review-event-types :version 1}
  [state event]
  (weekly-review* state event))

(defn all-tasks [ctx] (rmp/project ctx :todo/tasks))
(defn all-projects [ctx] (rmp/project ctx :todo/projects))
(defn current-weekly-review [ctx] (rmp/project ctx :todo/weekly-review))

(defn active-task?
  [task]
  (= :active (:status task)))

(defn deferred?
  ([task] (deferred? task (OffsetDateTime/now)))
  ([task now]
   (when-let [defer-until (:defer-until task)]
     (.isAfter defer-until now))))

(defn ordered-tasks
  [tasks]
  (sort-by (juxt :order :title :task-id) tasks))

(defn tasks-for-bucket
  ([ctx bucket] (tasks-for-bucket ctx bucket (OffsetDateTime/now)))
  ([ctx bucket now]
   (->> (vals (all-tasks ctx))
        (filter active-task?)
        (filter #(= bucket (:bucket %)))
        (remove #(deferred? % now))
        ordered-tasks)))

(defn deferred-tasks
  ([ctx] (deferred-tasks ctx (OffsetDateTime/now)))
  ([ctx now]
   (->> (vals (all-tasks ctx))
        (filter active-task?)
        (filter #(deferred? % now))
        ordered-tasks)))

(defn due-soon-tasks
  [ctx]
  (->> (vals (all-tasks ctx))
       (filter active-task?)
       (filter :due-at)
       (sort-by (juxt :due-at :order :title :task-id))))

(defn inactive-tasks
  [ctx]
  (->> (vals (all-tasks ctx))
       (filter #(#{:completed :canceled} (:status %)))
       ordered-tasks))

(defn active-projects
  [ctx]
  (->> (vals (all-projects ctx))
       (filter #(= :active (:status %)))
       (sort-by (juxt :name :project-id))))

(defn project-task-counts
  [ctx project-id]
  (->> (vals (all-tasks ctx))
       (filter #(= project-id (:project-id %)))
       (group-by :status)
       (reduce-kv (fn [m status tasks]
                    (assoc m status (count tasks)))
                  {:active 0 :completed 0 :canceled 0 :archived 0})))

(defn project-summaries
  [ctx]
  (->> (vals (all-projects ctx))
       (sort-by (juxt :status :name :project-id))
       (map (fn [project]
              (assoc project :task-counts (project-task-counts ctx (:project-id project)))))))

(defn active-project-summaries
  [ctx]
  (->> (project-summaries ctx)
       (filter #(= :active (:status %)))))

(defn tasks-for-project
  [ctx project-id]
  (->> (vals (all-tasks ctx))
       (filter active-task?)
       (filter #(= project-id (:project-id %)))
       ordered-tasks))

(defn projects-without-next-action
  [ctx]
  (->> (active-project-summaries ctx)
       (filter #(zero? (get-in % [:task-counts :active] 0)))))

;; -------- ;;
;; Commands ;;
;; -------- ;;

(defn anomaly
  [category message]
  {::anom/category category ::anom/message message})

(defn not-found [message] (anomaly ::anom/not-found message))
(defn conflict [message] (anomaly ::anom/conflict message))
(defn incorrect [message] (anomaly ::anom/incorrect message))

(defn next-order
  [tasks]
  (+ 1000 (or (some->> tasks (map :order) seq (apply max)) 0)))

(defn require-task
  [ctx task-id]
  (or (get (all-tasks ctx) task-id)
      (not-found "Task not found.")))

(defn require-project
  [ctx project-id]
  (or (get (all-projects ctx) project-id)
      (not-found "Project not found.")))

(defcommand :todo capture-task
  {:authorized? (constantly true)}
  "Capture a new task."
  [{{:keys [task-id title bucket project-id due-at defer-until order]} :command :as ctx}]
  (let [task-id (or task-id (random-uuid))
        bucket (or bucket :inbox)
        due-at (some-> due-at coerce-offset-date-time)
        defer-until (some-> defer-until coerce-offset-date-time)
        project (when project-id (require-project ctx project-id))]
    (cond
      (contains? project ::anom/category) project
      (and project (not= :active (:status project))) (conflict "Cannot assign a task to an inactive project.")
      :else
      (let [order (or order (next-order (tasks-for-bucket ctx bucket)))]
        {:command-result/events
         [(->event {:type :todo/task-captured
                    :tags #{[:task task-id]}
                    :body (cond-> {:task-id task-id
                                   :title title
                                   :bucket bucket
                                   :status :active
                                   :order order}
                            project-id (assoc :project-id project-id)
                            due-at (assoc :due-at due-at)
                            defer-until (assoc :defer-until defer-until))})]
         :datastar/signals {:title "" :__toast "Task captured."}}))))

(defcommand :todo rename-task
  {:authorized? (constantly true)}
  [{{:keys [task-id title]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-renamed
                  :tags #{[:task task-id]}
                  :body {:task-id task-id :title title}})]
       :datastar/signals {:__toast "Task renamed."}})))

(defcommand :todo move-task-to-bucket
  {:authorized? (constantly true)}
  [{{:keys [task-id bucket order]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (not= :active (:status task)) (conflict "Only active tasks can be moved.")
      :else
      (let [order (or order (next-order (tasks-for-bucket ctx bucket)))]
        {:command-result/events
         [(->event {:type :todo/task-moved-to-bucket
                    :tags #{[:task task-id]}
                    :body {:task-id task-id :bucket bucket :order order}})]}))))

(defcommand :todo assign-task-to-project
  {:authorized? (constantly true)}
  [{{:keys [task-id project-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        project (require-project ctx project-id)]
    (cond
      (contains? task ::anom/category) task
      (contains? project ::anom/category) project
      (not= :active (:status task)) (conflict "Only active tasks can be assigned.")
      (not= :active (:status project)) (conflict "Cannot assign to an inactive project.")
      :else
      {:command-result/events
       [(->event {:type :todo/task-assigned-to-project
                  :tags #{[:task task-id]}
                  :body {:task-id task-id :project-id project-id}})]})))

(defcommand :todo remove-task-from-project
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (not (:project-id task)) (conflict "Task is not assigned to a project.")
      :else
      {:command-result/events
       [(->event {:type :todo/task-removed-from-project
                  :tags #{[:task task-id]}
                  :body {:task-id task-id :project-id (:project-id task)}})]})))

(defcommand :todo set-task-due-at
  {:authorized? (constantly true)}
  [{{:keys [task-id due-at]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        due-at (coerce-offset-date-time due-at)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-due-at-set
                  :tags #{[:task task-id]}
                  :body {:task-id task-id :due-at due-at}})]})))

(defcommand :todo clear-task-due-at
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-due-at-cleared
                  :tags #{[:task task-id]}
                  :body {:task-id task-id}})]})))

(defcommand :todo defer-task
  {:authorized? (constantly true)}
  [{{:keys [task-id defer-until]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        defer-until (coerce-offset-date-time defer-until)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-deferred
                  :tags #{[:task task-id]}
                  :body {:task-id task-id :defer-until defer-until}})]})))

(defcommand :todo clear-task-defer-date
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-defer-date-cleared
                  :tags #{[:task task-id]}
                  :body {:task-id task-id}})]})))

(defcommand :todo complete-task
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (not= :active (:status task)) (conflict "Only active tasks can be completed.")
      :else {:command-result/events
             [(->event {:type :todo/task-completed
                        :tags #{[:task task-id]}
                        :body {:task-id task-id}})]
             :datastar/signals {:__toast "Task completed."}})))

(defcommand :todo archive-task
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (not= :completed (:status task)) (conflict "Only completed tasks can be archived.")
      :else {:command-result/events
             [(->event {:type :todo/task-archived
                        :tags #{[:task task-id]}
                        :body {:task-id task-id}})]})))

(defcommand :todo cancel-task
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (#{:canceled :archived} (:status task)) (conflict "Task is already inactive.")
      :else {:command-result/events
             [(->event {:type :todo/task-canceled
                        :tags #{[:task task-id]}
                        :body {:task-id task-id}})]})))

(defcommand :todo reactivate-task
  {:authorized? (constantly true)}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (not (#{:completed :canceled} (:status task))) (conflict "Only completed or canceled tasks can be reactivated.")
      :else {:command-result/events
             [(->event {:type :todo/task-reactivated
                        :tags #{[:task task-id]}
                        :body {:task-id task-id}})]})))

(defcommand :todo reorder-task
  {:authorized? (constantly true)}
  [{{:keys [task-id order]} :command :as ctx}]
  (let [task (require-task ctx task-id)]
    (cond
      (contains? task ::anom/category) task
      (not= :active (:status task)) (conflict "Only active tasks can be reordered.")
      :else {:command-result/events
             [(->event {:type :todo/task-reordered
                        :tags #{[:task task-id]}
                        :body {:task-id task-id :order order}})]})))

(defcommand :todo create-project
  {:authorized? (constantly true)}
  [{{:keys [project-id name]} :command}]
  (let [project-id (or project-id (random-uuid))]
    {:command-result/events
     [(->event {:type :todo/project-created
                :tags #{[:project project-id]}
                :body {:project-id project-id :name name :status :active}})]
     :datastar/signals {:projectName "" :__toast "Project created."}}))

(defcommand :todo rename-project
  {:authorized? (constantly true)}
  [{{:keys [project-id name]} :command :as ctx}]
  (let [project (require-project ctx project-id)]
    (if (contains? project ::anom/category)
      project
      {:command-result/events
       [(->event {:type :todo/project-renamed
                  :tags #{[:project project-id]}
                  :body {:project-id project-id :name name}})]})))

(defcommand :todo complete-project
  {:authorized? (constantly true)}
  [{{:keys [project-id]} :command :as ctx}]
  (let [project (require-project ctx project-id)]
    (cond
      (contains? project ::anom/category) project
      (not= :active (:status project)) (conflict "Only active projects can be completed.")
      :else {:command-result/events
             [(->event {:type :todo/project-completed
                        :tags #{[:project project-id]}
                        :body {:project-id project-id}})]})))

(defcommand :todo cancel-project
  {:authorized? (constantly true)}
  [{{:keys [project-id]} :command :as ctx}]
  (let [project (require-project ctx project-id)]
    (cond
      (contains? project ::anom/category) project
      (not= :active (:status project)) (conflict "Only active projects can be canceled.")
      :else {:command-result/events
             [(->event {:type :todo/project-canceled
                        :tags #{[:project project-id]}
                        :body {:project-id project-id}})]})))

(defcommand :todo reactivate-project
  {:authorized? (constantly true)}
  [{{:keys [project-id]} :command :as ctx}]
  (let [project (require-project ctx project-id)]
    (cond
      (contains? project ::anom/category) project
      (not (#{:completed :canceled} (:status project))) (conflict "Only inactive projects can be reactivated.")
      :else {:command-result/events
             [(->event {:type :todo/project-reactivated
                        :tags #{[:project project-id]}
                        :body {:project-id project-id}})]})))

(defcommand :todo start-weekly-review
  {:authorized? (constantly true)}
  [{{:keys [review-id]} :command :as ctx}]
  (let [current (current-weekly-review ctx)]
    (cond
      (= :active (:status current)) (conflict "A weekly review is already active.")
      :else
      (let [review-id (or review-id (random-uuid))
            started-at (OffsetDateTime/now)]
        {:command-result/events
         [(->event {:type :todo/weekly-review-started
                    :tags #{[:review review-id]}
                    :body {:review-id review-id :started-at started-at}})]}))))

(defcommand :todo mark-project-reviewed
  {:authorized? (constantly true)}
  [{{:keys [review-id project-id]} :command :as ctx}]
  (let [review (current-weekly-review ctx)
        project (require-project ctx project-id)]
    (cond
      (contains? project ::anom/category) project
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      (not= :active (:status project)) (conflict "Only active projects can be reviewed.")
      :else
      {:command-result/events
       [(->event {:type :todo/project-reviewed
                  :tags #{[:review review-id]}
                  :body {:review-id review-id :project-id project-id}})]})))

(defcommand :todo mark-bucket-reviewed
  {:authorized? (constantly true)}
  [{{:keys [review-id bucket]} :command :as ctx}]
  (let [review (current-weekly-review ctx)]
    (cond
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      :else
      {:command-result/events
       [(->event {:type :todo/bucket-reviewed
                  :tags #{[:review review-id]}
                  :body {:review-id review-id :bucket bucket}})]})))

(defcommand :todo complete-weekly-review
  {:authorized? (constantly true)}
  [{{:keys [review-id]} :command :as ctx}]
  (let [review (current-weekly-review ctx)
        active-project-ids (set (map :project-id (active-projects ctx)))]
    (cond
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      (not (set/subset? buckets (:reviewed-buckets review))) (conflict "All buckets must be reviewed.")
      (not (set/subset? active-project-ids (:reviewed-project-ids review))) (conflict "All active projects must be reviewed.")
      :else
      {:command-result/events
       [(->event {:type :todo/weekly-review-completed
                  :tags #{[:review review-id]}
                  :body {:review-id review-id :completed-at (OffsetDateTime/now)}})]})))

;; ------- ;;
;; Queries ;;
;; ------- ;;

(defn workspace-data
  [ctx]
  {:buckets (into {} (map (fn [bucket] [bucket (vec (tasks-for-bucket ctx bucket))]) buckets))
   :deferred (vec (deferred-tasks ctx))
   :due-soon (vec (due-soon-tasks ctx))
   :inactive (vec (inactive-tasks ctx))
   :projects (vec (active-projects ctx))
   :project-summaries (vec (project-summaries ctx))
   :review-projects (vec (active-project-summaries ctx))
   :projects-without-next-action (vec (projects-without-next-action ctx))
   :review (current-weekly-review ctx)})

(defquery :todo home-page
  {:authorized? (constantly true)
   :datastar/path "/"
   :datastar/title "Grain Todo"
   :grain/read-models {:todo/tasks 1
                       :todo/projects 1
                       :todo/weekly-review 1}}
  "Main GTD workspace."
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (ui/home-page data)}))

(defquery :todo tasks-page
  {:authorized? (constantly true)
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [bucket]} :query :as ctx}]
  (let [bucket (or bucket :inbox)
        tasks (vec (tasks-for-bucket ctx bucket))]
    {:query/result {:bucket bucket :tasks tasks :projects (vec (active-projects ctx))}
     :datastar/hiccup (ui/tasks-page {:bucket bucket :tasks tasks :projects (vec (active-projects ctx))})}))

(defquery :todo projects-page
  {:authorized? (constantly true)
   :datastar/path "/projects"
   :datastar/title "Projects"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [projects (vec (project-summaries ctx))]
    {:query/result {:projects projects}
     :datastar/hiccup (ui/projects-page {:projects projects})}))

(defquery :todo project-page
  {:authorized? (constantly true)
   :datastar/path "/project"
   :datastar/title "Project"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [project-id]} :query :as ctx}]
  (let [project (get (all-projects ctx) project-id)
        tasks (vec (tasks-for-project ctx project-id))]
    {:query/result {:project (when project
                               (assoc project :task-counts (project-task-counts ctx project-id)))
                    :tasks tasks
                    :projects (vec (active-projects ctx))}
     :datastar/hiccup (ui/project-page {:project (when project
                                                   (assoc project :task-counts (project-task-counts ctx project-id)))
                                        :tasks tasks
                                        :projects (vec (active-projects ctx))})}))

(defquery :todo review-page
  {:authorized? (constantly true)
   :datastar/path "/review"
   :datastar/title "Weekly Review"
   :grain/read-models {:todo/tasks 1 :todo/projects 1 :todo/weekly-review 1}}
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (ui/review-page data)}))

;; --------------- ;;
;; Todo Processors ;;
;; --------------- ;;

;; -------------- ;;
;; Periodic Tasks ;;
;; -------------- ;;


(comment
  
  (def app (start))
  
  (stop app)

  
  "")
