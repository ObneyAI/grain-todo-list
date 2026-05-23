(ns cjbarre.grain-todo-list.ui
  (:require [clojure.string :as string]))

(def bucket-labels
  {:inbox "Inbox"
   :next "Next"
   :waiting "Waiting"
   :someday "Someday"})

(defn signal-ref
  [signal-name]
  (if (re-find #"[^a-zA-Z0-9_]" signal-name)
    (str "$['" signal-name "']")
    (str "$" signal-name)))

(defn ds-str [value]
  (str "'" (string/replace (str (or value "")) "'" "\\\\'") "'"))

(defn ds-assign
  [signal-name value-expr]
  (str (signal-ref signal-name) " = " value-expr))

(defn command-click
  [command assignments]
  (string/join
   " "
   (concat [(str (ds-assign "command/name" (ds-str command)) ";")]
           (map (fn [[k v]]
                  (str (ds-assign (name k) (ds-str v)) ";"))
                assignments)
           ["@post('/actions');"])))

(defn signal-suffix
  [value]
  (string/replace (str value) #"[^a-zA-Z0-9_]" "_"))

(defn signal-map
  [entries]
  (str "{"
       (string/join ", "
                    (map (fn [[k v]]
                           (str "'" k "': " (ds-str v)))
                         entries))
       "}"))

(defn date-value
  [value]
  (some-> value str (subs 0 10)))

(defn offset-from-date-expr
  [signal-name]
  (str "new Date(" (signal-ref signal-name) " + 'T00:00:00').toISOString()"))

(defn shell [{:keys [title]} & body]
  [:div#app
   [:main {:class "min-h-screen bg-base-100 text-base-content"}
    [:div {:class "mx-auto max-w-6xl px-4 py-8"}
     [:header {:class "mb-8 flex flex-col gap-2 border-b border-base-300 pb-6"}
      [:p {:class "text-sm font-medium uppercase tracking-wide text-primary"} "Grain Todo"]
      [:h1 {:class "text-3xl font-semibold leading-tight"} title]
      [:p {:class "max-w-2xl text-sm text-base-content/70"}
       "A personal GTD workspace backed by Grain events and Datastar updates."]]
     body]]])

(defn action-error []
  [:div {:data-show "$error" :class "alert alert-error mb-4"}
   [:span {:data-text "$error"}]])

(defn quick-add [{:keys [bucket project-id]}]
  [:form {:class "flex flex-col gap-3 rounded-box border border-base-300 bg-base-100 p-4 shadow-sm sm:flex-row"
          :data-signals (str "{'title': '', 'bucket': '" (name (or bucket :inbox)) "'"
                             (when project-id (str ", 'project-id': '" project-id "'"))
                             "}")
          :data-on:submit__prevent "$['command/name'] = 'todo/capture-task'; @post('/actions')"}
   [:input {:class "input input-bordered min-w-0 flex-1"
            :placeholder "Capture a task"
            :data-bind "title"
            :required true}]
   [:select {:class "select select-bordered sm:w-40" :data-bind "bucket"}
    (for [[value label] bucket-labels]
      [:option {:key value :value (name value)} label])]
   [:button {:class "btn btn-primary" :type "submit"} "Add"]])

(defn project-add []
  [:form {:class "flex flex-col gap-3 rounded-box border border-base-300 bg-base-100 p-4 shadow-sm sm:flex-row"
          :data-signals "{'projectName': ''}"
          :data-on:submit__prevent "$['command/name'] = 'todo/create-project'; $name = $projectName; @post('/actions')"}
   [:input {:class "input input-bordered min-w-0 flex-1"
            :placeholder "New project"
            :data-bind "projectName"
            :required true}]
   [:button {:class "btn btn-outline" :type "submit"} "Create project"]])

(defn task-actions [{:keys [task-id status]}]
  [:div {:class "flex flex-wrap gap-2"}
   (when (= :active status)
     [:button {:class "btn btn-xs btn-success"
               :data-on:click (command-click "todo/complete-task" {:task-id task-id})}
      "Done"])
   (when (= :completed status)
     [:button {:class "btn btn-xs btn-outline"
               :data-on:click (command-click "todo/archive-task" {:task-id task-id})}
      "Archive"])
   (when (#{:completed :canceled} status)
     [:button {:class "btn btn-xs btn-outline"
               :data-on:click (command-click "todo/reactivate-task" {:task-id task-id})}
      "Reactivate"])
   (when (not= :archived status)
     [:button {:class "btn btn-xs btn-ghost text-error"
               :data-on:click (command-click "todo/cancel-task" {:task-id task-id})}
      "Cancel"])])

(defn bucket-move-controls [{:keys [task-id bucket]}]
  [:div {:class "flex flex-wrap gap-1"}
   (for [[value label] bucket-labels
         :when (not= value bucket)]
     [:button {:key value
               :class "btn btn-xs btn-ghost"
               :data-on:click (command-click "todo/move-task-to-bucket"
                                             {:task-id task-id :bucket (name value)})}
      label])])

(defn task-editor [{:keys [task-id title due-at defer-until project-id status]} projects]
  (let [suffix (signal-suffix task-id)
        title-signal (str "title_" suffix)
        project-signal (str "project_" suffix)
        due-signal (str "due_" suffix)
        defer-signal (str "defer_" suffix)
        project-options (remove #(= project-id (:project-id %)) projects)]
    [:details {:class "rounded border border-base-300 bg-base-200/30 px-3 py-2"
               :data-signals (signal-map [[title-signal title]
                                          [project-signal ""]
                                          [due-signal (date-value due-at)]
                                          [defer-signal (date-value defer-until)]])}
     [:summary {:class "cursor-pointer select-none text-sm font-medium text-base-content/70"} "Edit details"]
     [:div {:class "mt-3 space-y-3"}
      [:form {:class "grid gap-2 sm:grid-cols-[1fr_auto]"
              :data-on:submit__prevent
              (str (ds-assign "command/name" (ds-str "todo/rename-task")) "; "
                   (ds-assign "task-id" (ds-str task-id)) "; "
                   (ds-assign "title" (signal-ref title-signal)) "; "
                   "@post('/actions')")}
       [:input {:class "input input-bordered input-sm min-w-0"
                :aria-label "Task title"
                :data-bind title-signal
                :required true}]
       [:button {:class "btn btn-sm btn-outline" :type "submit"} "Rename"]]
      (when (= :active status)
        [:div {:class "grid gap-3 md:grid-cols-3"}
         [:form {:class "grid gap-2"
                 :data-on:submit__prevent
                 (str "if (" (signal-ref project-signal) ") { "
                      (ds-assign "command/name" (ds-str "todo/assign-task-to-project")) "; "
                      (ds-assign "task-id" (ds-str task-id)) "; "
                      (ds-assign "project-id" (signal-ref project-signal)) "; "
                      "@post('/actions'); }")}
          [:select {:class "select select-bordered select-sm min-w-0"
                    :aria-label "Assign project"
                    :data-bind project-signal
                    :disabled (empty? project-options)}
           [:option {:value ""} (if (seq project-options) "Assign project" "No projects")]
           (for [{:keys [project-id name]} project-options]
             [:option {:key project-id :value project-id} name])]
          [:button {:class "btn btn-sm btn-outline"
                    :type "submit"
                    :disabled (empty? project-options)}
           "Assign"]]
         [:form {:class "grid gap-2"
                 :data-on:submit__prevent
                 (str "if (" (signal-ref due-signal) ") { "
                      (ds-assign "command/name" (ds-str "todo/set-task-due-at")) "; "
                      (ds-assign "task-id" (ds-str task-id)) "; "
                      (ds-assign "due-at" (offset-from-date-expr due-signal)) "; "
                      "@post('/actions'); }")}
          [:input {:class "input input-bordered input-sm min-w-0"
                   :aria-label "Due date"
                   :type "date"
                   :data-bind due-signal}]
          [:button {:class "btn btn-sm btn-outline" :type "submit"} "Set due"]]
         [:form {:class "grid gap-2"
                 :data-on:submit__prevent
                 (str "if (" (signal-ref defer-signal) ") { "
                      (ds-assign "command/name" (ds-str "todo/defer-task")) "; "
                      (ds-assign "task-id" (ds-str task-id)) "; "
                      (ds-assign "defer-until" (offset-from-date-expr defer-signal)) "; "
                      "@post('/actions'); }")}
          [:input {:class "input input-bordered input-sm min-w-0"
                   :aria-label "Defer until"
                   :type "date"
                   :data-bind defer-signal}]
          [:button {:class "btn btn-sm btn-outline" :type "submit"} "Defer"]]])
      [:div {:class "flex flex-wrap gap-2"}
       (when project-id
         [:button {:class "btn btn-xs btn-ghost"
                   :data-on:click (command-click "todo/remove-task-from-project" {:task-id task-id})}
          "Remove project"])
       (when due-at
         [:button {:class "btn btn-xs btn-ghost"
                   :data-on:click (command-click "todo/clear-task-due-at" {:task-id task-id})}
          "Clear due"])
       (when defer-until
         [:button {:class "btn btn-xs btn-ghost"
                   :data-on:click (command-click "todo/clear-task-defer-date" {:task-id task-id})}
          "Clear defer"])]]]))

(defn task-card
  ([task] (task-card task []))
  ([{:keys [title bucket due-at defer-until project-id status] :as task} projects]
   (let [project-name (some #(when (= project-id (:project-id %)) (:name %)) projects)]
     [:article {:class "rounded-box border border-base-300 bg-base-100 p-3 shadow-sm"}
   [:div {:class "flex flex-col gap-3"}
    [:div {:class "flex items-start justify-between gap-3"}
     [:div {:class "min-w-0"}
      [:h3 {:class "font-medium"} title]
      [:div {:class "mt-1 flex flex-wrap gap-2 text-xs text-base-content/60"}
       [:span {:class "badge badge-sm badge-outline"} (get bucket-labels bucket (name bucket))]
       [:span {:class "badge badge-sm"} (name status)]
       (when due-at [:span {:class "badge badge-sm badge-warning"} (str "Due " (date-value due-at))])
       (when defer-until [:span {:class "badge badge-sm"} (str "Deferred " (date-value defer-until))])
       (when project-id [:span {:class "badge badge-sm badge-secondary"} (or project-name "Project")])]]
     (task-actions task)]
    (when (= :active (:status task))
      (bucket-move-controls task))
    (task-editor task projects)]])))

(defn task-list
  ([tasks empty-message] (task-list tasks empty-message []))
  ([tasks empty-message projects]
  (if (seq tasks)
    [:div {:class "space-y-3"}
     (for [task tasks]
       (with-meta (task-card task projects) {:key (:task-id task)}))]
    [:div {:class "rounded-box border border-dashed border-base-300 p-6 text-center text-sm text-base-content/60"}
     empty-message])))

(defn bucket-section [bucket tasks projects]
  [:section {:class "space-y-3"}
   [:div {:class "flex items-center justify-between"}
    [:h2 {:class "text-lg font-semibold"} (get bucket-labels bucket)]
    [:span {:class "badge badge-outline"} (count tasks)]]
   (task-list tasks "Nothing here." projects)])

(defn project-card [{:keys [project-id name status]}]
  [:article {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
   [:div {:class "flex flex-col gap-3"}
    [:div {:class "min-w-0"}
     [:h3 {:class "font-medium"} name]
     [:p {:class "text-xs text-base-content/60"} (clojure.core/name status)]]
    [:div {:class "flex flex-wrap gap-2"}
     [:a {:class "btn btn-xs btn-outline" :href (str "/projects/" project-id)} "Open"]
     [:button {:class "btn btn-xs btn-success"
               :data-on:click (command-click "todo/complete-project" {:project-id project-id})}
      "Complete"]]]])

(defn projects-list [projects]
  (if (seq projects)
    [:div {:class "grid gap-3 md:grid-cols-2"}
     (for [project projects]
       (with-meta (project-card project) {:key (:project-id project)}))]
    [:div {:class "rounded-box border border-dashed border-base-300 p-6 text-center text-sm text-base-content/60"}
     "No active projects."]))

(defn home-page [{:keys [buckets deferred due-soon inactive projects review]}]
  (shell {:title "GTD Workspace"}
         (action-error)
         [:div {:class "mb-8 grid gap-4 lg:grid-cols-[2fr_1fr]"}
          (quick-add {:bucket :inbox})
          (project-add)]
         [:div {:class "grid gap-8 lg:grid-cols-[2fr_1fr]"}
          [:div {:class "space-y-8"}
           (for [bucket [:inbox :next :waiting :someday]]
             (with-meta (bucket-section bucket (get buckets bucket) projects) {:key bucket}))
           [:section {:class "space-y-3"}
            [:h2 {:class "text-lg font-semibold"} "Deferred"]
            (task-list deferred "No deferred tasks." projects)]
           [:section {:class "space-y-3"}
            [:h2 {:class "text-lg font-semibold"} "Due Soon"]
            (task-list due-soon "No due dates yet." projects)]
           [:section {:class "space-y-3"}
            [:h2 {:class "text-lg font-semibold"} "Done / Canceled"]
            (task-list inactive "No completed or canceled tasks." projects)]]
          [:aside {:class "space-y-6"}
           [:section {:class "space-y-3"}
            [:h2 {:class "text-lg font-semibold"} "Projects"]
            (projects-list projects)]
           [:section {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
            [:h2 {:class "text-lg font-semibold"} "Weekly Review"]
            [:p {:class "mt-2 text-sm text-base-content/70"}
             (if (= :active (:status review))
               "A weekly review is active."
               "No active weekly review.")]
            [:a {:class "btn btn-outline btn-sm mt-4" :href "/review"} "Open review"]]]]))

(defn tasks-page [{:keys [bucket tasks projects]}]
  (shell {:title (str (get bucket-labels bucket "Tasks") " Tasks")}
         (action-error)
         [:div {:class "mb-6"} (quick-add {:bucket bucket})]
         (task-list tasks "No tasks in this bucket." projects)))

(defn projects-page [{:keys [projects]}]
  (shell {:title "Projects"}
         (action-error)
         [:div {:class "mb-6"} (project-add)]
         (projects-list projects)))

(defn project-page [{:keys [project tasks projects]}]
  (shell {:title (or (:name project) "Project")}
         (action-error)
         (if project
           [:div {:class "space-y-6"}
            (quick-add {:bucket :next :project-id (:project-id project)})
            (task-list tasks "No active tasks for this project." projects)]
           [:div {:class "alert alert-warning"} "Project not found."])))

(defn review-page [{:keys [buckets projects review]}]
  (shell {:title "Weekly Review"}
         (action-error)
         [:section {:class "space-y-4"}
          (if (= :active (:status review))
            [:div {:class "space-y-4"}
             [:p {:class "text-sm text-base-content/70"} "Review each bucket and active project."]
             [:div {:class "grid gap-3 md:grid-cols-2"}
              (for [bucket [:inbox :next :waiting :someday]]
                [:button {:key bucket
                          :class "btn btn-outline justify-between"
                          :data-on:click (command-click "todo/mark-bucket-reviewed"
                                                        {:review-id (:review-id review)
                                                         :bucket (name bucket)})}
                 (get bucket-labels bucket)
                 [:span {:class "badge"} (count (get buckets bucket))]])]
             [:div {:class "space-y-2"}
              (for [{:keys [project-id name]} projects]
                [:button {:key project-id
                          :class "btn btn-outline w-full justify-start"
                          :data-on:click (command-click "todo/mark-project-reviewed"
                                                        {:review-id (:review-id review)
                                                         :project-id project-id})}
                 name])]
             [:button {:class "btn btn-primary"
                       :data-on:click (command-click "todo/complete-weekly-review"
                                                     {:review-id (:review-id review)})}
              "Complete review"]]
            [:button {:class "btn btn-primary"
                      :data-on:click "$['command/name'] = 'todo/start-weekly-review'; @post('/actions');"}
             "Start weekly review"])]))
