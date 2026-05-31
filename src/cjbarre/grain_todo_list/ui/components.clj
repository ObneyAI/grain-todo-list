(ns cjbarre.grain-todo-list.ui.components
  (:require [ai.obney.grain.datastar_v2.interface :as ds]
            [clojure.string :as string]))

(def bucket-labels
  {:inbox "Inbox"
   :next "Next"
   :waiting "Waiting"
   :someday "Someday"})

(defn signal-suffix
  [value]
  (string/replace (str value) #"[^a-zA-Z0-9_]" "_"))

(defn date-value
  [value]
  (some-> value str (subs 0 10)))

(defn reset-signal-expr
  [signal-name value]
  (str (ds/assign signal-name value) "; el.blur();"))

(defn surface-class
  [variant class]
  (let [base (case variant
               :gallery "gallery-variant scroll-mt-4 rounded-[1.25rem] p-px"
               :gallery-chrome "gallery-variant-chrome rounded-[inherit] p-4 md:p-5"
               :panel "home-panel rounded-box border border-base-300 bg-base-100/65 p-4 shadow-sm sm:p-5"
               :card "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"
               :compact-card "rounded-box border border-base-300 bg-base-100 p-3 shadow-sm"
               :list "divide-y divide-base-300 rounded-box border border-base-300 bg-base-100 shadow-sm"
               :empty "empty-state rounded-box border border-base-300 bg-base-100/85 px-4 py-5 text-center text-sm text-base-content/70 shadow-sm"
               :form "flex flex-col gap-3 rounded-box border border-base-300 bg-base-100 p-4 shadow-sm sm:flex-row"
               "")]
    (str base (when class (str " " class)))))

(defn surface
  [{:keys [tag class variant id]} & body]
  (let [tag (or tag :div)
        class (surface-class variant class)]
    (into [tag (cond-> {:class class}
                 id (assoc :id id))]
          body)))

(defn product-label
  [text]
  [:p {:class "text-sm font-medium text-primary"} text])

(defn page-title
  [text]
  [:h1 {:class "text-3xl font-semibold tracking-tight"} text])

(defn section-title
  [text]
  [:h2 {:class "text-lg font-semibold"} text])

(defn metadata-text
  [text]
  [:p {:class "text-sm text-base-content/70"} text])

(defn section-heading
  [{:keys [title count status action]}]
  [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"}
   [:div {:class "min-w-0"}
    (section-title title)
    (when status
      [:p {:class "mt-1 text-sm text-base-content/60"} status])]
   [:div {:class "flex flex-wrap items-center gap-2"}
    (when (some? count)
      [:span {:class "status-token"} count])
    action]])

(defn action-button
  [{:keys [label class type disabled? on-click-attrs]}]
  [:button (cond-> {:class (str "btn " class)
                    :type (or type "button")
                    :disabled disabled?}
             on-click-attrs (merge on-click-attrs))
   label])

(defn text-field
  [{:keys [class command id-key id-value value-key signal-name value aria-label multiline?]}]
  (let [tag (if multiline? :textarea :input)
        attrs {:class (str "inline-edit " class)
               :aria-label aria-label
               :required true}]
    [tag (merge (if multiline? attrs (assoc attrs :type "text"))
                (ds/bind signal-name)
                (ds/on-command :blur
                               {:let [['next (ds/expr (str (ds/signal signal-name) ".trim()"))]]
                                :when (ds/expr (str "next && next !== " (ds/lit value)))
                                :command command
                                :extra [[id-key id-value]
                                        [value-key (ds/expr "next")]]})
                (ds/on :keydown (ds/expr (str "if (evt.key === 'Enter') { evt.preventDefault(); el.blur(); } "
                                           "if (evt.key === 'Escape') { evt.preventDefault(); "
                                           (reset-signal-expr signal-name value) " }"))))]))

(defn chip
  [{:keys [label active? disabled? danger? href on-click-attrs]}]
  (let [class (str "status-token value-chip"
                   (when active? " value-chip-active")
                   (when danger? " value-chip-danger"))]
    (if href
      [:a {:class class :href href} label]
      [:button (cond-> {:class class
                        :type "button"
                        :disabled disabled?}
                 on-click-attrs (merge on-click-attrs))
       label])))

(defn check-action
  [{:keys [aria-label on-click-attrs]}]
  [:button (cond-> {:class "check-action"
                    :type "button"
                    :aria-label (or aria-label "Complete task")
                    :title (or aria-label "Complete task")}
             on-click-attrs (merge on-click-attrs))
   [:span {:class "check-action-mark" :aria-hidden "true"}]])

(defn status-action
  [{:keys [label mark danger? on-click-attrs]}]
  [:button (cond-> {:class (str "status-action" (when danger? " status-action-danger"))
                    :type "button"
                    :aria-label label}
             on-click-attrs (merge on-click-attrs))
   [:span {:class "status-action-circle" :aria-hidden "true"}
    [:span {:class (str "status-action-mark status-action-mark-" (name mark))}]]
   [:span label]])

(defn complete-action
  [{:keys [on-click-attrs]}]
  (status-action {:label "Complete task"
                  :mark :check
                  :on-click-attrs on-click-attrs}))

(defn cancel-action
  [{:keys [on-click-attrs]}]
  (status-action {:label "Cancel task"
                  :mark :cancel
                  :danger? true
                  :on-click-attrs on-click-attrs}))

(defn chip-row
  [& chips]
  (into [:div {:class "value-chip-row"}]
        (remove nil? chips)))

(defn badge
  [{:keys [label class]}]
  (when label
    [:span {:class (str "status-token " (or class ""))} label]))

(defn select-field
  [{:keys [class signal-name aria-label disabled? on-change-attrs]} & options]
  (into [:select (merge {:class (str "inline-select " class)
                         :aria-label aria-label
                         :disabled disabled?}
                        (ds/bind signal-name)
                        on-change-attrs)]
        options))

(defn date-field
  [{:keys [label signal-name value command clear-command id-value value-key]}]
  [:label {:class "inline-date"}
   [:span label]
   [:input (merge {:class "inline-date-input"
                   :aria-label label
                   :type "date"}
                  (ds/bind signal-name)
                  (ds/on-command
                   :change
                   (cond-> {:let [['selected (ds/expr (ds/signal signal-name))]]
                            :when (ds/expr "selected")
                            :then {:command command
                                   :extra [[:task-id id-value]
                                           [value-key (ds/expr "new Date(selected + 'T00:00:00').toISOString()")]]}}
                     value
                     (assoc :else {:command clear-command
                                   :extra [[:task-id id-value]]}))))]])

(defn status-badges
  [badges]
  [:div {:class "flex flex-wrap gap-2 text-xs text-base-content/60"}
   (for [{:keys [key label class]} badges
         :when label]
     (with-meta (badge {:label label :class class}) {:key (or key label)}))])

(defn app-shell [{:keys [title]} & body]
  [:div#app
   [:main {:class "gallery-page min-h-screen bg-base-100 text-base-content"}
    [:div {:class "mx-auto max-w-6xl px-4 py-8"}
     (into
      [:div {:class "app-vista"}
       [:header {:class "mb-8 flex flex-col gap-2"}
        (product-label "Grain Todo")
        (page-title title)
        [:p {:class "max-w-2xl text-sm text-base-content/70"}
         "A personal GTD workspace backed by Grain events and Datastar updates."]]]
      body)]]])

(defn action-error []
  [:div (merge {:class "alert alert-error mb-4"} (ds/show (ds/expr "$error")))
   [:span (ds/text (ds/expr "$error"))]])

(defn empty-state
  [message]
  (surface {:variant :empty} message))

(defn badge-row
  [badges]
  (status-badges badges))

(defn page-section
  [{:keys [title count status action class]} & body]
  [:section {:class (str "space-y-3" (when class (str " " class)))}
   (section-heading {:title title :count count :status status :action action})
   body])

(defn panel
  [{:keys [title count status action class]} & body]
  (surface {:tag :section
            :variant :panel
            :class class}
           (section-heading {:title title :count count :status status :action action})
           (into [:div {:class "mt-4 grid gap-4"}] body)))

(defn panel-stack
  [& body]
  (into [:div {:class "grid gap-5"}] body))

(defn form-class
  [form class]
  (let [[tag attrs & children] form]
    (into [tag (assoc attrs :class class)] children)))

(defn quick-add [{:keys [bucket project-id]}]
  (ds/with-scope (str "quick-add-" (or project-id "global"))
    (form-class
     (ds/action-form
      {:command :todo/capture-task
       :fields (cond-> {:title ""
                        :bucket (name (or bucket :inbox))}
                 project-id (assoc :project-id project-id))
       :reset-on-success? true}
      [:input (merge {:class "input input-bordered min-w-0 flex-1"
                      :aria-label "Task title"
                      :placeholder "Capture a task"
                      :required true}
                     (ds/bind :title))]
      [:select (merge {:class "select select-bordered sm:w-40" :aria-label "Bucket"}
                      (ds/bind :bucket))
       (for [[value label] bucket-labels]
         [:option {:key value :value (name value)} label])]
      (action-button {:label "Add" :class "btn-primary" :type "submit"}))
     (surface-class :form nil))))

(defn project-add []
  (ds/with-scope "project-add"
    (form-class
     (ds/action-form
      {:command :todo/create-project
       :fields {:name ""}
       :reset-on-success? true}
      [:input (merge {:class "input input-bordered min-w-0 flex-1"
                      :aria-label "Project name"
                      :placeholder "New project"
                      :required true}
                     (ds/bind :name))]
      (action-button {:label "Create project" :class "btn-outline" :type "submit"}))
     (surface-class :form nil))))

(defn task-actions [{:keys [task-id status]}]
  [:div {:class "grid gap-3"}
   (when (= :active status)
     [:div {:class "grid gap-3"}
      (complete-action {:on-click-attrs (ds/on-click-command :todo/complete-task
                                                        {:extra {:task-id task-id}})})
      (cancel-action {:on-click-attrs (ds/on-click-command :todo/cancel-task
                                                      {:extra {:task-id task-id}})})])
   (when (not= :active status)
     (chip-row
      (chip {:label (name status) :active? true :disabled? true})
      (when (= :completed status)
        (chip {:label "Archive task"
               :on-click-attrs (ds/on-click-command :todo/archive-task
                                               {:extra {:task-id task-id}})}))
      (when (#{:completed :canceled} status)
        (chip {:label "Reactivate"
               :on-click-attrs (ds/on-click-command :todo/reactivate-task
                                               {:extra {:task-id task-id}})}))))])

(defn bucket-move-controls [{:keys [task-id bucket]}]
  (into (chip-row)
        (for [[value label] bucket-labels]
          (chip {:label label
                 :active? (= value bucket)
                 :disabled? (= value bucket)
                 :on-click-attrs (ds/on-click-command :todo/move-task-to-bucket
                                                 {:extra {:task-id task-id
                                                          :bucket (name value)}})}))))

(defn task-project-name
  [{:keys [project-id]} projects]
  (some #(when (= project-id (:project-id %)) (:name %)) projects))

(defn task-title-edit [{:keys [task-id title]}]
  (let [suffix (signal-suffix task-id)
        title-signal (str "title_" suffix)]
    [:div {:data-signals (ds/signals {title-signal title})}
     (text-field {:class "inline-edit-title"
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
        change-attrs (ds/on-command
                      :change
                      (cond-> {:when (ds/expr (ds/signal project-signal))
                               :then {:command :todo/assign-task-to-project
                                      :extra [[:task-id task-id]
                                              [:project-id (ds/expr (ds/signal project-signal))]]}}
                        project-id
                        (assoc :else {:command :todo/remove-task-from-project
                                      :extra [[:task-id task-id]]})))]
    (task-sidebar-section
     "Project"
     (when (= :active status)
       [:div {:data-signals (ds/signals {project-signal current-project-id})}
        (apply select-field
               {:signal-name project-signal
                :aria-label "Project"
                :disabled? (empty? projects)
                :on-change-attrs change-attrs}
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
              :data-signals (ds/signals {due-signal (date-value due-at)
                                         defer-signal (date-value defer-until)})}
        (date-field {:label "Due"
                     :signal-name due-signal
                     :value (date-value due-at)
                     :command "todo/set-task-due-at"
                     :clear-command "todo/clear-task-due-at"
                     :id-value task-id
                     :value-key :due-at})
        (date-field {:label "Defer"
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
  [{:keys [bucket project-id status] :as task} projects]
  [{:key :bucket :label (when bucket (get bucket-labels bucket (name bucket)))}
   {:key :status :label (when (and status (not= :active status)) (name status))}
   {:key :project :label (when project-id (or (task-project-name task projects) "Project")) :class "status-token-blue"}])

(defn task-schedule-badges
  [{:keys [due-at defer-until]}]
  [{:key :due-at :label (when due-at (str "Due " (date-value due-at))) :class "status-token-warning"}
   {:key :defer-until :label (when defer-until (str "Deferred " (date-value defer-until))) :class "status-token-schedule"}])

(def clickable-content-class
  "block min-w-0 flex-1 rounded-lg -m-2 p-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary hover:bg-base-100/45")

(defn task-href
  [{:keys [task-id]}]
  (str "/task?task-id=" task-id))

(defn project-href
  [{:keys [project-id]}]
  (str "/project?project-id=" project-id))

(defn task-primary-action [{:keys [task-id status]}]
  (case status
    :active (check-action {:on-click-attrs (ds/on-click-command :todo/complete-task
                                                           {:extra {:task-id task-id}})})
    :completed (chip {:label "archive"
                      :on-click-attrs (ds/on-click-command :todo/archive-task
                                                     {:extra {:task-id task-id}})})
    :canceled (chip {:label "active"
                     :on-click-attrs (ds/on-click-command :todo/reactivate-task
                                                    {:extra {:task-id task-id}})})
    nil))

(defn task-summary-row
  ([task] (task-summary-row task []))
  ([{:keys [task-id title] :as task} projects]
   [:div {:key task-id
          :class "flex flex-col gap-2 p-3"}
    [:a {:class clickable-content-class
         :href (task-href task)}
     [:h3 {:class "truncate text-sm font-medium"} title]
     [:div {:class "mt-2 flex flex-wrap items-center gap-2"}
      (badge-row (task-badges task projects))
      (badge-row (task-schedule-badges task))]]]))

(defn task-card
  ([task] (task-card task []))
  ([{:keys [title] :as task} projects]
   (surface {:tag :article :variant :compact-card}
            [:div {:class "grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"}
             [:a {:class clickable-content-class
                  :href (task-href task)}
              [:h3 {:class "truncate font-medium"} title]
              [:div {:class "mt-2 flex flex-wrap items-center gap-2"}
               (badge-row (task-badges task projects))
               (badge-row (task-schedule-badges task))]]
             (when-let [action (task-primary-action task)]
               [:div {:class "flex justify-start sm:justify-end"}
                action])])))

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
    (surface {:variant :list}
             (for [task tasks]
               (task-summary-row task projects)))
    (empty-state "No due dates yet.")))

(defn task-summary-list [tasks empty-message projects]
  (if (seq tasks)
    (surface {:variant :list}
             (for [task tasks]
               (task-summary-row task projects)))
    (empty-state empty-message)))

(defn planning-summary-row
  [{:keys [label count items empty-message projects muted?]}]
  [:section {:class "border-t border-base-content/10 pt-3 first:border-t-0 first:pt-0"}
   [:div {:class "flex items-center justify-between gap-3"}
    [:h3 {:class "truncate text-sm font-semibold"} label]
    [:span {:class "status-token"} count]]
   (cond
     (seq items)
     [:div {:class "mt-3"}
      (if muted?
        [:p {:class "text-sm text-base-content/60"}
         (str count " item" (when (not= 1 count) "s") " closed")]
        (task-summary-list items empty-message projects))]

     muted?
     [:p {:class "mt-2 text-sm text-base-content/60"} empty-message]

     :else
     [:p {:class "mt-2 text-sm text-base-content/60"} empty-message])])

(defn planning-summary
  [{:keys [deferred due-soon inactive projects]}]
  [:div {:class "grid gap-3"}
   (planning-summary-row {:label "Deferred"
                          :count (count deferred)
                          :items deferred
                          :empty-message "No deferred tasks."
                          :projects projects})
   (planning-summary-row {:label "Due Soon"
                          :count (count due-soon)
                          :items due-soon
                          :empty-message "No due dates yet."
                          :projects projects})
   (planning-summary-row {:label "Done / Canceled"
                          :count (count inactive)
                          :items inactive
                          :empty-message "No completed or canceled tasks."
                          :projects projects
                          :muted? true})])

(defn project-actions [{:keys [project-id status]}]
  (chip-row
   (chip {:label (name status) :active? true :disabled? true})
   (when (= :active status)
     (chip {:label "completed"
            :on-click-attrs (ds/on-click-command :todo/complete-project
                                            {:extra {:project-id project-id}})}))
   (when (= :active status)
     (chip {:label "canceled"
            :danger? true
            :on-click-attrs (ds/on-click-command :todo/cancel-project
                                            {:extra {:project-id project-id}})}))
   (when (#{:completed :canceled} status)
     (chip {:label "active"
            :on-click-attrs (ds/on-click-command :todo/reactivate-project
                                            {:extra {:project-id project-id}})}))))

(defn project-editor [{:keys [project-id name]}]
  (let [name-signal (str "project_name_" (signal-suffix project-id))]
    [:div {:data-signals (ds/signals {name-signal name})}
     (text-field {:class "inline-edit-heading"
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
    (badge-row [{:key :active :label (str active " active")}
                {:key :completed :label (str completed " done")}
                {:key :canceled :label (when (pos? canceled) (str canceled " canceled"))}
                {:key :archived :label (when (pos? archived) (str archived " archived"))}])))

(defn project-summary-row [{:keys [project-id name status task-counts] :as project}]
  [:div {:key project-id
         :class "flex flex-col gap-3 p-3 sm:flex-row sm:items-center sm:justify-between"}
   [:a {:class clickable-content-class
        :href (project-href project)}
    [:h3 {:class "truncate text-sm font-medium"} name]
    (badge-row [{:key :status :label (clojure.core/name status)}
                {:key :active :label (str (get task-counts :active 0) " active")}
                {:key :completed :label (str (get task-counts :completed 0) " done")}])]
   (project-actions project)])

(defn project-card [{:keys [name status task-counts] :as project}]
  (surface {:tag :article :variant :card}
           [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
            [:a {:class (str clickable-content-class " space-y-2")
                 :href (project-href project)}
             [:h3 {:class "font-medium"} name]
             [:p {:class "text-sm text-base-content/60"} (clojure.core/name status)]
             (task-count-row task-counts)]]))

(defn task-detail-panel [task projects]
  [:div {:class "grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]"}
   (surface {:tag :section :variant :card}
            [:div {:class "space-y-6"}
             [:div {:class "space-y-3"}
              [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
               [:div {:class "min-w-0 space-y-2"}
                [:p {:class "text-sm font-medium text-base-content/60"} "Task"]
                (task-title-edit task)]
               (when (not= :active (:status task))
                 (badge {:label (clojure.core/name (:status task))}))]
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
                 (get bucket-labels (:bucket task) (some-> task :bucket name))])]])
   (surface {:tag :aside :variant :card}
            [:div {:class "space-y-4"}
             (task-sidebar-section
              "Status"
              (task-actions task))
             (task-project-panel task projects)
             (task-schedule-panel task)])])

(defn projects-list [projects]
  (if (seq projects)
    [:div {:class "grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(min(100%,16rem),1fr))]"}
     (for [project projects]
       (with-meta (project-card project) {:key (:project-id project)}))]
    (empty-state "No active projects.")))

(defn project-summary-list [projects empty-message]
  (if (seq projects)
    (surface {:variant :list}
             (for [project projects]
               (project-summary-row project)))
    (empty-state empty-message)))

(defn review-bucket-action [review bucket reviewed?]
  (chip {:label (if reviewed? "reviewed" "review")
         :active? reviewed?
         :disabled? reviewed?
         :on-click-attrs (ds/on-click-command :todo/mark-bucket-reviewed
                                         {:extra {:review-id (:review-id review)
                                                  :bucket (name bucket)}})}))

(defn review-project-row [review reviewed-project-ids project]
  (let [reviewed? (contains? reviewed-project-ids (:project-id project))]
    [:div {:key (:project-id project)
           :class "grid gap-2 p-3 lg:grid-cols-[1fr_auto] lg:items-center"}
     (project-summary-row project)
     (chip {:label (if reviewed? "reviewed" "review")
            :active? reviewed?
            :disabled? reviewed?
            :on-click-attrs (ds/on-click-command :todo/mark-project-reviewed
                                            {:extra {:review-id (:review-id review)
                                                     :project-id (:project-id project)}})})]))

(defn review-project-list [review reviewed-project-ids projects]
  (if (seq projects)
    (surface {:variant :list}
             (for [project projects]
               (review-project-row review reviewed-project-ids project)))
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
  (cond
    (keyword? k)
    (or (= "data-on" (namespace k))
        (string/starts-with? (name k) "data-on"))

    (string? k)
    (string/starts-with? k "data-on")

    :else false))

(defn inert-attrs
  [attrs]
  (into {}
        (remove (fn [[k _]]
                  (or (data-on-key? k)
                      (= :href k)
                      (= :data-signals k)
                      (= "data-signals" k))))
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

(defn fundamental-tray
  [title & body]
  (surface {:tag :section :variant :card}
           [:h3 {:class "mb-3 text-sm font-semibold"} title]
           (into [:div {:class "grid gap-3"}] body)))

(defn color-swatch
  [label class]
  [:div {:class "flex items-center gap-3"}
   [:span {:class (str "h-8 w-12 rounded border border-base-content/10 shadow-sm " class)}]
   [:span {:class "text-sm text-base-content/70"} label]])

(defn design-system-fundamentals []
  (let [[active-task] gallery-tasks
        [active-project] gallery-projects
        title-signal "fundamental_title"
        project-signal "fundamental_project"
        due-signal "fundamental_due"]
    (surface
     {:tag :section :variant :gallery :class "gallery-vista"}
     (surface
      {:variant :gallery-chrome}
      [:div {:class "mb-4 max-w-[70ch]"}
       (product-label "Design System")
       [:h2 {:class "text-2xl font-semibold tracking-tight"} "Fundamental Elements"]
       [:p {:class "mt-2 text-sm text-base-content/70"}
        "The smallest canonical visual pieces that compose Grain Todo surfaces."]]
      [:div {:class "grid gap-4 md:grid-cols-2 xl:grid-cols-3"}
       (fundamental-tray
        "Color + Glass"
        (color-swatch "Base surface" "bg-base-100")
        (color-swatch "Elevated surface" "bg-base-200")
        (chip-row
         (chip {:label "active" :active? true :disabled? true})
         (chip {:label "danger" :danger? true}))
        [:div {:class "flex flex-wrap gap-2"}
         (badge {:label "Due soon" :class "status-token-warning"})])
       (fundamental-tray
        "Typography"
        [:div {:class "grid gap-1"}
         (product-label "Grain Todo")
         (page-title "Page Title")
         (section-title "Section Title")
         (metadata-text "Metadata and supporting text.")])
       (fundamental-tray
        "Surfaces"
        (panel {:title "Panel surface"
                :status "Groups related workflow content."}
               (empty-state "Quiet panel well."))
        (surface {:variant :compact-card}
                 [:p {:class "text-sm font-medium"} "Card surface"]
                 [:p {:class "mt-1 text-sm text-base-content/70"} "Lightweight bordered content."])
        (surface {:variant :list}
                 (inert (task-summary-row active-task gallery-projects)))
       (empty-state "Empty state."))
       (fundamental-tray
        "Inputs"
        [:div {:data-signals (ds/signals {title-signal "Inline title"
                                          project-signal ""
                                          due-signal "2026-06-03"})}
         (inert (text-field {:class ""
                             :command "todo/rename-task"
                             :id-key :task-id
                             :id-value gallery-task-id
                             :value-key :title
                             :signal-name title-signal
                             :value "Inline title"
                             :aria-label "Inline title"}))]
        (inert (select-field {:signal-name project-signal
                              :aria-label "Project"}
                             [:option {:value ""} "No project"]
                             [:option {:value gallery-project-id} "Launch reference workflow"]))
        (inert (date-field {:label "Due"
                            :signal-name due-signal
                            :value "2026-06-03"
                            :command "todo/set-task-due-at"
                            :clear-command "todo/clear-task-due-at"
                            :id-value gallery-task-id
                            :value-key :due-at})))
       (fundamental-tray
        "Actions"
        [:div {:class "flex flex-wrap gap-2"}
         (action-button {:label "Create" :class "btn-primary"})
         (action-button {:label "Secondary" :class "btn-outline"})]
        (chip-row
         (chip {:label "active" :active? true :disabled? true})
         (chip {:label "cancel" :danger? true})))
       (fundamental-tray
        "Status"
        (badge-row (task-badges active-task gallery-projects))
        (task-count-row (:task-counts active-project))
        (chip-row
         (chip {:label "reviewed" :active? true :disabled? true})
         (chip {:label "review"})))
       (fundamental-tray
        "Feedback"
        (gallery-alert-sample))]))))

(defn gallery-project-detail
  [project]
  (surface {:tag :section :variant :card}
           [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
            [:div {:class "min-w-0 space-y-2"}
             [:h3 {:class "font-medium"} (:name project)]
             [:p {:class "text-sm text-base-content/70"} (str "Status: " (clojure.core/name (:status project)))]
             (task-count-row (:task-counts project))]
            (project-actions project)]
           [:div {:class "mt-4"}
            (project-editor project)]))

(defn gallery-actions-strip
  [active-task completed-task canceled-task archived-task active-project completed-project]
  [:div {:class "grid gap-4 md:grid-cols-2"}
   (surface {:tag :section :variant :card}
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
      (task-actions archived-task)]])
   (surface {:tag :section :variant :card}
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
       (review-bucket-action gallery-review :waiting false)]]])])

(defn gallery-home-panel-sample
  [active-task active-project]
  [:div {:class "grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(16rem,1fr)]"}
   (panel {:title "Workflow"
           :status "Panelized bucket groups keep cards from floating on the glass."
           :count 1}
          (quick-add {:bucket :inbox})
          (page-section {:title "Inbox"
                         :count 1
                         :class "border-t border-base-content/10 pt-5 first:border-t-0 first:pt-0"}
                        (task-list [active-task] "Nothing here." gallery-projects))
          (page-section {:title "Next"
                         :count 0
                         :class "border-t border-base-content/10 pt-5 first:border-t-0 first:pt-0"}
                        (task-list [] "Nothing here." gallery-projects)))
   [:div {:class "grid gap-4 content-start"}
    (panel {:title "Projects"
            :count 1}
           (project-add)
           (projects-list [active-project]))
    (panel {:title "Weekly Review"
            :status "No active weekly review."})
    (panel {:title "Planning"
            :status "Scheduled and recently closed work."}
           (planning-summary {:deferred []
                              :due-soon [active-task]
                              :inactive []
                              :projects gallery-projects}))]])

(defn gallery-specimen
  [{:keys [compact?]}]
  (let [[active-task waiting-task completed-task] gallery-tasks
        [active-project completed-project] gallery-projects
        canceled-project (assoc completed-project
                                :project-id #uuid "00000000-0000-0000-0000-000000000909"
                                :name "Canceled project"
                                :status :canceled)]
    [:div {:class "space-y-8"}
     (gallery-section {:title "Home Panels"
                       :status "Screen-level panels provide the main dashboard hierarchy."}
                      (inert (gallery-home-panel-sample active-task active-project)))

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
                        (inert (task-summary-list gallery-tasks "No tasks in this bucket." gallery-projects))]
                       [:div {:class "space-y-4"}
                        (inert (due-soon-list [active-task] gallery-projects))
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
  (surface {:tag :section
            :variant :gallery
            :class (str class)
            :id id}
           (surface {:variant :gallery-chrome}
                    [:div {:class "mb-3 flex items-center justify-between gap-4"}
                     [:div {:class "min-w-0"}
                      [:p {:class "text-xs font-semibold uppercase opacity-70"} "Aesthetic direction"]
                      [:h2 {:class "text-xl font-semibold"} label]]
                     [:div {:class "gallery-window-controls" :aria-hidden "true"}
                      [:span] [:span] [:span]]]
                    [:p {:class "mb-4 max-w-[70ch] text-sm opacity-75"} summary]
                    (gallery-specimen {:compact? compact?}))))

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
      (design-system-fundamentals)
      (for [variant gallery-variants]
        (with-meta (gallery-variant variant) {:key (:id variant)}))]]]])
