(ns cjbarre.grain-todo-list-test
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [cjbarre.grain-todo-list.foundation.auth-interceptors :as auth]
            [cjbarre.grain-todo-list :as app]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as todo-read-models]
            [cjbarre.grain-todo-list.service.todo-list-service.ui :as ui]
            [cjbarre.grain-todo-list.service.todo-list-service.ui.components :as tc]
            [cjbarre.grain-todo-list.foundation.ui.components :as c]
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

(defn attr-key
  [k]
  (if (keyword? k)
    (if-let [ns (namespace k)]
      (str ns ":" (name k))
      (name k))
    k))

(defn attr-value
  [attrs k]
  (or (get attrs k)
      (get attrs (attr-key k))))

(defn has-attr?
  [attrs k]
  (or (contains? attrs k)
      (contains? attrs (attr-key k))))

(defn render-ui
  [hiccup]
  (ds-ui/hiccup hiccup))

(defn data-on-key?
  [k]
  (let [s (attr-key k)]
    (string/starts-with? s "data-on")))

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

(deftest root-app-composition-test
  (testing "root app namespace owns composition and lifecycle entrypoints"
    (is (map? app/system))
    (is (contains? app/system :cjbarre.grain-todo-list/routes))
    (is (contains? app/system :cjbarre.grain-todo-list/auth-token-verifier))
    (is (fn? app/start))
    (is (fn? app/stop))))

(deftest pure-schema-validation
  (testing "valid payloads satisfy app schemas"
    (is (m/validate :todo/capture-task
                    (command :todo/capture-task
                             {:task-id task-id
                              :title "Plan project"
                              :due-within-days 3})))
    (is (m/validate :todo/task-captured
                    {:event/id (random-uuid)
                     :event/timestamp now
                     :event/type :todo/task-captured
                     :event/tags #{[:task task-id]}
                     :task-id task-id
                     :title "Plan project"
                     :status :active
                     :order 1000
                     :due-within-days 3
                     :due-within-set-at later})))
    (is (m/validate :todo/tasks
                    {task-id {:task-id task-id
                              :title "Projected task"
                              :status :active
                              :order 1000
                              :project-id project-id
                              :due-within-days 3
                              :due-within-set-at later}}))
    (is (m/validate :todo/projects
                    {project-id {:project-id project-id
                                 :name "Projected project"
                                 :status :active
                                 :task-counts {:active 1
                                               :completed 0
                                               :canceled 0
                                               :archived 0}}}))
    (is (m/validate :todo/weekly-review nil))
    (is (m/validate :todo/weekly-review
                    {:review-id review-id
                     :status :active
                     :started-at now
                     :reviewed-project-ids #{project-id}
                     :reviewed-task-ids #{task-id}}))
    (is (m/validate :todo/tasks-page
                    (query :todo/tasks-page {}))))
  (testing "invalid payloads fail schema validation"
    (is (not (m/validate :todo/capture-task (command :todo/capture-task {:title "  "}))))
    (is (not (m/validate :todo/set-task-due-within
                         (command :todo/set-task-due-within {:task-id task-id :due-within-days -1}))))
    (is (not (m/validate :todo/tasks
                         {task-id {:task-id task-id
                                   :title "Missing status"
                                   :order 1000}}))))

(deftest pure-reducer-and-helper-tests
  (testing "task reducer is pure and reconstructs state from event maps"
      (let [state (-> {}
                    (todo-read-models/tasks* {:event/type :todo/task-captured
                                              :task-id task-id
                                              :title "Inbox task"
                                              :status :active
                                              :order 1000})
                    (todo-read-models/tasks* {:event/type :todo/task-reordered
                                              :task-id task-id
                                              :order 2000})
                    (todo-read-models/tasks* {:event/type :todo/task-completed
                                              :task-id task-id}))]
      (is (m/validate :todo/tasks state))
      (is (= :completed (get-in state [task-id :status])))
      (is (= 2000 (get-in state [task-id :order])))))
  (testing "pure ordering helper sorts by order then title"
    (is (= [task-2-id task-id]
           (map :task-id
                (todo-read-models/ordered-tasks [{:task-id task-id :title "B" :order 2}
                                                 {:task-id task-2-id :title "A" :order 1}]))))))

(deftest command-processor-task-lifecycle
  (with-context
    (fn [ctx]
      (testing "commands go through Grain command processor and append events"
        (let [result (process! ctx :todo/capture-task
                               {:task-id task-id :title "Add task"})]
          (is (not (anomaly? result)))
          (is (event-of-type result :todo/task-captured))
          (is (= {:__toast "Task added."} (:datastar/signals result)))
          (is (not (contains? (:datastar/signals result) :title)))
          (is (= "Add task" (get-in (project-tasks ctx) [task-id :title])))))
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
      (testing "due-within and ordering go through commands"
        (process! ctx :todo/reorder-task {:task-id task-id :order 50})
        (process! ctx :todo/set-task-due-within {:task-id task-id :due-within-days 0})
        (let [task (get (project-tasks ctx) task-id)]
          (is (= 50 (:order task)))
          (is (= 0 (:due-within-days task)))
          (is (instance? OffsetDateTime (:due-within-set-at task)))))
      (testing "clear command removes due metadata"
        (process! ctx :todo/clear-task-due-within {:task-id task-id})
        (let [task (get (project-tasks ctx) task-id)]
          (is (not (contains? task :due-within-days)))
          (is (not (contains? task :due-within-set-at))))))))

(deftest command-processor-projects-and-assignment
  (with-context
   (fn [ctx]
     (testing "project commands go through Grain command processor"
       (let [result (process! ctx :todo/create-project {:project-id project-id :name "Ship v1"})]
         (is (event-of-type result :todo/project-created))
         (is (= {:__toast "Project created."} (:datastar/signals result)))
         (is (not (contains? (:datastar/signals result) :projectName))))
       (is (= "Ship v1" (get-in (project-projects ctx) [project-id :name])))
       (is (m/validate :todo/projects (project-projects ctx)))
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
      (process! ctx :todo/capture-task {:task-id task-id :title "Visible" :order 20})
      (process! ctx :todo/capture-task {:task-id task-2-id :title "Soon" :due-within-days 0 :order 10})
      (testing "helpers read real Grain projections"
        (is (= [task-2-id task-id] (map :task-id (todo-read-models/active-tasks ctx))))
        (is (= [task-2-id] (map :task-id (todo-read-models/due-soon-tasks ctx))))))))

(deftest query-processor-tests
  (with-context
    (fn [ctx]
      (process! ctx :todo/capture-task {:task-id task-id :title "Render me"})
      (testing "queries go through Grain query processor"
        (let [result (run-query ctx :todo/home-page {})]
          (is (not (anomaly? result)))
          (is (= "Render me" (get-in result [:query/result :tasks 0 :title])))
          (is (= :div#app (first (:datastar/hiccup result))))))
      (testing "workspace project cards include task counts"
        (process! ctx :todo/create-project {:project-id project-id :name "Counted project"})
        (process! ctx :todo/assign-task-to-project {:task-id task-id :project-id project-id})
        (let [result (run-query ctx :todo/home-page {})
              leaves (tree-seq coll? seq (:datastar/hiccup result))]
          (is (= 1 (get-in result [:query/result :projects 0 :task-counts :active])))
          (is (some #(= "1 active" %) leaves))))
      (testing "path/query params are validated and decoded through query processor"
        (let [result (run-query ctx :todo/tasks-page {})]
          (is (= [task-id] (map :task-id (:tasks (:query/result result)))))))
      (testing "task page query renders dedicated task editing surface"
        (let [result (run-query ctx :todo/task-page {:task-id task-id})
              leaves (tree-seq coll? seq (:datastar/hiccup result))
              attrs (filter map? leaves)]
          (is (= "Render me" (get-in result [:query/result :task :title])))
          (is (not (some #(= "Rename" %) leaves)))
          (is (some #(has-attr? % :data-on:blur) attrs))
          (is (some #(has-attr? % :data-on:keydown) attrs))
          (is (some #(has-attr? % :data-on:change) attrs))
          (is (some #(= "Status" %) leaves))
          (is (some #(= "Project" %) leaves))
          (is (some #(= "Due" %) leaves))
          (is (not (some #(= "Edit details" %) leaves)))))
      (testing "project page query includes task counts from projections"
        (let [result (run-query ctx :todo/project-page {:project-id project-id})]
          (is (= 1 (get-in result [:query/result :project :task-counts :active])))
          (is (= [task-id] (map :task-id (get-in result [:query/result :tasks]))))))
      (testing "review page query surfaces review material from real projections"
        (process! ctx :todo/capture-task {:task-id task-2-id
                                          :title "Timed review item"
                                          :due-within-days 1})
        (let [result (run-query ctx :todo/review-page {})
              query-result (:query/result result)]
          (is (= "Timed review item" (get-in query-result [:due-soon 0 :title])))
          (is (= #{"Render me" "Timed review item"} (set (map :title (:tasks query-result)))))
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
      (testing "review cannot complete before all active tasks and projects are reviewed"
        (process! ctx :todo/create-project {:project-id project-id :name "Review project"})
        (process! ctx :todo/capture-task {:task-id task-id :title "Review task"})
        (process! ctx :todo/start-weekly-review {:review-id review-id})
        (is (= :active (:status (project-review ctx))))
        (is (m/validate :todo/weekly-review (project-review ctx)))
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
      (testing "review completes after all active tasks and active projects are reviewed"
        (process! ctx :todo/mark-task-reviewed {:review-id review-id :task-id task-id})
        (process! ctx :todo/mark-project-reviewed {:review-id review-id :project-id project-id})
        (let [result (process! ctx :todo/complete-weekly-review {:review-id review-id})]
          (is (event-of-type result :todo/weekly-review-completed))
          (is (= :completed (:status (project-review ctx)))))))))

(deftest pure-ui-rendering-tests
  (testing "UI substrate primitives render reusable section and empty surfaces"
    (let [hiccup [:div
                  (c/page-section {:title "Substrate" :count 2}
                                  (c/empty-state "Nothing to show."))
                  (c/panel {:title "Panel Primitive"
                            :status "Grouped surface"}
                           (c/empty-state "Quiet well."))]
          leaves (tree-seq coll? seq hiccup)]
      (is (some #(= "Substrate" %) leaves))
      (is (some #(= "Panel Primitive" %) leaves))
      (is (some #(= "Quiet well." %) leaves))
      (is (some #(= "Nothing to show." %) leaves))))

  (testing "todo service components render task and project summaries"
    (let [hiccup (render-ui
                  [:div
                   (tc/task-summary-row {:task-id task-id
                                         :title "Summary task"
                                         :status :active
                                         :order 1000
                                         :due-within-days 1
                                         :due-within-set-at later}
                                        [{:project-id project-id :name "Project"}])
                   (tc/project-summary-row {:project-id project-id
                                            :name "Summary project"
                                            :status :active
                                            :task-counts {:active 1 :completed 0}})])
          leaves (tree-seq coll? seq hiccup)
          attrs (filter map? leaves)]
      (is (some #(= "Summary task" %) leaves))
      (is (some #(= "Summary project" %) leaves))
      (is (some #(= "1 active" %) leaves))
      (is (= 1 (count (filter #(= "Due within 1 day" %) leaves))))
      (is (some #(= (str "/task?task-id=" task-id) (:href %)) attrs))
      (is (some #(= (str "/project?project-id=" project-id) (:href %)) attrs))))

  (testing "pure Hiccup rendering can be tested without Grain processors"
    (let [hiccup (render-ui
                  (ui/home-page {:tasks [{:task-id task-id
                                          :title "UI task"
                                          :status :active
                                          :order 1000}]
                                 :due-soon []
                                 :inactive [{:task-id task-2-id
                                             :title "Closed task"
                                             :status :completed
                                             :order 2000}]
                                 :projects []
                                 :review {}}))
          leaves (tree-seq coll? seq hiccup)
          attrs (filter map? leaves)]
      (is (= :div#app (first hiccup)))
      (is (some #(= "Tasks" %) leaves))
      (is (some #(= "Planning" %) leaves))
      (is (some #(= "Projects" %) leaves))
      (is (some #(= "Weekly Review" %) leaves))
      (is (some #(= "Add a task" %) leaves))
      (is (some #(= "UI task" %) leaves))
      (is (some #(= "Done / Canceled" %) leaves))
      (is (some #(= "1 item closed" %) leaves))
      (is (not (some #(= "Closed task" %) leaves)))
      (is (some #(= (str "/task?task-id=" task-id) (:href %)) attrs))
      (is (some #(= "/review" (:href %)) attrs))))

  (testing "review page exposes projection-derived progress without running processors"
    (let [hiccup (render-ui
                  (ui/review-page {:tasks [{:task-id task-id
                                            :title "Review task"
                                            :status :active
                                            :order 1000}]
                                   :due-soon [{:task-id task-id
                                               :title "Review due"
                                               :status :active
                                               :order 1000
                                               :due-within-days 1
                                               :due-within-set-at later}]
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
                                            :reviewed-task-ids #{task-id}
                                            :reviewed-project-ids #{project-id}}}))]
      (is (some #(= "1/1 tasks reviewed" %) (tree-seq coll? seq hiccup)))
      (is (some #(= "1/1 projects reviewed" %) (tree-seq coll? seq hiccup)))
      (is (some #(= "Review due" %) (tree-seq coll? seq hiccup)))
      (is (some #(= "Projects Needing Tasks" %) (tree-seq coll? seq hiccup)))))

  (testing "dev gallery renders canonical fixtures without command post actions"
    (let [hiccup (render-ui (ui/dev-gallery-page))
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
      (is (some #(= "Panel surface" %) leaves))
      (is (some #(= "Home Panels" %) leaves))
      (is (some #(= "Vista Aero Minimal" %) leaves))
      (is (not (some #(= "Aero Glass Console" %) leaves)))
      (is (not (some #(= "Windows 7 Productivity" %) leaves)))
      (is (some #(= "Draft task entry guidelines" %) leaves))
      (is (some #(= "Launch reference checklist" %) leaves))
      (is (some #(= "Forms and Alerts" %) leaves))
      (is (some #(= "Unable to save changes." %) leaves))
      (is (some #(= "Task Detail and Editing" %) leaves))
      (is (not (some #(= "Rename" %) leaves)))
      (is (some #(= "Due" %) leaves))
      (is (some #(= "Task action states" %) leaves))
      (is (some #(= "Project action states" %) leaves))
      (is (not (some #(= "Edit project" %) leaves)))
      (is (not (some #(contains? % :data-bind) attrs)))
      (is (some #(= "No active projects." %) leaves))
      (is (some #(= "No active projects to review." %) leaves))
      (is (not (some #(= "data-uidotsh-pick" %) leaves)))
      (is (not (some (fn [[k _]] (data-on-key? k))
                     (mapcat seq attrs))))
      (is (not (some #(and (string? %) (string/includes? % "@post('/actions')")) leaves)))))

  (testing "home command forms initialize plain Datastar command signals"
    (let [hiccup (render-ui
                  (ui/home-page {:tasks []
                                 :due-soon []
                                 :inactive []
                                 :projects []
                                 :review nil}))
          leaves (tree-seq coll? seq hiccup)
          attrs (filter map? leaves)
          signal-strings (keep :data-signals attrs)
          submit-strings (keep #(attr-value % :data-on:submit__prevent) attrs)]
      (is (some #(string/includes? % "\"title__") signal-strings))
      (is (some #(string/includes? % "project-add-name") signal-strings))
      (is (every? #(string/includes? % "@post($__grainAction") submit-strings))
      (is (some #(string/includes? % "todo/capture-task") submit-strings))
      (is (some #(string/includes? % "todo/create-project") submit-strings))
      (is (some #(string/includes? % "\"title\":") submit-strings))
      (is (some #(string/includes? % "\"name\":") submit-strings))
      (is (some #(string/includes? % " = \"\"") submit-strings))))

  (testing "cards stay minimal and task page owns the edit UI"
    (let [task {:task-id task-id
                :title "Minimal card"
                :status :active
                :order 1000
                :project-id project-id
                :due-within-days 1
                :due-within-set-at later}
          card-hiccup (render-ui (tc/task-card task []))
          page-hiccup (render-ui (ui/task-page {:task task :projects []}))
          card-leaves (tree-seq coll? seq card-hiccup)
          card-attrs (filter map? card-leaves)
          page-leaves (tree-seq coll? seq page-hiccup)
          page-attrs (filter map? page-leaves)
          page-attr-values (filter string? (mapcat vals page-attrs))]
      (is (some #(= "Minimal card" %) card-leaves))
      (is (not (some #(= "open" %) card-leaves)))
      (is (some #(= (str "/task?task-id=" task-id) (:href %)) card-attrs))
      (is (not (some #(= "active" %) card-leaves)))
      (is (some #(= "Project" %) card-leaves))
      (is (some #(= "Due within 1 day" %) card-leaves))
      (is (not (some #(= "done" %) card-leaves)))
      (is (some #(= "Complete task" (:aria-label %)) card-attrs))
      (is (some #(and (has-attr? % :data-on:click)
                      (string? (attr-value % :data-on:click))
                      (string/includes? (attr-value % :data-on:click) "todo/complete-task"))
                card-attrs))
      (is (not (some #(= "Edit details" %) card-leaves)))
      (is (some #(= "Status" %) page-leaves))
      (is (some #(= "Complete task" %) page-leaves))
      (is (some #(= "Cancel task" %) page-leaves))
      (is (not (some #(= "canceled" %) page-leaves)))
      (is (not (some #(= "Bucket" %) page-leaves)))
      (is (some #(= "Due" %) page-leaves))
      (is (some #(has-attr? % :data-on:blur) page-attrs))
      (is (some #(has-attr? % :data-on:change) page-attrs))
      (is (some #(has-attr? % :data-on:keydown) page-attrs))
      (doseq [command-name ["todo/rename-task"
                            "todo/assign-task-to-project"
                            "todo/remove-task-from-project"
                            "todo/set-task-due-within"
                            "todo/clear-task-due-within"]]
        (is (some #(string/includes? % command-name) page-attr-values)
            command-name))
      (is (not (some #(= "Edit details" %) page-leaves))))))

(deftest auth-interceptor-tests
  (testing "missing auth cookie leaves auth claims absent"
    (let [interceptor (auth/extract-auth-cookie-interceptor
                       {:verify-token (fn [_] {:user-id (random-uuid)})})
          result ((:enter interceptor) {:request {:cookies {}}})]
      (is (nil? (get-in result [:grain/additional-context :auth-claims])))))

  (testing "invalid token leaves auth claims absent"
    (let [interceptor (auth/extract-auth-cookie-interceptor
                       {:verify-token (fn [_] (throw (ex-info "invalid" {})))})
          result ((:enter interceptor)
                  {:request {:cookies {"auth-token" {:value "bad-token"}}}})]
      (is (nil? (get-in result [:grain/additional-context :auth-claims])))))

  (testing "valid token adds normalized auth claims and preserves existing context"
    (let [user-id (random-uuid)
          tenant-id (random-uuid)
          interceptor (auth/extract-auth-cookie-interceptor
                       {:verify-token (fn [token]
                                        (when (= "good-token" token)
                                          {:user-id (str user-id)
                                           :tenant-id (str tenant-id)
                                           :name "Example User"}))})
          result ((:enter interceptor)
                  {:request {:cookies {"auth-token" {:value "good-token"}}}
                   :grain/additional-context {:request-id "req-1"}})]
      (is (= "req-1" (get-in result [:grain/additional-context :request-id])))
      (is (= {:user-id user-id
              :tenant-id tenant-id
              :name "Example User"}
             (get-in result [:grain/additional-context :auth-claims])))))

  (testing "authenticated? follows Grain merged auth context"
    (is (auth/authenticated? {:auth-claims {:user-id (random-uuid)}}))
    (is (not (auth/authenticated? {})))))

(deftest datastar-dsl-migration-tests
  (testing "app source uses the checked Datastar DSL instead of removed v2/manual patterns"
    (let [source (str (slurp "src/cjbarre/grain_todo_list.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/foundation/ui.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/service/todo_list_service/commands.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/service/todo_list_service/queries.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/service/todo_list_service/read_models.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/service/todo_list_service/schemas.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/service/todo_list_service/ui.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/service/todo_list_service/ui/components.clj")
                      "\n"
                      (slurp "src/cjbarre/grain_todo_list/foundation/ui/components.clj"))
          deprecated-patterns ["ds/action-form"
                               "ds/bind"
                               "ds/signals"
                               "ds/on-command"
                               "ds/on-click-command"
                               "ds/show"
                               "ds/text"
                               "ds/expr"
                               "ds/lit"
                               "ds/assign"
                               "ds/with-scope"
                               "grain-datastar-v2"
                               "datastar_v2"
                               "ds/action-route"
                               "command-click"
                               "command-assignments"
                               "with-signal-scope"
                               "data-signals"
                               "signal-ref"
                               "@post('/actions')"
                               "data-on-submit__prevent"
                               "data-on-click"]]
      (doseq [pattern deprecated-patterns]
        (is (not (string/includes? source pattern)) pattern)))))
