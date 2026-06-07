(ns cjbarre.grain-todo-list.user-service.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]
            [clojure.string :as string]))

(def email-re #"^[^@\s]+@[^@\s]+\.[^@\s]+$")

(def password-requirements
  "Use at least 8 characters with uppercase, lowercase, and a number.")

(defn non-blank-string?
  [x]
  (and (string? x) (seq (string/trim x))))

(def password-schema
  [:and
   [:string {:min 8 :error/message password-requirements}]
   [:fn {:error/message password-requirements} #(re-find #"[A-Z]" %)]
   [:fn {:error/message password-requirements} #(re-find #"[a-z]" %)]
   [:fn {:error/message password-requirements} #(re-find #"[0-9]" %)]])

(defschemas common-schemas
  {::email [:re {:error/message "Invalid email"} email-re]
   ::non-blank-string [:fn {:error/message "Must not be blank"} non-blank-string?]
   ::password password-schema
   ::user [:map
           [:user/id :uuid]
           [:user/email-address :string]
           [:user/password :string]
           [:user/token-version nat-int?]
           [:user/active :boolean]
           [:user/email-verified :boolean]
           [:user/pending-email-verification-token {:optional true} [:maybe :string]]
           [:user/pending-reset-token {:optional true} [:maybe :string]]]})

(defschemas event-schemas
  {:user/signed-up [:map
                    [:user-id :uuid]
                    [:email-address :string]
                    [:password :string]]
   :user/logged-in [:map
                    [:user-id :uuid]
                    [:email-address :string]]
   :user/logged-out [:map
                     [:user-id :uuid]
                     [:token-version nat-int?]]
   :user/password-set [:map
                       [:user-id :uuid]
                       [:password :string]]
   :user/email-verification-requested [:map
                                       [:user-id :uuid]
                                       [:email-address :string]
                                       [:verification-token :string]]
   :user/email-verified [:map
                         [:user-id :uuid]
                         [:email-address :string]]
   :user/password-reset-requested [:map
                                   [:user-id :uuid]
                                   [:email-address :string]
                                   [:reset-token :string]]
   :user/password-reset [:map
                         [:user-id :uuid]
                         [:password :string]]})

(defschemas command-schemas
  {:user/sign-up [:map
                  [:email-address ::email]
                  [:password ::password]
                  [:confirm-password ::password]]
   :user/login [:map
                [:email-address [:string {:min 1 :error/message "Email is required"}]]
                [:password [:string {:min 1 :error/message "Password is required"}]]]
   :user/logout [:map]
   :user/set-password [:map
                       [:password ::password]]
   :user/verify-email [:map
                       [:verification-token :string]]
   :user/request-password-reset [:map
                                 [:email-address ::email]]
   :user/reset-password [:map
                         [:reset-token :string]
                         [:password ::password]]})

(defschemas query-schemas
  {:user/sign-in-page [:map]
   :user/sign-up-page [:map]
   :user/forgot-password-page [:map]
   :user/verify-email-page [:map [:verification-token {:optional true} :string]]
   :user/reset-password-page [:map [:reset-token {:optional true} :string]]})

(defschemas read-model-schemas
  {:user/users [:map-of :uuid ::user]})
