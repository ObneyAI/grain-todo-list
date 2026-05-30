(ns cjbarre.grain-todo-list-test
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [cjbarre.grain-todo-list :as app]
            [cjbarre.grain-todo-list.ui :as ui]
            [cjbarre.grain-todo-list.ui.components :as c]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [cognitect.anomalies :as anom]
            [malli.core :as m])
  (:import [java.time OffsetDateTime]))

(def task-id #uuid "00000000-0000-0000-0000-000000000101")
(def task-2-id #uuid "00000000-0000-0000-0000-000000000102")
(def project-id #uuid "00000000-0000-0000-0000-000000000201")
(def review-id #uuid "00000000-0000-0000-0000-000000000301")
(def now (OffsetDateTime/parse "2026-05-22T12:00:00Z"))
(def later (OffsetDateTime/parse "2026-05-23T12:00:00Z"))
(def later-string "2026-05-24T00:00:00Z")
(def later-from-string (OffsetDateTime/parse later-string))

(defn test-context
  []
  (let [event-store (es/start {:conn {:type :in-memory}})
        cache (kv/start
               (lmdb/->KV-Store-LMDB
                {:storage-dir (str "/tmp/grain-todo-test-" (random-uuid))
                 :db-name "test"}))]
    {:event-store event-store
     :cache cache
     :tenant-id (random-uuid)
     :command-registry (cp/global-command-registry)
     :query-registry (qp/global-query-registry)
     ::stop (fn []
              (kv/stop cache)
              (es/stop event-store))}))

(defn with-context
  [f]
  (let [ctx (test-context)]
    (try
      (f ctx)
      (finally
        ((::stop ctx))))))

(defn command
  [name fields]
  (merge {:command/id (random-uuid)
          :command/name name
          :command/timestamp now}
         fields))

(defn process!
  [ctx name fields]
  (cp/process-command (assoc ctx :command (command name fields))))

(defn query
  [name fields]
  (merge {:query/id (random-uuid)
          :query/name name
          :query/timestamp now}
         fields))

(defn run-query
  [ctx name fields]
  (qp/process-query (assoc ctx :query (query name fields))))

(defn event-of-type
  [result event-type]
  (some #(when (= event-type (:event/type %)) %) (:command-result/events result)))

(defn anomaly?
  [result]
  (contains? result ::anom/category))

(defn project-tasks
  [ctx]
  (rmp/project ctx :todo/tasks))

(defn project-projects
  [ctx]
  (rmp/project ctx :todo/projects))

(defn project-review
  [ctx]
  (rmp/project ctx :todo/weekly-review))

(use-fixtures :each
  (fn [f]
    (rmp/l1-clear!)
    (f)
    (rmp/l1-clear!)))

(deftest pure-schema-validation
  (testing "valid payloads satisfy app schemas"
    (is (m/validate :todo/capture-task
                    (command :todo/capture-task
                             {:task-id task-id
                              :title "Plan project"
                              :bucket :next
                              :due-at later
                              :defer-until later})))
    (is (m/validate :todo/task-captured
                    {:event/id (random-uuid)
                     :event/timestamp now
                     :event/type :todo/task-captured
                     :event/tags #{[:task task-id]}
                     :task-id task-id
                     :title "Plan project"
                     :bucket :next
                     :status :active
                     :order 1000})))
  (testing "invalid payloads fail schema validation"
    (is (not (m/validate :todo/capture-task (command :todo/capture-task {:title "  "}))))
    (is (not (m/validate :todo/capture-task (command :todo/capture-task {:title "x" :bucket :bad}))))
    (is (not (m/validate :todo/set-task-due-at (command :todo/set-task-due-at {:task-id task-id :due-at "tomorrow"}))))))

(deftest pure-reducer-and-helper-tests
  (testing "task reducer is pure and reconstructs state from event maps"
    (let [state (-> {}
                    (app/tasks* {:event/type :todo/task-captured
                                 :task-id task-id
                                 :title "Inbox task"
                                 :bucket :inbox
                                 :status :active
                                 :order 1000})
                    (app/tasks* {:event/type :todo/task-moved-to-bucket
                                 :task-id task-id
                                 :bucket :next
                                 :order 2000})
                    (app/tasks* {:event/type :todo/task-completed
                                 :task-id task-id}))]
      (is (= :completed (get-in state [task-id :status])))
      (is (= :next (get-in state [task-id :bucket])))))
  (testing "pure ordering helper sorts by order then title"
    (is (= [task-2-id task-id]
           (map :task-id
                (app/ordered-tasks [{:task-id task-id :title "B" :order 2}
                                    {:task-id task-2-id :title "A" :order 1}]))))))

(deftest command-processor-task-lifecycle
  (with-context
    (fn [ctx]
      (testing "commands go through Grain command processor and append events"
        (let [result (process! ctx :todo/capture-task
                               {:task-id task-id :title "Capture task" :bucket :inbox})]
          (is (not (anomaly? result)))
          (is (event-of-type result :todo/task-captured))
          (is (= "Capture task" (get-in (project-tasks ctx) [task-id :title])))))
      (testing "invalid commands return anomalies and emit no events"
        (let [result (process! ctx :todo/capture-task {:title " "})]
          (is (= ::anom/incorrect (::anom/category result)))
          (is (nil? (:command-result/events result)))))
      (testing "task lifecycle transitions are enforced"
        (is (= ::anom/conflict
               (::anom/category (process! ctx :todo/archive-task {:task-id task-id}))))
        (is (event-of-type (process! ctx :todo/complete-task {:task-id task-id})
                           :todo/task-completed))
        (is (= :completed (get-in (project-tasks ctx) [task-id :status])))
        (is (event-of-type (process! ctx :todo/archive-task {:task-id task-id})
                           :todo/task-archived))
        (is (= :archived (get-in (project-tasks ctx) [task-id :status])))))))

(deftest command-processor-task-clarification
  (with-context
    (fn [ctx]
      (process! ctx :todo/capture-task {:task-id task-id :title "Clarify me"})
      (testing "bucket movement, dates, and ordering go through commands"
        (process! ctx :todo/move-task-to-bucket {:task-id task-id :bucket :next :order 50})
        (process! ctx :todo/set-task-due-at {:task-id task-id :due-at later})
        (process! ctx :todo/defer-task {:task-id task-id :defer-until later})
        (let [task (get (project-tasks ctx) task-id)]
          (is (= :next (:bucket task)))
          (is (= 50 (:order task)))
          (is (= later (:due-at task)))
          (is (= later (:defer-until task)))))
      (testing "clear commands remove date metadata"
        (process! ctx :todo/clear-task-due-at {:task-id task-id})
        (process! ctx :todo/clear-task-defer-date {:task-id task-id})
        (is (not (contains? (get (project-tasks ctx) task-id) :due-at)))
        (is (not (contains? (get (project-tasks ctx) task-id) :defer-until))))
      (testing "date commands accept parseable strings and emit OffsetDateTime values"
        (process! ctx :todo/set-task-due-at {:task-id task-id :due-at later-string})
        (process! ctx :todo/defer-task {:task-id task-id :defer-until later-string})
        (let [task (get (project-tasks ctx) task-id)]
          (is (= later-from-string (:due-at task)))
          (is (= later-from-string (:defer-until task))))))))

(deftest command-processor-projects-and-assignment
  (with-context
   (fn [ctx]
     (testing "project commands go through Grain command processor"
       (is (event-of-type (process! ctx :todo/create-project {:project-id project-id :name "Ship v1"})
                          :todo/project-created))
       (is (= "Ship v1" (get-in (project-projects ctx) [project-id :name])))
       (is (event-of-type (process! ctx :todo/rename-project {:project-id project-id :name "Ship v2"})
                          :todo/project-renamed))
       (is (= "Ship v2" (get-in (project-projects ctx) [project-id :name]))))
     (testing "task assignment tags and projects through read models"
       (process! ctx :todo/capture-task {:task-id task-id :title "Project task"})
       (let [result (process! ctx :todo/assign-task-to-project {:task-id task-id :project-id project-id})
             event (event-of-type result :todo/task-assigned-to-project)]
         (is event)
         (is (= #{[:task task-id]} (:event/tags event)))
         (is (= project-id (get-in (project-tasks ctx) [task-id :project-id]))))
       (is (event-of-type (process! ctx :todo/remove-task-from-project {:task-id task-id})
                          :todo/task-removed-from-project))
       (is (not (contains? (get (project-tasks ctx) task-id) :project-id))))
     (testing "inactive projects reject assignment"
       (process! ctx :todo/cancel-project {:project-id project-id})
       (process! ctx :todo/capture-task {:task-id task-2-id :title "Blocked assignment"})
       (is (= ::anom/conflict
              (::anom/category
               (process! ctx :todo/assign-task-to-project
                         {:task-id task-2-id :project-id project-id})))))
       (is (= :canceled (get-in (project-projects ctx) [project-id :status])))
     (testing "inactive projects can be reactivated"
       (is (event-of-type (process! ctx :todo/reactivate-project {:project-id project-id})
                          :todo/project-reactivated))
       (is (= :active (get-in (project-projects ctx) [project-id :status])))))))

(deftest grain-projection-helper-tests
  (with-context
    (fn [ctx]
      (process! ctx :todo/capture-task {:task-id task-id :title "Visible" :bucket :next :order 20})
      (process! ctx :todo/capture-task {:task-id task-2-id :title "Deferred" :bucket :next :defer-until later :order 10})
      (testing "helpers read real Grain projections"
        (is (= [task-id] (map :task-id (app/tasks-for-bucket ctx :next now))))
        (is (= [task-2-id] (map :task-id (app/deferred-tasks ctx now))))
        (is (= [task-2-id task-id]
               (map :task-id (app/tasks-for-bucket ctx :next (.plusDays later 1)))))))))

(deftest query-processor-tests
  (with-context
    (fn [ctx]
      (process! ctx :todo/capture-task {:task-id task-id :title "Render me" :bucket :inbox})
      (testing "queries go through Grain query processor"
        (let [result (run-query ctx :todo/home-page {})]
          (is (not (anomaly? result)))
          (is (= "Render me" (get-in result [:query/result :buckets :inbox 0 :title])))
          (is (= :div#app (first (:datastar/hiccup result))))))
      (testing "path/query params are validated and decoded through query processor"
        (let [result (run-query ctx :todo/tasks-page {:bucket :inbox})]
          (is (= :inbox (:bucket (:query/result result))))
          (is (= [task-id] (map :task-id (:tasks (:query/result result)))))))
      (testing "task page query renders dedicated task editing surface"
        (let [result (run-query ctx :todo/task-page {:task-id task-id})
              leaves (tree-seq coll? seq (:datastar/hiccup result))
              attrs (filter map? leaves)]
          (is (= "Render me" (get-in result [:query/result :task :title])))
          (is (not (some #(= "Rename" %) leaves)))
          (is (some #(contains? % :data-on:blur) attrs))
          (is (some #(contains? % :data-on:keydown) attrs))
          (is (some #(contains? % :data-on:change) attrs))
          (is (some #(= "Status" %) leaves))
          (is (some #(= "Bucket" %) leaves))
          (is (some #(= "Project" %) leaves))
          (is (some #(= "Schedule" %) leaves))
          (is (not (some #(= "Edit details" %) leaves)))))
      (testing "project page query includes task counts from projections"
        (process! ctx :todo/create-project {:project-id project-id :name "Counted project"})
        (process! ctx :todo/assign-task-to-project {:task-id task-id :project-id project-id})
        (let [result (run-query ctx :todo/project-page {:project-id project-id})]
          (is (= 1 (get-in result [:query/result :project :task-counts :active])))
          (is (= [task-id] (map :task-id (get-in result [:query/result :tasks]))))))
      (testing "review page query surfaces review material from real projections"
        (let [future-date (.plusDays (OffsetDateTime/now) 1)]
          (process! ctx :todo/capture-task {:task-id task-2-id
                                            :title "Timed review item"
                                            :bucket :waiting
                                            :due-at future-date
                                            :defer-until future-date}))
        (let [result (run-query ctx :todo/review-page {})
              query-result (:query/result result)]
          (is (= "Timed review item" (get-in query-result [:due-soon 0 :title])))
          (is (= "Timed review item" (get-in query-result [:deferred 0 :title])))
          (is (= "Counted project" (get-in query-result [:review-projects 0 :name])))
          (is (empty? (:projects-without-next-action query-result)))))
      (testing "dev gallery query renders inert gallery Hiccup"
        (let [result (run-query ctx :todo/dev-gallery-page {})]
          (is (not (anomaly? result)))
          (is (= :div#app (first (:datastar/hiccup result))))
          (is (some #(= "Dev UI Gallery" %) (tree-seq coll? seq (:datastar/hiccup result)))))))))

(deftest weekly-review-command-and-projection-tests
  (with-context
    (fn [ctx]
      (testing "review cannot complete before all buckets are reviewed"
        (process! ctx :todo/create-project {:project-id project-id :name "Review project"})
        (process! ctx :todo/start-weekly-review {:review-id review-id})
        (is (= :active (:status (project-review ctx))))
        (is (= ::anom/conflict
               (::anom/category (process! ctx :todo/complete-weekly-review {:review-id review-id})))))
      (testing "project review commands validate active projects through the processor"
        (is (= ::anom/not-found
               (::anom/category (process! ctx :todo/mark-project-reviewed
                                          {:review-id review-id :project-id task-id}))))
        (process! ctx :todo/cancel-project {:project-id project-id})
        (is (= ::anom/conflict
               (::anom/category (process! ctx :todo/mark-project-reviewed
                                          {:review-id review-id :project-id project-id}))))
        (process! ctx :todo/reactivate-project {:project-id project-id}))
      (testing "review completes after all buckets and active projects are reviewed"
        (doseq [bucket app/buckets]
          (process! ctx :todo/mark-bucket-reviewed {:review-id review-id :bucket bucket}))
        (process! ctx :todo/mark-project-reviewed {:review-id review-id :project-id project-id})
        (let [result (process! ctx :todo/complete-weekly-review {:review-id review-id})]
          (is (event-of-type result :todo/weekly-review-completed))
          (is (= :completed (:status (project-review ctx)))))))))

(deftest pure-ui-rendering-tests
  (testing "UI substrate primitives render reusable section, empty, and summary surfaces"
    (let [hiccup [:div
                  (c/page-section {:title "Substrate" :count 2}
                                  (c/empty-state "Nothing to show."))
                  (c/task-summary-row {:task-id task-id
                                       :title "Summary task"
                                       :bucket :next
                                       :status :active
                                       :order 1000
                                       :due-at later}
                                      [{:project-id project-id :name "Project"}])
                  (c/project-summary-row {:project-id project-id
                                          :name "Summary project"
                                          :status :active
                                          :task-counts {:active 1 :completed 0}})]
          leaves (tree-seq coll? seq hiccup)]
      (is (some #(= "Substrate" %) leaves))
      (is (some #(= "Nothing to show." %) leaves))
      (is (some #(= "Summary task" %) leaves))
      (is (some #(= "Summary project" %) leaves))))

  (testing "pure Hiccup rendering can be tested without Grain processors"
    (let [hiccup (ui/home-page {:buckets {:inbox [{:task-id task-id
                                                   :title "UI task"
                                                   :bucket :inbox
                                                   :status :active
                                                   :order 1000}]
                                         :next []
                                         :waiting []
                                         :someday []}
                                :deferred []
                                :due-soon []
                                :projects []
                                :review {}})]
      (is (= :div#app (first hiccup)))
      (is (some #(= "UI task" %) (tree-seq coll? seq hiccup)))))

  (testing "review page exposes projection-derived progress without running processors"
    (let [hiccup (ui/review-page {:buckets {:inbox []
                                            :next [{:task-id task-id}]
                                            :waiting []
                                            :someday []}
                                  :deferred []
                                  :due-soon [{:task-id task-id
                                              :title "Review due"
                                              :bucket :next
                                              :status :active
                                              :due-at later}]
                                  :inactive []
                                  :projects [{:project-id project-id :name "UI project"}]
                                  :review-projects [{:project-id project-id
                                                     :name "UI project"
                                                     :status :active
                                                     :task-counts {:active 0 :completed 0}}]
                                  :projects-without-next-action [{:project-id project-id
                                                                  :name "UI project"
                                                                  :status :active
                                                                  :task-counts {:active 0 :completed 0}}]
                                  :review {:review-id review-id
                                           :status :active
                                           :reviewed-buckets #{:inbox :next}
                                           :reviewed-project-ids #{project-id}}})]
      (is (some #(= "2/4 buckets reviewed" %) (tree-seq coll? seq hiccup)))
      (is (some #(= "1/1 projects reviewed" %) (tree-seq coll? seq hiccup)))
      (is (some #(= "Review due" %) (tree-seq coll? seq hiccup)))
      (is (some #(= "Projects Without Next Actions" %) (tree-seq coll? seq hiccup)))))

  (testing "dev gallery renders canonical fixtures without command post actions"
    (let [hiccup (ui/dev-gallery-page)
          leaves (tree-seq coll? seq hiccup)
          attrs (filter map? leaves)]
      (is (= :div#app (first hiccup)))
      (is (some #(= "Dev UI Gallery" %) leaves))
      (is (some #(= "Design System" %) leaves))
      (is (some #(= "Fundamental Elements" %) leaves))
      (is (some #(= "Color + Glass" %) leaves))
      (is (some #(= "Typography" %) leaves))
      (is (some #(= "Surfaces" %) leaves))
      (is (some #(= "Inputs" %) leaves))
      (is (some #(= "Actions" %) leaves))
      (is (some #(= "Status" %) leaves))
      (is (some #(= "Feedback" %) leaves))
      (is (some #(= "Vista Aero Minimal" %) leaves))
      (is (not (some #(= "Aero Glass Console" %) leaves)))
      (is (not (some #(= "Windows 7 Productivity" %) leaves)))
      (is (some #(= "Draft inbox capture conventions" %) leaves))
      (is (some #(= "Launch reference workflow" %) leaves))
      (is (some #(= "Forms and Alerts" %) leaves))
      (is (some #(= "Unable to save changes." %) leaves))
      (is (some #(= "Task Detail and Editing" %) leaves))
      (is (not (some #(= "Rename" %) leaves)))
      (is (some #(= "Schedule" %) leaves))
      (is (some #(= "Task action states" %) leaves))
      (is (some #(= "Project action states" %) leaves))
      (is (not (some #(= "Edit project" %) leaves)))
      (is (some #(contains? % :data-bind) attrs))
      (is (some #(= "No active projects." %) leaves))
      (is (some #(= "No active projects to review." %) leaves))
      (is (not (some #(= "data-uidotsh-pick" %) leaves)))
      (is (not (some (fn [[k _]]
                       (and (keyword? k)
                            (or (= "data-on" (namespace k))
                                (string/starts-with? (name k) "data-on"))))
                     (mapcat seq attrs))))
      (is (not (some #(and (string? %) (string/includes? % "@post('/actions')")) leaves)))))

  (testing "cards stay minimal and task page owns the edit UI"
    (let [task {:task-id task-id
                :title "Minimal card"
                :bucket :next
                :status :active
                :order 1000}
          card-leaves (tree-seq coll? seq (c/task-card task []))
          page-leaves (tree-seq coll? seq (ui/task-page {:task task :projects []}))
          page-attrs (filter map? page-leaves)]
      (is (some #(= "Minimal card" %) card-leaves))
      (is (some #(= "open" %) card-leaves))
      (is (not (some #(= "Edit details" %) card-leaves)))
      (is (some #(= "Status" %) page-leaves))
      (is (some #(= "Bucket" %) page-leaves))
      (is (some #(= "Schedule" %) page-leaves))
      (is (some #(contains? % :data-on:blur) page-attrs))
      (is (some #(contains? % :data-on:change) page-attrs))
      (is (some #(contains? % :data-on:keydown) page-attrs))
      (is (not (some #(= "Edit details" %) page-leaves))))))
