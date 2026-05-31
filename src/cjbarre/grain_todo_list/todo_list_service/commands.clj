(ns cjbarre.grain-todo-list.todo-list-service.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [cjbarre.grain-todo-list.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.todo-list-service.schemas :as schemas]
            [clojure.set :as set]
            [cognitect.anomalies :as anom])
  (:import [java.time OffsetDateTime]))

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
  (or (get (rm/all-tasks ctx) task-id)
      (not-found "Task not found.")))

(defn require-project
  [ctx project-id]
  (or (get (rm/all-projects ctx) project-id)
      (not-found "Project not found.")))

(defcommand :todo capture-task
  {:authorized? (constantly true)}
  "Capture a new task."
  [{{:keys [task-id title bucket project-id due-at defer-until order]} :command :as ctx}]
  (let [task-id (or task-id (random-uuid))
        bucket (or bucket :inbox)
        due-at (some-> due-at schemas/coerce-offset-date-time)
        defer-until (some-> defer-until schemas/coerce-offset-date-time)
        project (when project-id (require-project ctx project-id))]
    (cond
      (contains? project ::anom/category) project
      (and project (not= :active (:status project))) (conflict "Cannot assign a task to an inactive project.")
      :else
      (let [order (or order (next-order (rm/tasks-for-bucket ctx bucket)))]
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
         :datastar/signals {:__toast "Task captured."}}))))

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
      (let [order (or order (next-order (rm/tasks-for-bucket ctx bucket)))]
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
        due-at (schemas/coerce-offset-date-time due-at)]
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
        defer-until (schemas/coerce-offset-date-time defer-until)]
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
     :datastar/signals {:__toast "Project created."}}))

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
  (let [current (rm/current-weekly-review ctx)]
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
  (let [review (rm/current-weekly-review ctx)
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
  (let [review (rm/current-weekly-review ctx)]
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
  (let [review (rm/current-weekly-review ctx)
        active-project-ids (set (map :project-id (rm/active-projects ctx)))]
    (cond
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      (not (set/subset? schemas/buckets (:reviewed-buckets review))) (conflict "All buckets must be reviewed.")
      (not (set/subset? active-project-ids (:reviewed-project-ids review))) (conflict "All active projects must be reviewed.")
      :else
      {:command-result/events
       [(->event {:type :todo/weekly-review-completed
                  :tags #{[:review review-id]}
                  :body {:review-id review-id :completed-at (OffsetDateTime/now)}})]})))
