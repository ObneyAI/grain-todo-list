(ns cjbarre.grain-todo-list.ui
  (:require [ai.obney.grain.datastar_v2.interface :as ds]
            [cjbarre.grain-todo-list.ui.components :as c]))

(defn app-shell [{:keys [title]} & body]
  [:div#app
   [:main {:class "gallery-page min-h-screen bg-base-100 text-base-content"}
    [:div {:class "mx-auto max-w-6xl px-4 py-8"}
     (into
      [:div {:class "app-vista"}
       [:header {:class "mb-8 flex flex-col gap-2"}
        (c/product-label "Grain Todo")
        (c/page-title title)
        [:p {:class "max-w-2xl text-sm text-base-content/70"}
         "A personal GTD workspace backed by Grain events and Datastar updates."]]]
      body)]]])

(defn action-error []
  [:div (merge {:class "alert alert-error mb-4"} (ds/show (ds/expr "$error")))
   [:span (ds/text (ds/expr "$error"))]])
