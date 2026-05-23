(ns cjbarre.grain-todo-list.ui)

(defn home-page
  [_props]
  [:div#app
   [:main {:class "min-h-screen bg-base-100 text-base-content"}
    [:section {:class "mx-auto flex min-h-screen max-w-3xl flex-col justify-center px-6 py-12"}
     [:div {:class "space-y-6"}
      [:p {:class "text-sm font-medium uppercase tracking-wide text-primary"}
       "Grain Todo"]
      [:div {:class "space-y-3"}
       [:h1 {:class "text-4xl font-semibold leading-tight"}
        "Home screen is wired up."]
       [:p {:class "max-w-xl text-base text-base-content/70"}
        "This page is rendered by a Grain query, streamed through Datastar, and styled from the generated Tailwind CSS bundle."]]
      [:div {:class "flex flex-wrap gap-3"}
       [:span {:class "badge badge-primary"} "Datastar"]
       [:span {:class "badge badge-outline"} "Tailwind"]
       [:span {:class "badge badge-outline"} "DaisyUI"]]]]]])
