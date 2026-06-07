(ns cjbarre.grain-todo-list.service.todo-list-service.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]
            [clojure.set :as set]
            [cognitect.anomalies :as anom])
  (:import [java.time OffsetDateTime]))

(defn anomaly
  [category message]
  {::anom/category category ::anom/message message})

(defn not-found [message] (anomaly ::anom/not-found message))
(defn conflict [message] (anomaly ::anom/conflict message))
(defn incorrect [message] (anomaly ::anom/incorrect message))

(defn current-user-id
  [ctx]
  (:current-user/id ctx))

(defn authenticated?
  [ctx]
  (some? (current-user-id ctx)))

(defn owns-task?
  [ctx task-id]
  (boolean (and (authenticated? ctx)
                task-id
                (get (rm/all-tasks ctx) task-id))))

(defn owns-project?
  [ctx project-id]
  (boolean (and (authenticated? ctx)
                project-id
                (get (rm/all-projects ctx) project-id))))

(defn owns-review?
  [ctx review-id]
  (boolean (and (authenticated? ctx)
                review-id
                (= review-id (:review-id (rm/current-weekly-review ctx))))))

(defn can-capture-task?
  [{{:keys [project-id]} :command :as ctx}]
  (and (authenticated? ctx)
       (or (nil? project-id)
           (owns-project? ctx project-id))))

(defn can-use-task?
  [{{:keys [task-id]} :command :as ctx}]
  (owns-task? ctx task-id))

(defn can-assign-task-to-project?
  [{{:keys [task-id project-id]} :command :as ctx}]
  (and (owns-task? ctx task-id)
       (owns-project? ctx project-id)))

(defn can-use-project?
  [{{:keys [project-id]} :command :as ctx}]
  (owns-project? ctx project-id))

(defn can-use-review?
  [{{:keys [review-id]} :command :as ctx}]
  (owns-review? ctx review-id))

(defn can-review-project?
  [{{:keys [review-id project-id]} :command :as ctx}]
  (and (owns-review? ctx review-id)
       (owns-project? ctx project-id)))

(defn can-review-task?
  [{{:keys [review-id task-id]} :command :as ctx}]
  (and (owns-review? ctx review-id)
       (owns-task? ctx task-id)))

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
  {:authorized? can-capture-task?}
  "Add a new task."
  [{{:keys [task-id title project-id due-within-days order]} :command :as ctx}]
  (let [task-id (or task-id (random-uuid))
        user-id (current-user-id ctx)
        due-within-set-at (when due-within-days (OffsetDateTime/now))
        project (when project-id (require-project ctx project-id))]
    (cond
      (contains? project ::anom/category) project
      (and project (not= :active (:status project))) (conflict "Cannot assign a task to an inactive project.")
      :else
      (let [order (or order (next-order (rm/active-tasks ctx)))]
        {:command-result/events
         [(->event {:type :todo/task-captured
                    :tags #{[:task task-id] [:user user-id]}
                    :body (cond-> {:user-id user-id
                                   :task-id task-id
                                   :title title
                                   :status :active
                                   :order order}
                            project-id (assoc :project-id project-id)
                            due-within-days (assoc :due-within-days due-within-days
                                                   :due-within-set-at due-within-set-at))})]
         :datastar/signals {:__toast "Task added."}}))))

(defcommand :todo rename-task
  {:authorized? can-use-task?}
  [{{:keys [task-id title]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-renamed
                  :tags #{[:task task-id] [:user user-id]}
                  :body {:user-id user-id :task-id task-id :title title}})]
       :datastar/signals {:__toast "Task renamed."}})))

(defcommand :todo assign-task-to-project
  {:authorized? can-assign-task-to-project?}
  [{{:keys [task-id project-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        project (require-project ctx project-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (contains? project ::anom/category) project
      (not= :active (:status task)) (conflict "Only active tasks can be assigned.")
      (not= :active (:status project)) (conflict "Cannot assign to an inactive project.")
      :else
      {:command-result/events
       [(->event {:type :todo/task-assigned-to-project
                  :tags #{[:task task-id] [:user user-id]}
                  :body {:user-id user-id :task-id task-id :project-id project-id}})]})))

(defcommand :todo remove-task-from-project
  {:authorized? can-use-task?}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (not (:project-id task)) (conflict "Task is not assigned to a project.")
      :else
      {:command-result/events
       [(->event {:type :todo/task-removed-from-project
                  :tags #{[:task task-id] [:user user-id]}
                  :body {:user-id user-id :task-id task-id :project-id (:project-id task)}})]})))

(defcommand :todo set-task-due-within
  {:authorized? can-use-task?}
  [{{:keys [task-id due-within-days]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-due-within-set
                  :tags #{[:task task-id] [:user user-id]}
                  :body {:user-id user-id
                         :task-id task-id
                         :due-within-days due-within-days
                         :due-within-set-at (OffsetDateTime/now)}})]})))

(defcommand :todo clear-task-due-within
  {:authorized? can-use-task?}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (if (contains? task ::anom/category)
      task
      {:command-result/events
       [(->event {:type :todo/task-due-within-cleared
                  :tags #{[:task task-id] [:user user-id]}
                  :body {:user-id user-id :task-id task-id}})]})))

(defcommand :todo complete-task
  {:authorized? can-use-task?}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (not= :active (:status task)) (conflict "Only active tasks can be completed.")
      :else {:command-result/events
             [(->event {:type :todo/task-completed
                        :tags #{[:task task-id] [:user user-id]}
                        :body {:user-id user-id :task-id task-id}})]
             :datastar/signals {:__toast "Task completed."}})))

(defcommand :todo archive-task
  {:authorized? can-use-task?}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (not= :completed (:status task)) (conflict "Only completed tasks can be archived.")
      :else {:command-result/events
             [(->event {:type :todo/task-archived
                        :tags #{[:task task-id] [:user user-id]}
                        :body {:user-id user-id :task-id task-id}})]})))

(defcommand :todo cancel-task
  {:authorized? can-use-task?}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (#{:canceled :archived} (:status task)) (conflict "Task is already inactive.")
      :else {:command-result/events
             [(->event {:type :todo/task-canceled
                        :tags #{[:task task-id] [:user user-id]}
                        :body {:user-id user-id :task-id task-id}})]})))

(defcommand :todo reactivate-task
  {:authorized? can-use-task?}
  [{{:keys [task-id]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (not (#{:completed :canceled} (:status task))) (conflict "Only completed or canceled tasks can be reactivated.")
      :else {:command-result/events
             [(->event {:type :todo/task-reactivated
                        :tags #{[:task task-id] [:user user-id]}
                        :body {:user-id user-id :task-id task-id}})]})))

(defcommand :todo reorder-task
  {:authorized? can-use-task?}
  [{{:keys [task-id order]} :command :as ctx}]
  (let [task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (not= :active (:status task)) (conflict "Only active tasks can be reordered.")
      :else {:command-result/events
             [(->event {:type :todo/task-reordered
                        :tags #{[:task task-id] [:user user-id]}
                        :body {:user-id user-id :task-id task-id :order order}})]})))

(defcommand :todo create-project
  {:authorized? authenticated?}
  [{{:keys [project-id name]} :command :as ctx}]
  (let [project-id (or project-id (random-uuid))
        user-id (current-user-id ctx)]
    {:command-result/events
     [(->event {:type :todo/project-created
                :tags #{[:project project-id] [:user user-id]}
                :body {:user-id user-id :project-id project-id :name name :status :active}})]
     :datastar/signals {:__toast "Project created."}}))

(defcommand :todo rename-project
  {:authorized? can-use-project?}
  [{{:keys [project-id name]} :command :as ctx}]
  (let [project (require-project ctx project-id)
        user-id (current-user-id ctx)]
    (if (contains? project ::anom/category)
      project
      {:command-result/events
       [(->event {:type :todo/project-renamed
                  :tags #{[:project project-id] [:user user-id]}
                  :body {:user-id user-id :project-id project-id :name name}})]})))

(defcommand :todo complete-project
  {:authorized? can-use-project?}
  [{{:keys [project-id]} :command :as ctx}]
  (let [project (require-project ctx project-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? project ::anom/category) project
      (not= :active (:status project)) (conflict "Only active projects can be completed.")
      :else {:command-result/events
             [(->event {:type :todo/project-completed
                        :tags #{[:project project-id] [:user user-id]}
                        :body {:user-id user-id :project-id project-id}})]})))

(defcommand :todo cancel-project
  {:authorized? can-use-project?}
  [{{:keys [project-id]} :command :as ctx}]
  (let [project (require-project ctx project-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? project ::anom/category) project
      (not= :active (:status project)) (conflict "Only active projects can be canceled.")
      :else {:command-result/events
             [(->event {:type :todo/project-canceled
                        :tags #{[:project project-id] [:user user-id]}
                        :body {:user-id user-id :project-id project-id}})]})))

(defcommand :todo reactivate-project
  {:authorized? can-use-project?}
  [{{:keys [project-id]} :command :as ctx}]
  (let [project (require-project ctx project-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? project ::anom/category) project
      (not (#{:completed :canceled} (:status project))) (conflict "Only inactive projects can be reactivated.")
      :else {:command-result/events
             [(->event {:type :todo/project-reactivated
                        :tags #{[:project project-id] [:user user-id]}
                        :body {:user-id user-id :project-id project-id}})]})))

(defcommand :todo start-weekly-review
  {:authorized? authenticated?}
  [{{:keys [review-id]} :command :as ctx}]
  (let [current (rm/current-weekly-review ctx)]
    (cond
      (= :active (:status current)) (conflict "A weekly review is already active.")
      :else
      (let [review-id (or review-id (random-uuid))
            user-id (current-user-id ctx)
            started-at (OffsetDateTime/now)]
        {:command-result/events
         [(->event {:type :todo/weekly-review-started
                    :tags #{[:review review-id] [:user user-id]}
                    :body {:user-id user-id :review-id review-id :started-at started-at}})]}))))

(defcommand :todo mark-project-reviewed
  {:authorized? can-review-project?}
  [{{:keys [review-id project-id]} :command :as ctx}]
  (let [review (rm/current-weekly-review ctx)
        project (require-project ctx project-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? project ::anom/category) project
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      (not= :active (:status project)) (conflict "Only active projects can be reviewed.")
      :else
      {:command-result/events
       [(->event {:type :todo/project-reviewed
                  :tags #{[:review review-id] [:user user-id]}
                  :body {:user-id user-id :review-id review-id :project-id project-id}})]})))

(defcommand :todo mark-task-reviewed
  {:authorized? can-review-task?}
  [{{:keys [review-id task-id]} :command :as ctx}]
  (let [review (rm/current-weekly-review ctx)
        task (require-task ctx task-id)
        user-id (current-user-id ctx)]
    (cond
      (contains? task ::anom/category) task
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      (not= :active (:status task)) (conflict "Only active tasks can be reviewed.")
      :else
      {:command-result/events
       [(->event {:type :todo/task-reviewed
                  :tags #{[:review review-id] [:user user-id]}
                  :body {:user-id user-id :review-id review-id :task-id task-id}})]})))

(defcommand :todo complete-weekly-review
  {:authorized? can-use-review?}
  [{{:keys [review-id]} :command :as ctx}]
  (let [review (rm/current-weekly-review ctx)
        active-task-ids (set (map :task-id (rm/active-tasks ctx)))
        active-project-ids (set (map :project-id (rm/active-projects ctx)))
        user-id (current-user-id ctx)]
    (cond
      (not= review-id (:review-id review)) (conflict "Review is not active.")
      (not= :active (:status review)) (conflict "Review is not active.")
      (not (set/subset? active-task-ids (:reviewed-task-ids review))) (conflict "All active tasks must be reviewed.")
      (not (set/subset? active-project-ids (:reviewed-project-ids review))) (conflict "All active projects must be reviewed.")
      :else
      {:command-result/events
       [(->event {:type :todo/weekly-review-completed
                  :tags #{[:review review-id] [:user user-id]}
                  :body {:user-id user-id
                         :review-id review-id
                         :completed-at (OffsetDateTime/now)}})]})))
