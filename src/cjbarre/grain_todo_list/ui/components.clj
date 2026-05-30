(ns cjbarre.grain-todo-list.ui.components
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

(defn action-error []
  [:div {:data-show "$error" :class "alert alert-error mb-4"}
   [:span {:data-text "$error"}]])

(defn empty-state
  [message]
  [:div {:class "empty-state rounded-box border border-base-300 bg-base-100/85 px-4 py-5 text-center text-sm text-base-content/70 shadow-sm"}
   message])

(defn badge-row
  [badges]
  [:div {:class "flex flex-wrap gap-2 text-xs text-base-content/60"}
   (for [{:keys [key label class]} badges
         :when label]
     [:span {:key (or key label)
             :class (str "badge badge-sm " (or class "badge-outline"))}
      label])])

(defn page-section
  [{:keys [title count status action class]} & body]
  [:section {:class (str "space-y-3" (when class (str " " class)))}
   [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"}
    [:div {:class "min-w-0"}
     [:h2 {:class "text-lg font-semibold"} title]
     (when status
       [:p {:class "mt-1 text-sm text-base-content/60"} status])]
    [:div {:class "flex flex-wrap items-center gap-2"}
     (when (some? count)
       [:span {:class "badge badge-outline"} count])
     action]]
   body])

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
               :type "button"
               :data-on:click (command-click "todo/complete-task" {:task-id task-id})}
      "Done"])
   (when (= :completed status)
     [:button {:class "btn btn-xs btn-outline"
               :type "button"
               :data-on:click (command-click "todo/archive-task" {:task-id task-id})}
      "Archive"])
   (when (#{:completed :canceled} status)
     [:button {:class "btn btn-xs btn-outline"
               :type "button"
               :data-on:click (command-click "todo/reactivate-task" {:task-id task-id})}
      "Reactivate"])
   (when (not= :archived status)
     [:button {:class "btn btn-xs btn-ghost text-error"
               :type "button"
               :data-on:click (command-click "todo/cancel-task" {:task-id task-id})}
      "Cancel"])])

(defn bucket-move-controls [{:keys [task-id bucket]}]
  [:div {:class "flex flex-wrap gap-1"}
   (for [[value label] bucket-labels
         :when (not= value bucket)]
     [:button {:key value
               :class "btn btn-xs btn-ghost"
               :type "button"
               :data-on:click (command-click "todo/move-task-to-bucket"
                                             {:task-id task-id :bucket (name value)})}
      label])])

(defn task-project-name
  [{:keys [project-id]} projects]
  (some #(when (= project-id (:project-id %)) (:name %)) projects))

(defn task-rename-form [{:keys [task-id title]}]
  (let [suffix (signal-suffix task-id)
        title-signal (str "title_" suffix)]
    [:form {:class "grid gap-2 sm:grid-cols-[1fr_auto]"
            :data-signals (signal-map [[title-signal title]])
            :data-on:submit__prevent
            (str (ds-assign "command/name" (ds-str "todo/rename-task")) "; "
                 (ds-assign "task-id" (ds-str task-id)) "; "
                 (ds-assign "title" (signal-ref title-signal)) "; "
                 "@post('/actions')")}
     [:input {:class "input input-bordered input-sm min-w-0 max-sm:text-base"
              :aria-label "Task title"
              :name "title"
              :data-bind title-signal
              :required true}]
     [:button {:class "btn btn-sm btn-outline" :type "submit"} "Rename"]]))

(defn task-sidebar-section
  [title & body]
  [:section {:class "space-y-3 border-t border-base-content/10 pt-4 first:border-t-0 first:pt-0"}
   [:h3 {:class "text-sm font-semibold"} title]
   body])

(defn task-project-panel [{:keys [task-id project-id status] :as task} projects]
  (let [suffix (signal-suffix task-id)
        project-signal (str "project_" suffix)
        project-options (remove #(= project-id (:project-id %)) projects)]
    (task-sidebar-section
     "Project"
     [:div {:class "space-y-2 text-sm text-base-content/70"}
      [:p (or (task-project-name task projects) "No project assigned.")]]
     (when (= :active status)
       [:form {:class "grid gap-2"
               :data-signals (signal-map [[project-signal ""]])
               :data-on:submit__prevent
               (str "if (" (signal-ref project-signal) ") { "
                    (ds-assign "command/name" (ds-str "todo/assign-task-to-project")) "; "
                    (ds-assign "task-id" (ds-str task-id)) "; "
                    (ds-assign "project-id" (signal-ref project-signal)) "; "
                    "@post('/actions'); }")}
        [:select {:class "select select-bordered select-sm min-w-0 max-sm:text-base"
                  :aria-label "Assign project"
                  :name "project-id"
                  :data-bind project-signal
                  :disabled (empty? project-options)}
         [:option {:value ""} (if (seq project-options) "Assign project" "No projects")]
         (for [{:keys [project-id name]} project-options]
           [:option {:key project-id :value project-id} name])]
        [:button {:class "btn btn-sm btn-outline"
                  :type "submit"
                  :disabled (empty? project-options)}
         "Assign"]])
     (when project-id
       [:button {:class "btn btn-xs btn-ghost"
                 :type "button"
                 :data-on:click (command-click "todo/remove-task-from-project" {:task-id task-id})}
        "Remove project"]))))

(defn task-schedule-panel [{:keys [task-id due-at defer-until status]}]
  (let [suffix (signal-suffix task-id)
        due-signal (str "due_" suffix)
        defer-signal (str "defer_" suffix)]
    (task-sidebar-section
     "Schedule"
     [:div {:class "grid grid-cols-2 gap-3 text-sm"}
      [:div
       [:p {:class "text-xs font-medium text-base-content/60"} "Due"]
       [:p {:class "mt-1"} (or (date-value due-at) "None")]]
      [:div
       [:p {:class "text-xs font-medium text-base-content/60"} "Deferred"]
       [:p {:class "mt-1"} (or (date-value defer-until) "None")]]]
     (when (= :active status)
       [:div {:class "grid gap-3"}
        [:form {:class "grid gap-2"
                :data-signals (signal-map [[due-signal (date-value due-at)]])
                :data-on:submit__prevent
                (str "if (" (signal-ref due-signal) ") { "
                     (ds-assign "command/name" (ds-str "todo/set-task-due-at")) "; "
                     (ds-assign "task-id" (ds-str task-id)) "; "
                     (ds-assign "due-at" (offset-from-date-expr due-signal)) "; "
                     "@post('/actions'); }")}
         [:input {:class "input input-bordered input-sm min-w-0 max-sm:text-base"
                  :aria-label "Due date"
                  :name "due-at"
                  :type "date"
                  :data-bind due-signal}]
         [:button {:class "btn btn-sm btn-outline" :type "submit"} "Set due"]]
        [:form {:class "grid gap-2"
                :data-signals (signal-map [[defer-signal (date-value defer-until)]])
                :data-on:submit__prevent
                (str "if (" (signal-ref defer-signal) ") { "
                     (ds-assign "command/name" (ds-str "todo/defer-task")) "; "
                     (ds-assign "task-id" (ds-str task-id)) "; "
                     (ds-assign "defer-until" (offset-from-date-expr defer-signal)) "; "
                     "@post('/actions'); }")}
         [:input {:class "input input-bordered input-sm min-w-0 max-sm:text-base"
                  :aria-label "Defer until"
                  :name "defer-until"
                  :type "date"
                  :data-bind defer-signal}]
         [:button {:class "btn btn-sm btn-outline" :type "submit"} "Defer"]]])
     [:div {:class "flex flex-wrap gap-2"}
      (when due-at
        [:button {:class "btn btn-xs btn-ghost"
                  :type "button"
                  :data-on:click (command-click "todo/clear-task-due-at" {:task-id task-id})}
         "Clear due"])
      (when defer-until
        [:button {:class "btn btn-xs btn-ghost"
                  :type "button"
                  :data-on:click (command-click "todo/clear-task-defer-date" {:task-id task-id})}
         "Clear defer"])])))

(defn task-badges
  [{:keys [bucket due-at defer-until project-id status] :as task} projects]
  [{:key :bucket :label (when bucket (get bucket-labels bucket (name bucket))) :class "badge-outline"}
   {:key :status :label (when status (name status))}
   {:key :due-at :label (when due-at (str "Due " (date-value due-at))) :class "badge-warning"}
   {:key :defer-until :label (when defer-until (str "Deferred " (date-value defer-until)))}
   {:key :project :label (when project-id (or (task-project-name task projects) "Project")) :class "badge-secondary"}])

(defn task-open-link
  [{:keys [task-id]}]
  [:a {:class "btn btn-xs btn-outline" :href (str "/task?task-id=" task-id)} "Open"])

(defn task-primary-action [{:keys [task-id status] :as task}]
  (case status
    :active [:button {:class "btn btn-xs btn-primary"
                      :data-on:click (command-click "todo/complete-task" {:task-id task-id})}
             "Done"]
    :completed [:button {:class "btn btn-xs btn-outline"
                         :data-on:click (command-click "todo/archive-task" {:task-id task-id})}
                "Archive"]
    :canceled [:button {:class "btn btn-xs btn-outline"
                        :data-on:click (command-click "todo/reactivate-task" {:task-id task-id})}
               "Reactivate"]
    (task-open-link task)))

(defn task-summary-row
  ([task] (task-summary-row task []))
  ([{:keys [task-id title due-at] :as task} projects]
   [:div {:key task-id
          :class "flex flex-col gap-2 p-3 sm:flex-row sm:items-center sm:justify-between"}
    [:div {:class "min-w-0"}
     [:h3 {:class "truncate text-sm font-medium"} title]
     (badge-row (task-badges task projects))]
    (when due-at
      [:span {:class "badge badge-warning"} (date-value due-at)])]))

(defn task-card
  ([task] (task-card task []))
  ([{:keys [title] :as task} projects]
   [:article {:class "rounded-box border border-base-300 bg-base-100 p-3 shadow-sm"}
    [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
     [:div {:class "min-w-0 space-y-2"}
      [:h3 {:class "font-medium"} title]
      (badge-row (task-badges task projects))]
     [:div {:class "flex flex-wrap items-center gap-2"}
      (task-open-link task)
      (task-primary-action task)]]]))

(defn task-list
  ([tasks empty-message] (task-list tasks empty-message []))
  ([tasks empty-message projects]
  (if (seq tasks)
    [:div {:class "space-y-3"}
     (for [task tasks]
       (with-meta (task-card task projects) {:key (:task-id task)}))]
    (empty-state empty-message))))

(defn bucket-section [bucket tasks projects]
  (page-section {:title (get bucket-labels bucket)
                 :count (count tasks)}
                (task-list tasks "Nothing here." projects)))

(defn due-soon-list [tasks projects]
  (if (seq tasks)
    [:div {:class "divide-y divide-base-300 rounded-box border border-base-300 bg-base-100 shadow-sm"}
     (for [task tasks]
       (task-summary-row task projects))]
    (empty-state "No due dates yet.")))

(defn task-summary-list [tasks empty-message projects]
  (if (seq tasks)
    [:div {:class "divide-y divide-base-300 rounded-box border border-base-300 bg-base-100 shadow-sm"}
     (for [task tasks]
       (task-summary-row task projects))]
    (empty-state empty-message)))

(defn project-actions [{:keys [project-id status]}]
  [:div {:class "flex flex-wrap gap-2"}
   [:a {:class "btn btn-xs btn-outline" :href (str "/project?project-id=" project-id)} "Open"]
   (when (= :active status)
     [:button {:class "btn btn-xs btn-success"
               :data-on:click (command-click "todo/complete-project" {:project-id project-id})}
      "Complete"])
   (when (= :active status)
     [:button {:class "btn btn-xs btn-ghost text-error"
               :data-on:click (command-click "todo/cancel-project" {:project-id project-id})}
      "Cancel"])
   (when (#{:completed :canceled} status)
     [:button {:class "btn btn-xs btn-outline"
               :data-on:click (command-click "todo/reactivate-project" {:project-id project-id})}
      "Reactivate"])])

(defn project-editor [{:keys [project-id name]}]
  (let [name-signal (str "project_name_" (signal-suffix project-id))]
    [:details {:class "rounded border border-base-300 bg-base-200/30 px-3 py-2"
               :data-signals (signal-map [[name-signal name]])}
     [:summary {:class "cursor-pointer select-none text-sm font-medium text-base-content/70"} "Edit project"]
     [:form {:class "mt-3 grid gap-2 sm:grid-cols-[1fr_auto]"
             :data-on:submit__prevent
             (str (ds-assign "command/name" (ds-str "todo/rename-project")) "; "
                  (ds-assign "project-id" (ds-str project-id)) "; "
                  (ds-assign "name" (signal-ref name-signal)) "; "
                  "@post('/actions')")}
      [:input {:class "input input-bordered input-sm min-w-0"
               :aria-label "Project name"
               :data-bind name-signal
               :required true}]
      [:button {:class "btn btn-sm btn-outline" :type "submit"} "Rename"]]]))

(defn task-count-row [task-counts]
  (let [{:keys [active completed canceled archived]} (merge {:active 0 :completed 0 :canceled 0 :archived 0}
                                                            task-counts)]
    (badge-row [{:key :active :label (str active " active") :class "badge-outline"}
                {:key :completed :label (str completed " done") :class "badge-outline"}
                {:key :canceled :label (when (pos? canceled) (str canceled " canceled")) :class "badge-outline"}
                {:key :archived :label (when (pos? archived) (str archived " archived")) :class "badge-outline"}])))

(defn project-summary-row [{:keys [project-id name status task-counts] :as project}]
  [:div {:key project-id
         :class "flex flex-col gap-3 p-3 sm:flex-row sm:items-center sm:justify-between"}
   [:div {:class "min-w-0"}
    [:h3 {:class "truncate text-sm font-medium"} name]
    (badge-row [{:key :status :label (clojure.core/name status)}
                {:key :active :label (str (get task-counts :active 0) " active") :class "badge-outline"}
                {:key :completed :label (str (get task-counts :completed 0) " done") :class "badge-outline"}])]
   (project-actions project)])

(defn project-card [{:keys [project-id name status task-counts] :as project}]
  [:article {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
   [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
    [:div {:class "min-w-0 space-y-2"}
     [:h3 {:class "font-medium"} name]
     [:p {:class "text-sm text-base-content/60"} (clojure.core/name status)]
     (task-count-row task-counts)]
    [:a {:class "btn btn-xs btn-outline" :href (str "/project?project-id=" project-id)} "Open"]]])

(defn task-detail-panel [task projects]
  [:div {:class "grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]"}
   [:section {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
    [:div {:class "space-y-6"}
     [:div {:class "space-y-3"}
      [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
       [:div {:class "min-w-0 space-y-2"}
        [:p {:class "text-sm font-medium text-base-content/60"} "Task"]
        [:h2 {:class "text-xl font-semibold"} (:title task)]]
       [:span {:class "badge badge-outline"} (clojure.core/name (:status task))]]
      (badge-row (task-badges task projects))]
     [:div {:class "grid gap-4 border-t border-base-content/10 pt-4 md:grid-cols-3"}
      [:div
       [:p {:class "text-xs font-medium text-base-content/60"} "Bucket"]
       [:p {:class "mt-1 text-sm"} (get bucket-labels (:bucket task) (some-> task :bucket name))]]
      [:div
       [:p {:class "text-xs font-medium text-base-content/60"} "Project"]
       [:p {:class "mt-1 text-sm"} (or (task-project-name task projects) "None")]]
      [:div
       [:p {:class "text-xs font-medium text-base-content/60"} "Timing"]
       [:p {:class "mt-1 text-sm"}
        (or (when (:due-at task) (str "Due " (date-value (:due-at task))))
            (when (:defer-until task) (str "Deferred " (date-value (:defer-until task))))
            "None")]]]
     [:div {:class "border-t border-base-content/10 pt-4"}
      [:h3 {:class "mb-3 text-sm font-semibold"} "Rename"]
      (task-rename-form task)]]]
   [:aside {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
    [:div {:class "space-y-4"}
     (task-sidebar-section
      "Status"
      [:p {:class "text-sm text-base-content/70"} (str "Current status: " (clojure.core/name (:status task)))]
      (task-actions task))
     (when (= :active (:status task))
       (task-sidebar-section
        "Move"
        [:p {:class "text-sm text-base-content/70"} "Move this task to another bucket."]
        (bucket-move-controls task)))
     (task-project-panel task projects)
     (task-schedule-panel task)]]])

(defn projects-list [projects]
  (if (seq projects)
    [:div {:class "grid gap-3 md:grid-cols-2"}
     (for [project projects]
       (with-meta (project-card project) {:key (:project-id project)}))]
    (empty-state "No active projects.")))

(defn project-summary-list [projects empty-message]
  (if (seq projects)
    [:div {:class "divide-y divide-base-300 rounded-box border border-base-300 bg-base-100 shadow-sm"}
     (for [project projects]
       (project-summary-row project))]
    (empty-state empty-message)))

(defn review-bucket-action [review bucket reviewed?]
  [:button {:class (str "btn btn-sm " (if reviewed? "btn-success" "btn-outline"))
            :disabled reviewed?
            :data-on:click (command-click "todo/mark-bucket-reviewed"
                                          {:review-id (:review-id review)
                                           :bucket (name bucket)})}
   (if reviewed? "Reviewed" "Mark reviewed")])

(defn review-project-row [review reviewed-project-ids project]
  (let [reviewed? (contains? reviewed-project-ids (:project-id project))]
    [:div {:key (:project-id project)
           :class "grid gap-2 p-3 lg:grid-cols-[1fr_auto] lg:items-center"}
     (project-summary-row project)
     [:button {:class (str "btn btn-sm " (if reviewed? "btn-success" "btn-outline"))
               :disabled reviewed?
               :data-on:click (command-click "todo/mark-project-reviewed"
                                             {:review-id (:review-id review)
                                              :project-id (:project-id project)})}
      (if reviewed? "Reviewed" "Mark reviewed")]]))

(defn review-project-list [review reviewed-project-ids projects]
  (if (seq projects)
    [:div {:class "divide-y divide-base-300 rounded-box border border-base-300 bg-base-100 shadow-sm"}
     (for [project projects]
       (review-project-row review reviewed-project-ids project))]
    (empty-state "No active projects to review.")))

(def gallery-task-id #uuid "00000000-0000-0000-0000-000000000901")
(def gallery-task-2-id #uuid "00000000-0000-0000-0000-000000000902")
(def gallery-task-3-id #uuid "00000000-0000-0000-0000-000000000903")
(def gallery-project-id #uuid "00000000-0000-0000-0000-000000000904")
(def gallery-project-2-id #uuid "00000000-0000-0000-0000-000000000905")
(def gallery-review-id #uuid "00000000-0000-0000-0000-000000000906")

(def gallery-projects
  [{:project-id gallery-project-id
    :name "Launch reference workflow"
    :status :active
    :task-counts {:active 3 :completed 1}}
   {:project-id gallery-project-2-id
    :name "Archive imported notes"
    :status :completed
    :task-counts {:active 0 :completed 4 :archived 2}}])

(def gallery-tasks
  [{:task-id gallery-task-id
    :title "Draft inbox capture conventions"
    :bucket :inbox
    :status :active
    :order 1000
    :project-id gallery-project-id
    :due-at "2026-06-03T12:00:00Z"}
   {:task-id gallery-task-2-id
    :title "Wait for design review notes"
    :bucket :waiting
    :status :active
    :order 2000
    :defer-until "2026-06-07T12:00:00Z"}
   {:task-id gallery-task-3-id
    :title "Confirm weekly review checklist"
    :bucket :next
    :status :completed
    :order 3000
    :project-id gallery-project-2-id}])

(def gallery-review
  {:review-id gallery-review-id
   :status :active
   :reviewed-buckets #{:inbox :next}
   :reviewed-project-ids #{gallery-project-id}})

(def gallery-canceled-task
  {:task-id #uuid "00000000-0000-0000-0000-000000000907"
   :title "Canceled reference task"
   :bucket :someday
   :status :canceled
   :order 4000})

(def gallery-archived-task
  {:task-id #uuid "00000000-0000-0000-0000-000000000908"
   :title "Archived reference task"
   :bucket :someday
   :status :archived
   :order 5000})

(defn data-on-key?
  [k]
  (and (keyword? k)
       (or (= "data-on" (namespace k))
           (string/starts-with? (name k) "data-on"))))

(defn inert-attrs
  [attrs]
  (into {}
        (remove (fn [[k _]]
                  (or (data-on-key? k)
                      (= :href k)
                      (= :data-signals k))))
        attrs))

(defn inert
  [node]
  (cond
    (vector? node)
    (let [[tag maybe-attrs & children] node
          attrs? (map? maybe-attrs)
          attrs (if attrs? maybe-attrs {})
          children (if attrs? children (cons maybe-attrs children))]
      (into [tag (inert-attrs attrs)]
            (map inert)
            children))

    (seq? node) (doall (map inert node))
    :else node))

(defn gallery-section
  [{:keys [title status]} & body]
  (page-section {:title title :status status}
                [:div {:class "grid gap-4"}
                 body]))

(defn gallery-alert-sample []
  [:div {:class "alert alert-error"}
   [:span "Unable to save changes."]])

(defn gallery-project-detail
  [project]
  [:section {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
   [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
    [:div {:class "min-w-0 space-y-2"}
     [:h3 {:class "font-medium"} (:name project)]
     [:p {:class "text-sm text-base-content/70"} (str "Status: " (clojure.core/name (:status project)))]
     (task-count-row (:task-counts project))]
    (project-actions project)]
   [:div {:class "mt-4"}
    (project-editor project)]])

(defn gallery-actions-strip
  [active-task completed-task canceled-task archived-task active-project completed-project]
  [:div {:class "grid gap-4 md:grid-cols-2"}
   [:section {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
    [:h3 {:class "mb-3 text-sm font-semibold"} "Task action states"]
    [:div {:class "space-y-3"}
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Active"]
      (task-actions active-task)]
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Completed"]
      (task-actions completed-task)]
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Canceled"]
      (task-actions canceled-task)]
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Archived"]
      (task-actions archived-task)]]]
   [:section {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
    [:h3 {:class "mb-3 text-sm font-semibold"} "Project action states"]
    [:div {:class "space-y-3"}
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Active"]
      (project-actions active-project)]
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Completed"]
      (project-actions completed-project)]
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:span {:class "text-sm text-base-content/70"} "Reviewed"]
      [:div {:class "flex flex-wrap gap-2"}
       (review-bucket-action gallery-review :inbox true)
       (review-bucket-action gallery-review :waiting false)]]]]])

(defn gallery-specimen
  [{:keys [compact?]}]
  (let [[active-task waiting-task completed-task] gallery-tasks
        [active-project completed-project] gallery-projects
        canceled-project (assoc completed-project
                                :project-id #uuid "00000000-0000-0000-0000-000000000909"
                                :name "Canceled project"
                                :status :canceled)]
    [:div {:class "space-y-8"}
     (gallery-section {:title "Forms and Alerts"
                       :status "Capture forms, project creation, and transient error messaging."}
                      [:div {:class "grid gap-4 lg:grid-cols-[1.3fr_0.9fr]"}
                       [:div {:class "grid gap-4"}
                        (inert (quick-add {:bucket :inbox}))
                        (inert (quick-add {:bucket :next :project-id gallery-project-id}))]
                       [:div {:class "grid gap-4"}
                        (inert (project-add))
                        (gallery-alert-sample)]])

     (gallery-section {:title "Task Cards and Lists"
                       :status "Minimal task cards, summary rows, due-date rows, and empty list states."}
                      [:div {:class "grid gap-4 lg:grid-cols-[1.2fr_0.8fr]"}
                       [:div {:class "space-y-4"}
                        (inert (task-card active-task gallery-projects))
                        (when-not compact?
                          (inert (task-card waiting-task gallery-projects)))
                        (inert (task-card completed-task gallery-projects))
                        (task-summary-list gallery-tasks "No tasks in this bucket." gallery-projects)]
                       [:div {:class "space-y-4"}
                        (due-soon-list [active-task] gallery-projects)
                        (task-list [] "No completed or canceled tasks." gallery-projects)
                        (due-soon-list [] gallery-projects)]])

     (gallery-section {:title "Task Detail and Editing"
                       :status "Dedicated task detail page surfaces, bucket movement, and edit controls."}
                      [:div {:class "grid gap-4 xl:grid-cols-2"}
                       (inert (task-detail-panel active-task gallery-projects))
                       [:div {:class "space-y-4"}
                       (inert (task-detail-panel completed-task gallery-projects))
                        (inert (gallery-actions-strip active-task completed-task gallery-canceled-task gallery-archived-task
                                                       active-project completed-project))]])

     (gallery-section {:title "Projects"
                       :status "Project cards, project detail editing, project summaries, and project empty states."}
                      [:div {:class "grid gap-4 xl:grid-cols-2"}
                       [:div {:class "space-y-4"}
                        (inert (projects-list gallery-projects))
                        (projects-list [])]
                       [:div {:class "space-y-4"}
                        (inert (gallery-project-detail active-project))
                        (inert (project-summary-list [active-project completed-project canceled-project]
                                                     "No projects need attention."))]])

     (gallery-section {:title "Weekly Review"
                       :status "Review progress, reviewed and pending actions, project review rows, and review empty states."}
                      [:div {:class "grid gap-4 xl:grid-cols-2"}
                       [:div {:class "space-y-4"}
                        (inert (review-project-list gallery-review
                                                    (:reviewed-project-ids gallery-review)
                                                    gallery-projects))
                        (review-project-list gallery-review #{} [])]
                       (page-section {:title "Review Controls"
                                      :count 4
                                      :status "Review each active project for stale outcomes and next actions."
                                      :action (inert (review-bucket-action gallery-review :waiting false))}
                                     [:div {:class "flex flex-wrap gap-2"}
                                      (inert (review-bucket-action gallery-review :inbox true))
                                      (inert (review-bucket-action gallery-review :waiting false))]
                                     (empty-state "Every active project has an active task."))])]))

(def gallery-variants
  [{:id "vista-aero-minimal"
    :label "Vista Aero Minimal"
    :class "gallery-vista"
    :summary "Neutral task surfaces inside cool blue-gray Aero glass chrome, with restrained Windows-style controls and reduced card density."
    :compact? false}])

(defn gallery-variant [{:keys [id label class summary compact?]}]
  [:section {:id id
             :class (str "gallery-variant " class " scroll-mt-4 rounded-[1.25rem] p-px")}
   [:div {:class "gallery-variant-chrome rounded-[inherit] p-4 md:p-5"}
    [:div {:class "mb-3 flex items-center justify-between gap-4"}
     [:div {:class "min-w-0"}
      [:p {:class "text-xs font-semibold uppercase opacity-70"} "Aesthetic direction"]
      [:h2 {:class "text-xl font-semibold"} label]]
     [:div {:class "gallery-window-controls" :aria-hidden "true"}
      [:span] [:span] [:span]]]
    [:p {:class "mb-4 max-w-[70ch] text-sm opacity-75"} summary]
    (gallery-specimen {:compact? compact?})]])

(defn dev-gallery-page
  []
  [:div#app
   [:main {:class "gallery-page min-h-screen bg-base-100 text-base-content"}
    [:div {:class "mx-auto max-w-7xl px-4 py-8"}
     [:header {:class "mb-8 grid items-end gap-4 border-b border-base-content/10 pb-6 md:grid-cols-[minmax(0,1fr)_auto]"}
      [:div {:class "min-w-0"}
       [:p {:class "text-sm font-medium uppercase text-primary"} "Grain Todo"]
       [:h1 {:class "text-3xl font-semibold"} "Dev UI Gallery"]
       [:p {:class "mt-2 max-w-[70ch] text-sm text-base-content/70"}
        "Canonical application surfaces rendered in the locked Vista Aero Minimal direction. Cards stay lightweight; full editing happens on dedicated pages."]]
      [:nav {:class "flex flex-wrap gap-2" :aria-label "Aesthetic directions"}
       (for [{:keys [id label]} gallery-variants]
         [:a {:key id
              :class "rounded-full border border-base-content/15 bg-base-100/90 px-3 py-1.5 text-sm text-base-content/75 shadow-sm"
              :href (str "#" id)}
          label])]]
     [:div {:class "space-y-8"}
      (for [variant gallery-variants]
        (with-meta (gallery-variant variant) {:key (:id variant)}))]]]])
