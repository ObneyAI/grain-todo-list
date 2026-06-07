(ns cjbarre.grain-todo-list.service.todo-list-service.ui
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.service.todo-list-service.ui.components :as tc]
            [cjbarre.grain-todo-list.foundation.ui :as app-ui]
            [cjbarre.grain-todo-list.foundation.ui.components :as c]))

(defn shell [{:keys [title]} & body]
  (apply app-ui/app-shell {:title title} body))

(defn home-page [{:keys [tasks due-soon inactive projects review]}]
  (shell {:title "Todo Workspace"}
         (app-ui/action-error)
         [:div {:class "grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]"}
          [:div {:class "grid gap-6"}
           (c/panel {:title "Tasks"
                     :status "Active work ordered in one list."}
                    (tc/quick-add {})
                    (c/page-section {:title "Active Tasks"
                                     :count (count tasks)
                                     :class "border-t border-base-content/10 pt-5"}
                                    (tc/task-list tasks "No active tasks." projects)))]
          [:aside {:class "grid content-start gap-6"}
           (c/panel {:title "Projects"
                     :count (count projects)}
                    (tc/project-add)
                    (tc/projects-list projects))
           [:a {:class "block focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                :href (ds-ui/href :todo/review-page)
                :aria-label "Weekly review"}
            (c/panel {:title "Weekly Review"
                      :status (if (= :active (:status review))
                                "A weekly review is active."
                                "No active weekly review.")})]
           (c/panel {:title "Planning"
                     :status "Scheduled and recently closed work."}
                    (tc/planning-summary {:due-soon due-soon
                                         :inactive inactive
                                         :projects projects}))]]))

(defn tasks-page [{:keys [tasks projects]}]
  (shell {:title "Tasks"}
         (app-ui/action-error)
         [:div {:class "mb-6"} (tc/quick-add {})]
         (tc/task-list tasks "No active tasks." projects)))

(defn task-page [{:keys [task projects]}]
  (shell {:title (or (:title task) "Task")}
         (app-ui/action-error)
         (if task
           (tc/task-detail-panel task projects)
           [:div {:class "alert alert-warning"} "Task not found."])))

(defn projects-page [{:keys [projects]}]
  (shell {:title "Projects"}
         (app-ui/action-error)
         [:div {:class "mb-6"} (tc/project-add)]
         (tc/projects-list projects)))

(defn project-page [{:keys [project tasks projects]}]
  (shell {:title (or (:name project) "Project")}
         (app-ui/action-error)
         (if project
           [:div {:class "space-y-6"}
            (c/surface {:tag :section :variant :card}
             [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
              [:div {:class "min-w-0 space-y-2"}
               (tc/project-editor project)
               (tc/task-count-row (:task-counts project))]
              (tc/project-actions project)]
            [:p {:class "mt-4 text-sm text-base-content/70"}
              (str "Status: " (clojure.core/name (:status project)))])
            (when (= :active (:status project))
              (tc/quick-add {:project-id (:project-id project)}))
            (tc/task-list tasks "No active tasks for this project." projects)]
           [:div {:class "alert alert-warning"} "Project not found."])))

(defn review-page [{:keys [tasks due-soon inactive projects review-projects projects-without-next-action review]}]
  (shell {:title "Weekly Review"}
         (app-ui/action-error)
         [:section {:class "space-y-4"}
          (if (= :active (:status review))
            (let [reviewed-task-ids (:reviewed-task-ids review)
                  reviewed-project-ids (:reviewed-project-ids review)
                  review-tasks (or tasks [])
                  review-projects (or review-projects projects)
                  all-tasks-reviewed? (every? reviewed-task-ids (map :task-id review-tasks))
                  all-projects-reviewed? (every? reviewed-project-ids (map :project-id review-projects))
                  review-complete? (and all-tasks-reviewed? all-projects-reviewed?)]
              [:div {:class "space-y-8"}
               (c/surface {:variant :card}
                [:p {:class "text-sm font-medium"} "Review each active task and active project."]
                [:div {:class "mt-3 flex flex-wrap gap-2 text-xs text-base-content/70"}
                 (c/badge {:label (str (count reviewed-task-ids) "/" (count review-tasks) " tasks reviewed")})
                 (c/badge {:label (str (count reviewed-project-ids) "/" (count review-projects) " projects reviewed")})])
               [:div {:class "grid gap-8 xl:grid-cols-2"}
                (c/page-section {:title "Active Tasks"
                                 :count (count review-tasks)
                                 :status "Review each active task for relevance and next steps."}
                                (tc/review-task-list review reviewed-task-ids review-tasks projects))]
               (c/page-section {:title "Due Soon" :count (count due-soon)}
                               (tc/task-summary-list due-soon "No due dates yet." projects))
               (c/page-section {:title "Projects Needing Tasks"
                                :count (count projects-without-next-action)
                                :status "These active projects may need another task."}
                               (tc/project-summary-list projects-without-next-action "Every active project has an active task."))
               (c/page-section {:title "Active Projects"
                                :count (count review-projects)
                                :status "Review each active project for stale outcomes and open tasks."}
                               (tc/review-project-list review reviewed-project-ids review-projects))
               (c/page-section {:title "Done / Canceled" :count (count inactive)}
                               (tc/task-summary-list inactive "No recently completed or canceled tasks." projects))
               (c/action-button {:label "Complete review"
                                 :class "btn-primary"
                                 :disabled? (not review-complete?)
                                 :on-click-attrs {:on/click
                                                  {:effect
                                                   (ds-ui/dispatch
                                                    :todo/complete-weekly-review
                                                    {:review-id (:review-id review)})}}})])
            (c/action-button {:label "Start weekly review"
                              :class "btn-primary"
                              :on-click-attrs {:on/click
                                               {:effect
                                                (ds-ui/dispatch
                                                 :todo/start-weekly-review
                                                 {})}}}))]))

(defn dev-gallery-page []
  (tc/dev-gallery-page))
