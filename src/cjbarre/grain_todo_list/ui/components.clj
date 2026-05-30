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

(defn command-expr
  [command assignments]
  (string/join
   " "
   (concat [(str (ds-assign "command/name" (ds-str command)) ";")]
           assignments
           ["@post('/actions');"])))

(defn commit-text-expr
  [command id-key id-value value-key signal-name original-value]
  (let [signal (signal-ref signal-name)]
    (str "let next = " signal ".trim(); "
         "if (next && next !== " (ds-str original-value) ") { "
         (command-expr command
                       [(str (ds-assign (name id-key) (ds-str id-value)) ";")
                        (str (ds-assign (name value-key) "next") ";")])
         " }")))

(defn reset-signal-expr
  [signal-name value]
  (str (ds-assign signal-name (ds-str value)) "; el.blur();"))

(defn inline-text-edit
  [{:keys [class command id-key id-value value-key signal-name value aria-label multiline?]}]
  (let [tag (if multiline? :textarea :input)
        attrs {:class (str "inline-edit " class)
               :aria-label aria-label
               :data-bind signal-name
               :data-on:blur (commit-text-expr command id-key id-value value-key signal-name value)
               :data-on:keydown (str "if (evt.key === 'Enter') { evt.preventDefault(); el.blur(); } "
                                     "if (evt.key === 'Escape') { evt.preventDefault(); "
                                     (reset-signal-expr signal-name value) " }")
               :required true}]
    [tag (if multiline? attrs (assoc attrs :type "text"))]))

(defn value-chip
  [{:keys [label active? disabled? danger? href on-click]}]
  (let [class (str "value-chip"
                   (when active? " value-chip-active")
                   (when danger? " value-chip-danger"))]
    (if href
      [:a {:class class :href href} label]
      [:button {:class class
                :type "button"
                :disabled disabled?
                :data-on:click on-click}
       label])))

(defn value-chip-row
  [& chips]
  (into [:div {:class "value-chip-row"}]
        (remove nil? chips)))

(defn option-select
  [{:keys [class signal-name aria-label disabled? on-change]} & options]
  (into [:select {:class (str "inline-select " class)
                  :aria-label aria-label
                  :data-bind signal-name
                  :data-on:change on-change
                  :disabled disabled?}]
        options))

(defn inline-date-edit
  [{:keys [label signal-name value command clear-command id-value value-key]}]
  [:label {:class "inline-date"}
   [:span label]
   [:input {:class "inline-date-input"
            :aria-label label
            :type "date"
            :data-bind signal-name
            :data-on:change
            (str "if (" (signal-ref signal-name) ") { "
                 (command-expr command
                               [(str (ds-assign "task-id" (ds-str id-value)) ";")
                                (str (ds-assign (name value-key) (offset-from-date-expr signal-name)) ";")])
                 " } else "
                 (if value
                   (str "{ "
                        (command-expr clear-command
                                      [(str (ds-assign "task-id" (ds-str id-value)) ";")])
                        " }")
                   "{}"))}]])

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
             :class (str "badge badge-sm whitespace-nowrap " (or class "badge-outline"))}
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
  (value-chip-row
   (value-chip {:label (name status) :active? true :disabled? true})
   (when (= :active status)
     (value-chip {:label "done"
                  :on-click (command-click "todo/complete-task" {:task-id task-id})}))
   (when (= :completed status)
     (value-chip {:label "archived"
                  :on-click (command-click "todo/archive-task" {:task-id task-id})}))
   (when (#{:completed :canceled} status)
     (value-chip {:label "active"
                  :on-click (command-click "todo/reactivate-task" {:task-id task-id})}))
   (when (not= :archived status)
     (value-chip {:label "canceled"
                  :danger? true
                  :on-click (command-click "todo/cancel-task" {:task-id task-id})}))))

(defn bucket-move-controls [{:keys [task-id bucket]}]
  (into (value-chip-row)
        (for [[value label] bucket-labels]
          (value-chip {:label label
                       :active? (= value bucket)
                       :disabled? (= value bucket)
                       :on-click (command-click "todo/move-task-to-bucket"
                                               {:task-id task-id :bucket (name value)})}))))

(defn task-project-name
  [{:keys [project-id]} projects]
  (some #(when (= project-id (:project-id %)) (:name %)) projects))

(defn task-title-edit [{:keys [task-id title]}]
  (let [suffix (signal-suffix task-id)
        title-signal (str "title_" suffix)]
    [:div {:data-signals (signal-map [[title-signal title]])}
     (inline-text-edit {:class "inline-edit-title"
                        :command "todo/rename-task"
                        :id-key :task-id
                        :id-value task-id
                        :value-key :title
                        :signal-name title-signal
                        :value title
                        :aria-label "Task title"})]))

(defn task-sidebar-section
  [title & body]
  [:section {:class "space-y-3 border-t border-base-content/10 pt-4 first:border-t-0 first:pt-0"}
   [:h3 {:class "text-sm font-semibold"} title]
   body])

(defn task-project-panel [{:keys [task-id project-id status] :as task} projects]
  (let [suffix (signal-suffix task-id)
        project-signal (str "project_" suffix)
        current-project-id (str (or project-id ""))
        on-change (str "if (" (signal-ref project-signal) ") { "
                       (command-expr "todo/assign-task-to-project"
                                     [(str (ds-assign "task-id" (ds-str task-id)) ";")
                                      (str (ds-assign "project-id" (signal-ref project-signal)) ";")])
                       " } else "
                       (if project-id
                         (str "{ "
                              (command-expr "todo/remove-task-from-project"
                                            [(str (ds-assign "task-id" (ds-str task-id)) ";")])
                              " }")
                         "{}"))]
    (task-sidebar-section
     "Project"
     (when (= :active status)
       [:div {:data-signals (signal-map [[project-signal current-project-id]])}
        (apply option-select
               {:signal-name project-signal
                :aria-label "Project"
                :disabled? (empty? projects)
                :on-change on-change}
               (concat [[:option {:value ""} "No project"]]
                       (for [{:keys [project-id name]} projects]
                         [:option {:key project-id :value project-id} name])))])
     (when (not= :active status)
       [:p {:class "text-sm text-base-content/70"} (or (task-project-name task projects) "No project")]))))

(defn task-schedule-panel [{:keys [task-id due-at defer-until status]}]
  (let [suffix (signal-suffix task-id)
        due-signal (str "due_" suffix)
        defer-signal (str "defer_" suffix)]
    (task-sidebar-section
     "Schedule"
     (when (= :active status)
       [:div {:class "grid gap-3"
              :data-signals (signal-map [[due-signal (date-value due-at)]
                                         [defer-signal (date-value defer-until)]])}
        (inline-date-edit {:label "Due"
                           :signal-name due-signal
                           :value (date-value due-at)
                           :command "todo/set-task-due-at"
                           :clear-command "todo/clear-task-due-at"
                           :id-value task-id
                           :value-key :due-at})
        (inline-date-edit {:label "Defer"
                           :signal-name defer-signal
                           :value (date-value defer-until)
                           :command "todo/defer-task"
                           :clear-command "todo/clear-task-defer-date"
                           :id-value task-id
                           :value-key :defer-until})])
     (when (not= :active status)
       [:div {:class "grid grid-cols-2 gap-3 text-sm"}
        [:div
         [:p {:class "text-xs font-medium text-base-content/60"} "Due"]
         [:p {:class "mt-1"} (or (date-value due-at) "None")]]
        [:div
         [:p {:class "text-xs font-medium text-base-content/60"} "Deferred"]
         [:p {:class "mt-1"} (or (date-value defer-until) "None")]]]))))

(defn task-badges
  [{:keys [bucket due-at defer-until project-id status] :as task} projects]
  [{:key :bucket :label (when bucket (get bucket-labels bucket (name bucket))) :class "badge-outline"}
   {:key :status :label (when status (name status))}
   {:key :due-at :label (when due-at (str "Due " (date-value due-at))) :class "badge-warning"}
   {:key :defer-until :label (when defer-until (str "Deferred " (date-value defer-until)))}
   {:key :project :label (when project-id (or (task-project-name task projects) "Project")) :class "badge-secondary"}])

(defn task-open-link
  [{:keys [task-id]}]
  (value-chip {:label "open" :href (str "/task?task-id=" task-id)}))

(defn task-primary-action [{:keys [task-id status] :as task}]
  (case status
    :active (value-chip {:label "done"
                         :on-click (command-click "todo/complete-task" {:task-id task-id})})
    :completed (value-chip {:label "archive"
                            :on-click (command-click "todo/archive-task" {:task-id task-id})})
    :canceled (value-chip {:label "active"
                           :on-click (command-click "todo/reactivate-task" {:task-id task-id})})
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
      [:span {:class "badge badge-warning whitespace-nowrap"} (date-value due-at)])]))

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
  (value-chip-row
   (value-chip {:label "open" :href (str "/project?project-id=" project-id)})
   (value-chip {:label (name status) :active? true :disabled? true})
   (when (= :active status)
     (value-chip {:label "completed"
                  :on-click (command-click "todo/complete-project" {:project-id project-id})}))
   (when (= :active status)
     (value-chip {:label "canceled"
                  :danger? true
                  :on-click (command-click "todo/cancel-project" {:project-id project-id})}))
   (when (#{:completed :canceled} status)
     (value-chip {:label "active"
                  :on-click (command-click "todo/reactivate-project" {:project-id project-id})}))))

(defn project-editor [{:keys [project-id name]}]
  (let [name-signal (str "project_name_" (signal-suffix project-id))]
    [:div {:data-signals (signal-map [[name-signal name]])}
     (inline-text-edit {:class "inline-edit-heading"
                        :command "todo/rename-project"
                        :id-key :project-id
                        :id-value project-id
                        :value-key :name
                        :signal-name name-signal
                        :value name
                        :aria-label "Project name"})]))

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
    (value-chip {:label "open" :href (str "/project?project-id=" project-id)})]])

(defn task-detail-panel [task projects]
  [:div {:class "grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]"}
   [:section {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
    [:div {:class "space-y-6"}
     [:div {:class "space-y-3"}
      [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
       [:div {:class "min-w-0 space-y-2"}
        [:p {:class "text-sm font-medium text-base-content/60"} "Task"]
        (task-title-edit task)]
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
      [:h3 {:class "mb-3 text-sm font-semibold"} "Bucket"]
      (when (= :active (:status task))
        (bucket-move-controls task))
      (when (not= :active (:status task))
        [:p {:class "text-sm text-base-content/70"}
         (get bucket-labels (:bucket task) (some-> task :bucket name))])]]]
   [:aside {:class "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"}
    [:div {:class "space-y-4"}
     (task-sidebar-section
      "Status"
      (task-actions task))
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
  (value-chip {:label (if reviewed? "reviewed" "review")
               :active? reviewed?
               :disabled? reviewed?
               :on-click (command-click "todo/mark-bucket-reviewed"
                                        {:review-id (:review-id review)
                                         :bucket (name bucket)})}))

(defn review-project-row [review reviewed-project-ids project]
  (let [reviewed? (contains? reviewed-project-ids (:project-id project))]
    [:div {:key (:project-id project)
           :class "grid gap-2 p-3 lg:grid-cols-[1fr_auto] lg:items-center"}
     (project-summary-row project)
     (value-chip {:label (if reviewed? "reviewed" "review")
                  :active? reviewed?
                  :disabled? reviewed?
                  :on-click (command-click "todo/mark-project-reviewed"
                                          {:review-id (:review-id review)
                                           :project-id (:project-id project)})})]))

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
