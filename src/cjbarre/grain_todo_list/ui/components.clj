(ns cjbarre.grain-todo-list.ui.components)

(defn surface-class
  [variant class]
  (let [base (case variant
               :gallery "gallery-variant scroll-mt-4 rounded-[1.25rem] p-px"
               :gallery-chrome "gallery-variant-chrome rounded-[inherit] p-4 md:p-5"
               :panel "home-panel rounded-box border border-base-300 bg-base-100/65 p-4 shadow-sm sm:p-5"
               :card "rounded-box border border-base-300 bg-base-100 p-4 shadow-sm"
               :compact-card "rounded-box border border-base-300 bg-base-100 p-3 shadow-sm"
               :list "divide-y divide-base-300 rounded-box border border-base-300 bg-base-100 shadow-sm"
               :empty "empty-state rounded-box border border-base-300 bg-base-200/45 px-4 py-5 text-center text-sm text-base-content/70"
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
  [{:keys [label class type disabled? on-click-attrs attrs]}]
  [:button (cond-> {:class (str "btn " class)
                    :type (or type "button")
                    :disabled disabled?}
             attrs (merge attrs)
             on-click-attrs (merge on-click-attrs))
   label])

(defn text-field
  [{:keys [class aria-label multiline? attrs]}]
  (let [tag (if multiline? :textarea :input)
        base-attrs {:class (str "inline-edit " class)
                    :aria-label aria-label
                    :required true}]
    [tag (merge (if multiline? base-attrs (assoc base-attrs :type "text"))
                attrs)]))

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

(defn status-action
  [{:keys [label mark danger? on-click-attrs]}]
  [:button (cond-> {:class (str "status-action" (when danger? " status-action-danger"))
                    :type "button"
                    :aria-label label}
             on-click-attrs (merge on-click-attrs))
   [:span {:class "status-action-circle" :aria-hidden "true"}
    [:span {:class (str "status-action-mark status-action-mark-" (name mark))}]]
   [:span label]])

(defn chip-row
  [& chips]
  (into [:div {:class "value-chip-row"}]
        (remove nil? chips)))

(defn badge
  [{:keys [label class]}]
  (when label
    [:span {:class (str "status-token " (or class ""))} label]))

(defn select-field
  [{:keys [class aria-label disabled? on-change-attrs attrs]} & options]
  (into [:select (merge {:class (str "inline-select " class)
                         :aria-label aria-label
                         :disabled disabled?}
                        attrs
                        on-change-attrs)]
        options))

(defn status-badges
  [badges]
  [:div {:class "flex flex-wrap gap-2 text-xs text-base-content/60"}
   (for [{:keys [key label class]} badges
         :when label]
     (with-meta (badge {:label label :class class}) {:key (or key label)}))])

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
