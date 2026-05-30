(ns cjbarre.grain-todo-list.ui
  (:require [cjbarre.grain-todo-list.ui.components :as c]))

(defn shell [{:keys [title]} & body]
  (apply c/app-shell {:title title} body))

(defn home-page [{:keys [buckets deferred due-soon inactive projects review]}]
  (shell {:title "GTD Workspace"}
         (c/action-error)
         [:div {:class "mb-8 grid gap-4 lg:grid-cols-[2fr_1fr]"}
          (c/quick-add {:bucket :inbox})
          (c/project-add)]
         [:div {:class "grid gap-8 lg:grid-cols-[2fr_1fr]"}
          [:div {:class "space-y-8"}
           (for [bucket [:inbox :next :waiting :someday]]
             (with-meta (c/bucket-section bucket (get buckets bucket) projects) {:key bucket}))
           (c/page-section {:title "Deferred" :count (count deferred)}
                           (c/task-list deferred "No deferred tasks." projects))
           (c/page-section {:title "Due Soon" :count (count due-soon)}
                           (c/due-soon-list due-soon projects))
           (c/page-section {:title "Done / Canceled" :count (count inactive)}
                           (c/task-list inactive "No completed or canceled tasks." projects))]
          [:aside {:class "space-y-6"}
           (c/page-section {:title "Projects" :count (count projects)}
                           (c/projects-list projects))
           [:a {:class "block rounded-box focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                :href "/review"
                :aria-label "Weekly review"}
            (c/page-section {:title "Weekly Review"
                             :status (if (= :active (:status review))
                                       "A weekly review is active."
                                       "No active weekly review.")
                             :class (c/surface-class :card nil)})]]]))

(defn tasks-page [{:keys [bucket tasks projects]}]
  (shell {:title (str (get c/bucket-labels bucket "Tasks") " Tasks")}
         (c/action-error)
         [:div {:class "mb-6"} (c/quick-add {:bucket bucket})]
         (c/task-list tasks "No tasks in this bucket." projects)))

(defn task-page [{:keys [task projects]}]
  (shell {:title (or (:title task) "Task")}
         (c/action-error)
         (if task
           (c/task-detail-panel task projects)
           [:div {:class "alert alert-warning"} "Task not found."])))

(defn projects-page [{:keys [projects]}]
  (shell {:title "Projects"}
         (c/action-error)
         [:div {:class "mb-6"} (c/project-add)]
         (c/projects-list projects)))

(defn project-page [{:keys [project tasks projects]}]
  (shell {:title (or (:name project) "Project")}
         (c/action-error)
         (if project
           [:div {:class "space-y-6"}
            (c/surface {:tag :section :variant :card}
             [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
              [:div {:class "min-w-0 space-y-2"}
               (c/project-editor project)
               (c/task-count-row (:task-counts project))]
              (c/project-actions project)]
             [:p {:class "mt-4 text-sm text-base-content/70"}
              (str "Status: " (clojure.core/name (:status project)))])
            (when (= :active (:status project))
              (c/quick-add {:bucket :next :project-id (:project-id project)}))
            (c/task-list tasks "No active tasks for this project." projects)]
           [:div {:class "alert alert-warning"} "Project not found."])))

(defn review-page [{:keys [buckets deferred due-soon inactive projects review-projects projects-without-next-action review]}]
  (shell {:title "Weekly Review"}
         (c/action-error)
         [:section {:class "space-y-4"}
          (if (= :active (:status review))
            (let [reviewed-buckets (:reviewed-buckets review)
                  reviewed-project-ids (:reviewed-project-ids review)
                  review-projects (or review-projects projects)
                  all-buckets-reviewed? (every? reviewed-buckets [:inbox :next :waiting :someday])
                  all-projects-reviewed? (every? reviewed-project-ids (map :project-id review-projects))
                  review-complete? (and all-buckets-reviewed? all-projects-reviewed?)]
              [:div {:class "space-y-8"}
               (c/surface {:variant :card}
                [:p {:class "text-sm font-medium"} "Review each bucket and active project."]
                [:div {:class "mt-3 flex flex-wrap gap-2 text-xs text-base-content/70"}
                 (c/badge {:label (str (count reviewed-buckets) "/4 buckets reviewed")})
                 (c/badge {:label (str (count reviewed-project-ids) "/" (count review-projects) " projects reviewed")})])
               [:div {:class "grid gap-8 xl:grid-cols-2"}
                (for [bucket [:inbox :next :waiting :someday]
                      :let [tasks (get buckets bucket)
                            reviewed? (contains? reviewed-buckets bucket)]]
                  (c/page-section {:title (get c/bucket-labels bucket)
                                   :count (count tasks)
                                   :status (if reviewed? "This bucket has been reviewed." "Inspect these tasks, then mark the bucket reviewed.")
                                   :action (c/review-bucket-action review bucket reviewed?)}
                                  (c/task-summary-list tasks "Nothing here." projects)))]
               (c/page-section {:title "Due Soon" :count (count due-soon)}
                               (c/task-summary-list due-soon "No due dates yet." projects))
               (c/page-section {:title "Deferred" :count (count deferred)}
                               (c/task-summary-list deferred "No deferred tasks." projects))
               (c/page-section {:title "Projects Without Next Actions"
                                :count (count projects-without-next-action)
                                :status "These active projects may need a next action."}
                               (c/project-summary-list projects-without-next-action "Every active project has an active task."))
               (c/page-section {:title "Active Projects"
                                :count (count review-projects)
                                :status "Review each active project for stale outcomes and next actions."}
                               (c/review-project-list review reviewed-project-ids review-projects))
               (c/page-section {:title "Done / Canceled" :count (count inactive)}
                               (c/task-summary-list inactive "No recently completed or canceled tasks." projects))
               (c/action-button {:label "Complete review"
                                 :class "btn-primary"
                                 :disabled? (not review-complete?)
                                 :on-click (c/command-click "todo/complete-weekly-review"
                                                            {:review-id (:review-id review)})})])
            (c/action-button {:label "Start weekly review"
                              :class "btn-primary"
                              :on-click "$['command/name'] = 'todo/start-weekly-review'; @post('/actions');"}))]))

(defn dev-gallery-page []
  (c/dev-gallery-page))
