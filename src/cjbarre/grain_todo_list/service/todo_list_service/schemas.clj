(ns cjbarre.grain-todo-list.service.todo-list-service.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]
            [clojure.string :as string]))

(def task-statuses #{:active :completed :canceled :archived})
(def project-statuses #{:active :completed :canceled})

(defn non-blank-string?
  [x]
  (and (string? x) (seq (string/trim x))))

(defschemas common-schemas
  {::non-blank-string [:fn {:error/message "Must not be blank"} non-blank-string?]
   ::task-status [:enum :active :completed :canceled :archived]
   ::project-status [:enum :active :completed :canceled]
   ::review-status [:enum :active :completed]
   ::order number?
   ::task-counts [:map
                  [:active nat-int?]
                  [:completed nat-int?]
                  [:canceled {:optional true} nat-int?]
                  [:archived {:optional true} nat-int?]]
   ::task [:map
           [:user-id :uuid]
           [:task-id :uuid]
           [:title ::non-blank-string]
           [:status ::task-status]
           [:order ::order]
           [:project-id {:optional true} :uuid]
           [:due-within-days {:optional true} nat-int?]
           [:due-within-set-at {:optional true} :time/offset-date-time]]
   ::project [:map
              [:user-id :uuid]
              [:project-id :uuid]
              [:name ::non-blank-string]
              [:status ::project-status]
              [:task-counts {:optional true} ::task-counts]]
   ::weekly-review [:map
                    [:user-id :uuid]
                    [:review-id :uuid]
                    [:status ::review-status]
                    [:started-at :time/offset-date-time]
                    [:reviewed-project-ids [:set :uuid]]
                    [:reviewed-task-ids [:set :uuid]]
                    [:completed-at {:optional true} :time/offset-date-time]]})

(defschemas event-schemas
  {
   :todo/task-captured
   ::task
   :todo/task-renamed [:map [:user-id :uuid] [:task-id :uuid] [:title ::non-blank-string]]
   :todo/task-assigned-to-project [:map [:user-id :uuid] [:task-id :uuid] [:project-id :uuid]]
   :todo/task-removed-from-project [:map [:user-id :uuid] [:task-id :uuid] [:project-id :uuid]]
   :todo/task-due-within-set [:map
                              [:user-id :uuid]
                              [:task-id :uuid]
                              [:due-within-days nat-int?]
                              [:due-within-set-at :time/offset-date-time]]
   :todo/task-due-within-cleared [:map [:user-id :uuid] [:task-id :uuid]]
   :todo/task-completed [:map [:user-id :uuid] [:task-id :uuid]]
   :todo/task-archived [:map [:user-id :uuid] [:task-id :uuid]]
   :todo/task-canceled [:map [:user-id :uuid] [:task-id :uuid]]
   :todo/task-reactivated [:map [:user-id :uuid] [:task-id :uuid]]
   :todo/task-reordered [:map [:user-id :uuid] [:task-id :uuid] [:order ::order]]

   :todo/project-created [:map [:user-id :uuid] [:project-id :uuid] [:name ::non-blank-string] [:status ::project-status]]
   :todo/project-renamed [:map [:user-id :uuid] [:project-id :uuid] [:name ::non-blank-string]]
   :todo/project-completed [:map [:user-id :uuid] [:project-id :uuid]]
   :todo/project-canceled [:map [:user-id :uuid] [:project-id :uuid]]
   :todo/project-reactivated [:map [:user-id :uuid] [:project-id :uuid]]

   :todo/weekly-review-started [:map [:user-id :uuid] [:review-id :uuid] [:started-at :time/offset-date-time]]
   :todo/project-reviewed [:map [:user-id :uuid] [:review-id :uuid] [:project-id :uuid]]
   :todo/task-reviewed [:map [:user-id :uuid] [:review-id :uuid] [:task-id :uuid]]
   :todo/weekly-review-completed [:map [:user-id :uuid] [:review-id :uuid] [:completed-at :time/offset-date-time]]})

(defschemas command-schemas
  {
   :todo/capture-task
   [:map [:title ::non-blank-string]
    [:task-id {:optional true} :uuid]
    [:project-id {:optional true} :uuid]
    [:due-within-days {:optional true} nat-int?]
    [:order {:optional true} ::order]]
   :todo/rename-task [:map [:task-id :uuid] [:title ::non-blank-string]]
   :todo/assign-task-to-project [:map [:task-id :uuid] [:project-id :uuid]]
   :todo/remove-task-from-project [:map [:task-id :uuid]]
   :todo/set-task-due-within [:map [:task-id :uuid] [:due-within-days nat-int?]]
   :todo/clear-task-due-within [:map [:task-id :uuid]]
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
   :todo/mark-task-reviewed [:map [:review-id :uuid] [:task-id :uuid]]
   :todo/complete-weekly-review [:map [:review-id :uuid]]})

(defschemas query-schemas
  {
   :todo/home-page [:map]
   :todo/tasks-page [:map]
   :todo/task-page [:map [:task-id :uuid]]
   :todo/projects-page [:map]
   :todo/project-page [:map [:project-id :uuid]]
   :todo/review-page [:map]
   :todo/dev-gallery-page [:map]})

(defschemas read-model-schemas
  {:todo/tasks [:map-of :uuid ::task]
   :todo/projects [:map-of :uuid ::project]
   :todo/weekly-review [:maybe ::weekly-review]})
