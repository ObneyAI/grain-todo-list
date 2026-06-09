(ns cjbarre.grain-todo-list.service.user-service.ui
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.foundation.ui :as app-ui]
            [cjbarre.grain-todo-list.foundation.ui.components :as c]
            [cjbarre.grain-todo-list.service.user-service.schemas :as schemas]))

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

(defn field-error-value
  [field]
  (ds-ui/js
   (format "($fieldErrors && $fieldErrors['%s'] && $fieldErrors['%s'][0]) || ''"
           (name field)
           (name field))))

(defn field-error
  [error]
  [:p {:class "mt-1 text-xs text-error"
       :bind/show (ds-ui/present? error)
       :bind/text error}])

(defn field
  [label attrs]
  [:label {:class "form-control w-full"}
   [:div {:class "label"} [:span {:class "label-text"} label]]
   (input attrs)])

(defn requirement
  [met? text]
  [:li {:class "flex items-center gap-2 text-xs text-base-content/60"
        :bind/class (ds-ui/js "{'text-success': " met? ", 'text-base-content/60': !" met? "}")}
   [:span {:class "w-6 font-mono"
           :bind/text (ds-ui/js met? " ? 'OK' : '--'")}]
   [:span text]])

(defn sign-in-page []
  (auth-shell
   {:title "Sign In"}
   (ds-ui/with-signals [email-address {:init ""}
                        password {:init ""}
                        email-error {:init ""}
                        password-error {:init ""}]
     (let [has-email? (ds-ui/js email-address ".trim().length > 0")
           has-password? (ds-ui/js password ".length > 0")
           form-valid? (ds-ui/js has-email? " && " has-password?)
           clear-sign-in-errors (ds-ui/effects
                                 (ds-ui/clear-errors)
                                 (ds-ui/set-signal email-error "")
                                 (ds-ui/set-signal password-error ""))]
       [:form {:class "grid gap-4"
               :on/signal-patch
               {:effect (ds-ui/effects
                         (ds-ui/set-signal email-error (field-error-value :email-address))
                         (ds-ui/set-signal password-error (field-error-value :password)))}
               :on/submit {:effect (ds-ui/effects
                                     clear-sign-in-errors
                                     (ds-ui/dispatch :user/login
                                                     {:email-address email-address
                                                      :password password}))
                           :modifiers {:prevent true}}}
        (field "Email" {:id "sign-in-email"
                        :name "email-address"
                        :type "email"
                        :required true
                        :autocomplete "email"
                        :bind/value email-address
                        :on/input {:effect clear-sign-in-errors}})
        (field-error email-error)
        (field "Password" {:id "sign-in-password"
                           :name "password"
                           :type "password"
                           :required true
                           :autocomplete "current-password"
                           :bind/value password
                           :on/input {:effect clear-sign-in-errors}})
        (field-error password-error)
        (c/action-button {:label "Sign in"
                          :class "btn-primary"
                          :type "submit"
                          :disabled? true
                          :attrs {:bind/prop {:disabled (ds-ui/js "!(" form-valid? ")")}}})]))
   [:div {:class "mt-4 flex flex-wrap gap-3 text-sm"}
    [:a {:class "link link-primary" :href (ds-ui/href :user/sign-up-page)} "Create account"]
    [:a {:class "link link-primary" :href (ds-ui/href :user/forgot-password-page)} "Forgot password?"]]))

(defn sign-up-page []
  (auth-shell
   {:title "Create Account"}
   (ds-ui/with-signals [email-address {:init ""}
                        password {:init ""}
                        confirm-password {:init ""}
                        sign-up-complete {:init false}
                        email-error {:init ""}
                        password-error {:init ""}
                        confirm-password-error {:init ""}]
     (let [has-email? (ds-ui/js email-address ".trim().length > 0")
           has-length? (ds-ui/js password ".length >= 8")
           has-uppercase? (ds-ui/js "/[A-Z]/.test(" password ")")
           has-lowercase? (ds-ui/js "/[a-z]/.test(" password ")")
           has-number? (ds-ui/js "/[0-9]/.test(" password ")")
           password-valid? (ds-ui/js has-length? " && " has-uppercase? " && " has-lowercase? " && " has-number?)
           confirm-touched? (ds-ui/js confirm-password ".length > 0")
           passwords-match? (ds-ui/js confirm-password " === " password)
           form-valid? (ds-ui/js has-email? " && " password-valid? " && " confirm-touched? " && " passwords-match?)
           clear-sign-up-errors (ds-ui/effects
                                 (ds-ui/clear-errors)
                                 (ds-ui/set-signal email-error "")
                                 (ds-ui/set-signal password-error "")
                                 (ds-ui/set-signal confirm-password-error ""))]
       [:div {:on/signal-patch
              {:effect (ds-ui/effects
                        (ds-ui/set-signal sign-up-complete (ds-ui/js "$signUpComplete === true"))
                        (ds-ui/set-signal email-error (field-error-value :email-address))
                        (ds-ui/set-signal password-error (field-error-value :password))
                        (ds-ui/set-signal confirm-password-error (field-error-value :confirm-password)))}}
        [:div {:class "alert mb-4 text-success"
               :bind/show sign-up-complete}
         [:span "Account created. Check your email to verify your address."]]
        [:form {:class "grid gap-4"
                :bind/show (ds-ui/js "!(" sign-up-complete ")")
                :on/submit {:effect (ds-ui/effects
                                      clear-sign-up-errors
                                      (ds-ui/dispatch :user/sign-up
                                                      {:email-address email-address
                                                       :password password
                                                       :confirm-password confirm-password}))
                            :modifiers {:prevent true}}}
         (field "Email" {:id "sign-up-email"
                         :name "email-address"
                         :type "email"
                         :required true
                         :autocomplete "email"
                         :bind/value email-address
                         :on/input {:effect clear-sign-up-errors}})
         (field-error email-error)
         (field "Password" {:id "sign-up-password"
                            :name "password"
                            :type "password"
                            :required true
                            :autocomplete "new-password"
                            :minlength "8"
                            :bind/value password
                            :on/input {:effect clear-sign-up-errors}})
         (field-error password-error)
         [:ul {:class "grid gap-1"}
          (requirement has-length? "At least 8 characters")
          (requirement has-uppercase? "One uppercase letter")
          (requirement has-lowercase? "One lowercase letter")
          (requirement has-number? "One number")]
         (field "Confirm password" {:id "sign-up-confirm-password"
                                    :name "confirm-password"
                                    :type "password"
                                    :required true
                                    :autocomplete "new-password"
                                    :bind/value confirm-password
                                    :on/input {:effect clear-sign-up-errors}})
         (field-error confirm-password-error)
         [:p {:class "text-xs text-error"
              :bind/show (ds-ui/js confirm-touched? " && !(" passwords-match? ")")}
          "Passwords do not match."]
         [:p {:class "text-xs text-success"
              :bind/show (ds-ui/js confirm-touched? " && " passwords-match?)}
          "Passwords match."]
         (c/action-button {:label "Create account"
                           :class "btn-primary"
                           :type "submit"
                           :disabled? true
                           :attrs {:bind/prop {:disabled (ds-ui/js "!(" form-valid? ")")}}})]]))
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
