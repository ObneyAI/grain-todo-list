(ns cjbarre.grain-todo-list.todo-list-service.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]])
  (:import [java.time OffsetDateTime]))

(def task-event-types
  #{:todo/task-captured
    :todo/task-renamed
    :todo/task-assigned-to-project
    :todo/task-removed-from-project
    :todo/task-due-within-set
    :todo/task-due-within-cleared
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
    :todo/task-reviewed
    :todo/weekly-review-completed})

(defmulti tasks* (fn [_state event] (:event/type event)))

(defmethod tasks* :todo/task-captured
  [state {:keys [task-id title status order project-id due-within-days due-within-set-at]}]
  (assoc state task-id
         (cond-> {:task-id task-id
                  :title title
                  :status status
                  :order order}
           project-id (assoc :project-id project-id)
           due-within-days (assoc :due-within-days due-within-days)
           due-within-set-at (assoc :due-within-set-at due-within-set-at))))

(defmethod tasks* :todo/task-renamed
  [state {:keys [task-id title]}]
  (assoc-in state [task-id :title] title))

(defmethod tasks* :todo/task-assigned-to-project
  [state {:keys [task-id project-id]}]
  (assoc-in state [task-id :project-id] project-id))

(defmethod tasks* :todo/task-removed-from-project
  [state {:keys [task-id]}]
  (update state task-id dissoc :project-id))

(defmethod tasks* :todo/task-due-within-set
  [state {:keys [task-id due-within-days due-within-set-at]}]
  (-> state
      (assoc-in [task-id :due-within-days] due-within-days)
      (assoc-in [task-id :due-within-set-at] due-within-set-at)))

(defmethod tasks* :todo/task-due-within-cleared
  [state {:keys [task-id]}]
  (update state task-id dissoc :due-within-days :due-within-set-at))

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
   :reviewed-task-ids #{}})

(defmethod weekly-review* :todo/project-reviewed
  [state {:keys [project-id]}]
  (update state :reviewed-project-ids (fnil conj #{}) project-id))

(defmethod weekly-review* :todo/task-reviewed
  [state {:keys [task-id]}]
  (update state :reviewed-task-ids (fnil conj #{}) task-id))

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

(defn ordered-tasks
  [tasks]
  (sort-by (juxt :order :title :task-id) tasks))

(defn active-tasks
  [ctx]
  (->> (vals (all-tasks ctx))
       (filter active-task?)
       ordered-tasks))

(defn effective-due-time
  [{:keys [due-within-days due-within-set-at]}]
  (when (and due-within-days due-within-set-at)
    (.plusDays ^OffsetDateTime due-within-set-at due-within-days)))

(defn due-soon-tasks
  [ctx]
  (->> (vals (all-tasks ctx))
       (filter active-task?)
       (filter effective-due-time)
       (sort-by (juxt effective-due-time :order :title :task-id))))

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
