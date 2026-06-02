(ns cjbarre.grain-todo-list.todo-list-service.ui.components
  (:require [cjbarre.grain-todo-list.ui.components
             :refer [action-button badge badge-row chip chip-row command-assignments command-click data-signals empty-state form-class js-literal metadata-text page-section page-title panel product-label section-title select-field signal-ref signal-suffix status-action surface surface-class text-field]]
            [clojure.string :as string]))

(defn date-value
  [value]
  (some-> value str (subs 0 10)))
(defn check-action
  [{:keys [aria-label on-click-attrs]}]
  [:button (cond-> {:class "check-action"
                    :type "button"
                    :aria-label (or aria-label "Complete task")
                    :title (or aria-label "Complete task")}
             on-click-attrs (merge on-click-attrs))
   [:span {:class "check-action-mark" :aria-hidden "true"}]])
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
(defn due-within-field
  [{:keys [signal-name value id-value]}]
  (let [input-id (str signal-name "-input")
        change-js (str "const raw = " (signal-ref signal-name) "; "
                       "if (raw !== '' && raw !== null) { "
                       (command-assignments :todo/set-task-due-within
                                            [[:task-id (js-literal id-value)]
                                             [:due-within-days "Number(raw)"]])
                       " @post($__grainAction); }"
                       (when value
                         (str " else { "
                              (command-assignments :todo/clear-task-due-within
                                                   [[:task-id (js-literal id-value)]])
                              " @post($__grainAction); }")))]
    [:div {:class "due-within-control"}
     [:label {:class "due-within-label" :for input-id} "Due within"]
     [:div {:class "due-within-input-wrap"}
      [:input (merge {:id input-id
                      :name "due-within-days"
                      :class "due-within-input"
                      :aria-label "Due within days"
                      :type "number"
                      :min "0"
                      :step "1"
                      :inputmode "numeric"
                      :placeholder "0"}
                     {:data-bind signal-name
                      :data-on:change change-js})]
      [:span {:class "due-within-unit"} "days"]]]))

(defn quick-add [{:keys [project-id]}]
  (let [title-signal (str "quick_add_" (signal-suffix (or project-id "global")) "_title")
        submit-js (str (command-assignments :todo/capture-task
                                            (cond-> [[:title (signal-ref title-signal)]]
                                              project-id
                                              (conj [:project-id (js-literal project-id)])))
                       " @post($__grainAction); "
                       (signal-ref title-signal) " = '';")]
    (form-class
     [:form {:data-signals (data-signals {title-signal ""})
             :data-on:submit__prevent submit-js}
      [:input {:class "input input-bordered min-w-0 flex-1"
               :aria-label "Task title"
               :placeholder "Add a task"
               :required true
               :data-bind title-signal}]
      (action-button {:label "Add" :class "btn-primary" :type "submit"})]
     (surface-class :form nil))))

(defn project-add []
  (let [name-signal "project_add_name"
        submit-js (str (command-assignments :todo/create-project
                                            [[:name (signal-ref name-signal)]])
                       " @post($__grainAction); "
                       (signal-ref name-signal) " = '';")]
    (form-class
     [:form {:data-signals (data-signals {name-signal ""})
             :data-on:submit__prevent submit-js}
      [:input {:class "input input-bordered min-w-0 flex-1"
               :aria-label "Project name"
               :placeholder "New project"
               :required true
               :data-bind name-signal}]
      (action-button {:label "Create project" :class "btn-outline" :type "submit"})]
     (surface-class :form nil))))

(defn task-actions [{:keys [task-id status]}]
  [:div {:class "grid gap-3"}
   (when (= :active status)
     [:div {:class "grid gap-3"}
      (complete-action {:on-click-attrs (command-click :todo/complete-task
                                                       [[:task-id (js-literal task-id)]])})
      (cancel-action {:on-click-attrs (command-click :todo/cancel-task
                                                     [[:task-id (js-literal task-id)]])})])
   (when (not= :active status)
     (chip-row
      (chip {:label (name status) :active? true :disabled? true})
      (when (= :completed status)
        (chip {:label "Archive task"
               :on-click-attrs (command-click :todo/archive-task
                                              [[:task-id (js-literal task-id)]])}))
      (when (#{:completed :canceled} status)
        (chip {:label "Reactivate"
               :on-click-attrs (command-click :todo/reactivate-task
                                              [[:task-id (js-literal task-id)]])}))))])

(defn task-project-name
  [{:keys [project-id]} projects]
  (some #(when (= project-id (:project-id %)) (:name %)) projects))

(defn task-title-edit [{:keys [task-id title]}]
  (let [suffix (signal-suffix task-id)
        title-signal (str "title_" suffix)]
    [:div {:data-signals (data-signals {title-signal title})}
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
        change-attrs {:data-on:change
                      (str "if (" (signal-ref project-signal) ") { "
                           (command-assignments :todo/assign-task-to-project
                                                [[:task-id (js-literal task-id)]
                                                 [:project-id (signal-ref project-signal)]])
                           " @post($__grainAction); }"
                           (when project-id
                             (str " else { "
                                  (command-assignments :todo/remove-task-from-project
                                                       [[:task-id (js-literal task-id)]])
                                  " @post($__grainAction); }")))}]
    (task-sidebar-section
     "Project"
     (when (= :active status)
       [:div {:data-signals (data-signals {project-signal current-project-id})}
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

(defn due-within-label
  [{:keys [due-within-days]}]
  (when (some? due-within-days)
    (if (zero? due-within-days)
      "Due now"
      (str "Due within " due-within-days " day" (when (not= 1 due-within-days) "s")))))

(defn task-schedule-panel [{:keys [task-id due-within-days due-within-set-at status] :as task}]
  (let [suffix (signal-suffix task-id)
        due-signal (str "due_within_" suffix)]
    (task-sidebar-section
     "Due"
     (when (= :active status)
       [:div {:class "grid gap-3"
              :data-signals (data-signals {due-signal (or due-within-days "")})}
        (due-within-field {:signal-name due-signal
                           :value due-within-days
                           :id-value task-id})])
     (when (not= :active status)
       [:div {:class "grid gap-3 text-sm"}
        [:div
         [:p {:class "text-xs font-medium text-base-content/60"} "Due within"]
         [:p {:class "mt-1"} (or (due-within-label task) "None")]]
        (when due-within-set-at
          [:div
           [:p {:class "text-xs font-medium text-base-content/60"} "Set"]
           [:p {:class "mt-1"} (date-value due-within-set-at)]])]))))

(defn task-badges
  [{:keys [project-id status] :as task} projects]
  [{:key :status :label (when (and status (not= :active status)) (name status))}
   {:key :project :label (when project-id (or (task-project-name task projects) "Project")) :class "status-token-blue"}])

(defn task-schedule-badges
  [task]
  [{:key :due-within :label (due-within-label task) :class "status-token-warning"}])

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
    :active (check-action {:on-click-attrs (command-click :todo/complete-task
                                                          [[:task-id (js-literal task-id)]])})
    :completed (chip {:label "archive"
                      :on-click-attrs (command-click :todo/archive-task
                                                     [[:task-id (js-literal task-id)]])})
    :canceled (chip {:label "active"
                     :on-click-attrs (command-click :todo/reactivate-task
                                                    [[:task-id (js-literal task-id)]])})
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
  [{:keys [due-soon inactive projects]}]
  [:div {:class "grid gap-3"}
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
            :on-click-attrs (command-click :todo/complete-project
                                           [[:project-id (js-literal project-id)]])}))
   (when (= :active status)
     (chip {:label "canceled"
            :danger? true
            :on-click-attrs (command-click :todo/cancel-project
                                           [[:project-id (js-literal project-id)]])}))
   (when (#{:completed :canceled} status)
     (chip {:label "active"
            :on-click-attrs (command-click :todo/reactivate-project
                                           [[:project-id (js-literal project-id)]])}))))

(defn project-editor [{:keys [project-id name]}]
  (let [name-signal (str "project_name_" (signal-suffix project-id))]
    [:div {:data-signals (data-signals {name-signal name})}
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
               [:p {:class "text-xs font-medium text-base-content/60"} "Project"]
               [:p {:class "mt-1 text-sm"} (or (task-project-name task projects) "None")]]
              [:div
               [:p {:class "text-xs font-medium text-base-content/60"} "Timing"]
               [:p {:class "mt-1 text-sm"}
                (or (due-within-label task) "None")]]]])
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

(defn review-task-row [review reviewed-task-ids task projects]
  (let [reviewed? (contains? reviewed-task-ids (:task-id task))]
    [:div {:key (:task-id task)
           :class "grid gap-2 p-3 lg:grid-cols-[1fr_auto] lg:items-center"}
     (task-summary-row task projects)
     (chip {:label (if reviewed? "reviewed" "review")
            :active? reviewed?
            :disabled? reviewed?
            :on-click-attrs (command-click :todo/mark-task-reviewed
                                           [[:review-id (js-literal (:review-id review))]
                                            [:task-id (js-literal (:task-id task))]])})]))

(defn review-task-list [review reviewed-task-ids tasks projects]
  (if (seq tasks)
    (surface {:variant :list}
             (for [task tasks]
               (review-task-row review reviewed-task-ids task projects)))
    (empty-state "No active tasks to review.")))

(defn review-project-row [review reviewed-project-ids project]
  (let [reviewed? (contains? reviewed-project-ids (:project-id project))]
    [:div {:key (:project-id project)
           :class "grid gap-2 p-3 lg:grid-cols-[1fr_auto] lg:items-center"}
     (project-summary-row project)
     (chip {:label (if reviewed? "reviewed" "review")
            :active? reviewed?
            :disabled? reviewed?
            :on-click-attrs (command-click :todo/mark-project-reviewed
                                           [[:review-id (js-literal (:review-id review))]
                                            [:project-id (js-literal (:project-id project))]])})]))

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
    :name "Launch reference checklist"
    :status :active
    :task-counts {:active 3 :completed 1}}
   {:project-id gallery-project-2-id
    :name "Archive imported notes"
    :status :completed
    :task-counts {:active 0 :completed 4 :archived 2}}])

(def gallery-tasks
  [{:task-id gallery-task-id
    :title "Draft task entry guidelines"
    :status :active
    :order 1000
    :project-id gallery-project-id
    :due-within-days 3
    :due-within-set-at "2026-06-01T12:00:00Z"}
   {:task-id gallery-task-2-id
    :title "Review design notes"
    :status :active
    :order 2000}
   {:task-id gallery-task-3-id
    :title "Confirm weekly review checklist"
    :status :completed
    :order 3000
    :project-id gallery-project-2-id}])

(def gallery-review
  {:review-id gallery-review-id
   :status :active
   :reviewed-task-ids #{gallery-task-id}
   :reviewed-project-ids #{gallery-project-id}})

(def gallery-canceled-task
  {:task-id #uuid "00000000-0000-0000-0000-000000000907"
   :title "Canceled reference task"
   :status :canceled
   :order 4000})

(def gallery-archived-task
  {:task-id #uuid "00000000-0000-0000-0000-000000000908"
   :title "Archived reference task"
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
        due-signal "fundamental_due_within"]
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
                :status "Groups related todo content."}
               (empty-state "Quiet panel well."))
        (surface {:variant :compact-card}
                 [:p {:class "text-sm font-medium"} "Card surface"]
                 [:p {:class "mt-1 text-sm text-base-content/70"} "Lightweight bordered content."])
        (surface {:variant :list}
                 (inert (task-summary-row active-task gallery-projects)))
       (empty-state "Empty state."))
       (fundamental-tray
        "Inputs"
        [:div {:data-signals (data-signals {title-signal "Inline title"
                                            project-signal ""
                                            due-signal "3"})}
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
                             [:option {:value gallery-project-id} "Launch reference checklist"]))
        (inert (due-within-field {:signal-name due-signal
                                  :value 3
                                  :id-value gallery-task-id})))
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
       (chip {:label "reviewed" :active? true :disabled? true})
       (chip {:label "review"})]]])])

(defn gallery-home-panel-sample
  [active-task active-project]
  [:div {:class "grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(16rem,1fr)]"}
   (panel {:title "Tasks"
           :status "Active work stays in one ordered list."
           :count 1}
          (quick-add {})
          (page-section {:title "Active Tasks"
                         :count 1
                         :class "border-t border-base-content/10 pt-5 first:border-t-0 first:pt-0"}
                        (task-list [active-task] "Nothing here." gallery-projects))
          (task-list [] "No active tasks."))
   [:div {:class "grid gap-4 content-start"}
    (panel {:title "Projects"
            :count 1}
           (project-add)
           (projects-list [active-project]))
    (panel {:title "Weekly Review"
            :status "No active weekly review."})
    (panel {:title "Planning"
           :status "Scheduled and recently closed work."}
           (planning-summary {:due-soon [active-task]
                              :inactive []
                              :projects gallery-projects}))]])

(defn gallery-specimen
  [{:keys [compact?]}]
  (let [[active-task second-task completed-task] gallery-tasks
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
                       :status "Add-task forms, project creation, and transient error messaging."}
                      [:div {:class "grid gap-4 lg:grid-cols-[1.3fr_0.9fr]"}
                       [:div {:class "grid gap-4"}
                        (inert (quick-add {}))
                        (inert (quick-add {:project-id gallery-project-id}))]
                       [:div {:class "grid gap-4"}
                        (inert (project-add))
                        (gallery-alert-sample)]])

     (gallery-section {:title "Task Cards and Lists"
                       :status "Minimal task cards, summary rows, due-date rows, and empty list states."}
                      [:div {:class "grid gap-4 lg:grid-cols-[1.2fr_0.8fr]"}
                       [:div {:class "space-y-4"}
                        (inert (task-card active-task gallery-projects))
                        (when-not compact?
                          (inert (task-card second-task gallery-projects)))
                        (inert (task-card completed-task gallery-projects))
                        (inert (task-summary-list gallery-tasks "No active tasks." gallery-projects))]
                       [:div {:class "space-y-4"}
                        (inert (due-soon-list [active-task] gallery-projects))
                        (task-list [] "No completed or canceled tasks." gallery-projects)
                        (due-soon-list [] gallery-projects)]])

     (gallery-section {:title "Task Detail and Editing"
                       :status "Dedicated task detail page surfaces and edit controls."}
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
                       :status "Review progress, task review rows, project review rows, and review empty states."}
                      [:div {:class "grid gap-4 xl:grid-cols-2"}
                       [:div {:class "space-y-4"}
                        (inert (review-task-list gallery-review
                                                 (:reviewed-task-ids gallery-review)
                                                 gallery-tasks
                                                 gallery-projects))
                        (inert (review-project-list gallery-review
                                                    (:reviewed-project-ids gallery-review)
                                                    gallery-projects))
                        (review-project-list gallery-review #{} [])]
                       (page-section {:title "Review Controls"
                                      :count 4
                                      :status "Review each active project for stale outcomes and open tasks."
                                      :action (inert (chip {:label "review"}))}
                                     (inert (review-task-list gallery-review
                                                              (:reviewed-task-ids gallery-review)
                                                              gallery-tasks
                                                              gallery-projects))
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
