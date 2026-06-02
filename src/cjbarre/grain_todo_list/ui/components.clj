(ns cjbarre.grain-todo-list.ui.components
  (:require [clojure.data.json :as json]
            [clojure.string :as string]))

(defn signal-suffix
  [value]
  (string/replace (str value) #"[^a-zA-Z0-9_]" "_"))

(defn js-literal
  [value]
  (string/replace
   (json/write-str (cond
                     (keyword? value) (subs (str value) 1)
                     (uuid? value) (str value)
                     :else value))
   "\\/"
   "/"))

(defn signal-key
  [k]
  (cond
    (keyword? k) (if-let [ns (namespace k)]
                   (str ns "/" (name k))
                   (name k))
    :else (str k)))

(defn signal-ref
  [signal-name]
  (str "$[" (js-literal (signal-key signal-name)) "]"))

(defn data-signals
  [signals]
  (string/replace
   (json/write-str
    (into {}
          (map (fn [[k v]]
                 [(signal-key k) (cond
                                   (keyword? v) (subs (str v) 1)
                                   (uuid? v) (str v)
                                   :else v)]))
          signals))
   "\\/"
   "/"))

(defn reset-signal-expr
  [signal-name value]
  (str (signal-ref signal-name) " = " (js-literal value) "; el.blur();"))

(defn command-name
  [command]
  (if (keyword? command)
    (subs (str command) 1)
    (str command)))

(defn command-assignments
  [command fields]
  (str "$['command/name'] = " (js-literal (command-name command)) "; "
       (string/join
        " "
        (map (fn [[k v]]
               (str "$[" (js-literal (signal-key k)) "] = " v ";"))
             fields))))

(defn command-click
  [command fields]
  {:data-on:click (str (command-assignments command fields)
                       " @post($__grainAction)")})

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
               :required true}
        datastar-attrs {:data-bind signal-name
                        :data-on:blur (str "const next = " (signal-ref signal-name) ".trim(); "
                                           "if (next && next !== " (js-literal value) ") { "
                                           (command-assignments command
                                                                [[id-key (js-literal id-value)]
                                                                 [value-key "next"]])
                                           " @post($__grainAction); }")
                        :data-on:keydown (str "if (evt.key === 'Enter') { evt.preventDefault(); el.blur(); } "
                                              "if (evt.key === 'Escape') { evt.preventDefault(); "
                                              (reset-signal-expr signal-name value) " }")}]
    [tag (merge (if multiline? attrs (assoc attrs :type "text"))
                datastar-attrs)]))

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
  [{:keys [class signal-name aria-label disabled? on-change-attrs]} & options]
  (into [:select (merge {:class (str "inline-select " class)
                         :aria-label aria-label
                         :disabled disabled?}
                        {:data-bind signal-name}
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
