(ns cjbarre.grain-todo-list.todo-list-service.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]
            [clojure.string :as string])
  (:import [java.time OffsetDateTime]))

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
   :todo/task-page [:map [:task-id :uuid]]
   :todo/projects-page [:map]
   :todo/project-page [:map [:project-id :uuid]]
   :todo/review-page [:map]
   :todo/dev-gallery-page [:map]})
