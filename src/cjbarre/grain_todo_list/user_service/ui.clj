(ns cjbarre.grain-todo-list.user-service.ui
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.ui :as app-ui]
            [cjbarre.grain-todo-list.ui.components :as c]
            [cjbarre.grain-todo-list.user-service.schemas :as schemas]))

(defn auth-shell
  [{:keys [title]} & body]
  (app-ui/app-shell
   {:title title}
   (app-ui/action-error)
   (apply c/surface {:tag :section
                     :variant :card
                     :class "mx-auto max-w-md"}
          body)))

(defn input
  [attrs]
  [:input (merge {:class "input input-bordered w-full"} attrs)])

(defn field
  [label attrs]
  [:label {:class "form-control w-full"}
   [:div {:class "label"} [:span {:class "label-text"} label]]
   (input attrs)])

(defn sign-in-page []
  (auth-shell
   {:title "Sign In"}
   (ds-ui/with-signals [email-address {:init ""}
                        password {:init ""}]
     [:form {:class "grid gap-4"
             :on/submit {:effect (ds-ui/effects
                                   (ds-ui/clear-errors)
                                   (ds-ui/dispatch :user/login
                                                   {:email-address email-address
                                                    :password password}))
                         :modifiers {:prevent true}}}
      (field "Email" {:type "email"
                      :required true
                      :autocomplete "email"
                      :bind/value email-address})
      (field "Password" {:type "password"
                         :required true
                         :autocomplete "current-password"
                         :bind/value password})
      (c/action-button {:label "Sign in" :class "btn-primary" :type "submit"})])
   [:div {:class "mt-4 flex flex-wrap gap-3 text-sm"}
    [:a {:class "link link-primary" :href (ds-ui/href :user/sign-up-page)} "Create account"]
    [:a {:class "link link-primary" :href (ds-ui/href :user/forgot-password-page)} "Forgot password?"]]))

(defn sign-up-page []
  (auth-shell
   {:title "Create Account"}
   (ds-ui/with-signals [email-address {:init ""}
                        password {:init ""}]
     [:form {:class "grid gap-4"
             :on/submit {:effect (ds-ui/effects
                                   (ds-ui/clear-errors)
                                   (ds-ui/dispatch :user/sign-up
                                                   {:email-address email-address
                                                    :password password})
                                   (ds-ui/reset-signal password))
                         :modifiers {:prevent true}}}
      (field "Email" {:type "email"
                      :required true
                      :autocomplete "email"
                      :bind/value email-address})
      (field "Password" {:type "password"
                         :required true
                         :autocomplete "new-password"
                         :minlength "8"
                         :bind/value password})
      [:p {:class "text-xs text-base-content/60"} schemas/password-requirements]
      (c/action-button {:label "Create account" :class "btn-primary" :type "submit"})])
   [:div {:class "mt-4 text-sm"}
    [:a {:class "link link-primary" :href (ds-ui/href :user/sign-in-page)} "Already have an account?"]]))

(defn forgot-password-page []
  (auth-shell
   {:title "Forgot Password"}
   (ds-ui/with-signals [email-address {:init ""}]
     [:form {:class "grid gap-4"
             :on/submit {:effect (ds-ui/effects
                                   (ds-ui/clear-errors)
                                   (ds-ui/dispatch :user/request-password-reset
                                                   {:email-address email-address})
                                   (ds-ui/reset-signal email-address))
                         :modifiers {:prevent true}}}
      (field "Email" {:type "email"
                      :required true
                      :autocomplete "email"
                      :bind/value email-address})
      (c/action-button {:label "Send reset link" :class "btn-primary" :type "submit"})])
   [:div {:class "mt-4 text-sm"}
    [:a {:class "link link-primary" :href (ds-ui/href :user/sign-in-page)} "Back to sign in"]]))

(defn verify-email-page
  [{:keys [verification-token]}]
  (auth-shell
   {:title "Verify Email"}
   [:form {:class "grid gap-4"
           :on/submit {:effect (ds-ui/effects
                                 (ds-ui/clear-errors)
                                 (ds-ui/dispatch :user/verify-email
                                                 {:verification-token verification-token}))
                       :modifiers {:prevent true}}}
    [:p {:class "text-sm text-base-content/70"}
     "Confirm this email address for your account."]
    (c/action-button {:label "Verify email" :class "btn-primary" :type "submit"})]
   [:div {:class "mt-4 text-sm"}
    [:a {:class "link link-primary" :href (ds-ui/href :user/sign-in-page)} "Back to sign in"]]))

(defn reset-password-page
  [{:keys [reset-token]}]
  (auth-shell
   {:title "Reset Password"}
   (ds-ui/with-signals [password {:init ""}]
     [:form {:class "grid gap-4"
             :on/submit {:effect (ds-ui/effects
                                   (ds-ui/clear-errors)
                                   (ds-ui/dispatch :user/reset-password
                                                   {:reset-token reset-token
                                                    :password password})
                                   (ds-ui/reset-signal password))
                         :modifiers {:prevent true}}}
      (field "New password" {:type "password"
                             :required true
                             :autocomplete "new-password"
                             :minlength "8"
                             :bind/value password})
      [:p {:class "text-xs text-base-content/60"} schemas/password-requirements]
      (c/action-button {:label "Reset password" :class "btn-primary" :type "submit"})])
   [:div {:class "mt-4 text-sm"}
    [:a {:class "link link-primary" :href (ds-ui/href :user/sign-in-page)} "Back to sign in"]]))
