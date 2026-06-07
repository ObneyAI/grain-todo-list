(ns cjbarre.grain-todo-list.user-service.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]
            [clojure.string :as string]))

(def user-event-types
  #{:user/signed-up
    :user/logged-out
    :user/password-set
    :user/email-verification-requested
    :user/email-verified
    :user/password-reset-requested
    :user/password-reset})

(defmulti users*
  (fn [_state event]
    (:event/type event)))

(defmethod users* :user/signed-up
  [state {:keys [user-id email-address password]}]
  (assoc state user-id {:user/id user-id
                        :user/email-address email-address
                        :user/password password
                        :user/token-version 0
                        :user/active true
                        :user/email-verified false}))

(defmethod users* :user/password-set
  [state {:keys [user-id password]}]
  (assoc-in state [user-id :user/password] password))

(defmethod users* :user/email-verification-requested
  [state {:keys [user-id verification-token]}]
  (assoc-in state [user-id :user/pending-email-verification-token] verification-token))

(defmethod users* :user/email-verified
  [state {:keys [user-id]}]
  (-> state
      (assoc-in [user-id :user/email-verified] true)
      (assoc-in [user-id :user/pending-email-verification-token] nil)))

(defmethod users* :user/password-reset-requested
  [state {:keys [user-id reset-token]}]
  (assoc-in state [user-id :user/pending-reset-token] reset-token))

(defmethod users* :user/password-reset
  [state {:keys [user-id password]}]
  (-> state
      (assoc-in [user-id :user/password] password)
      (assoc-in [user-id :user/pending-reset-token] nil)))

(defmethod users* :user/logged-out
  [state {:keys [user-id token-version]}]
  (assoc-in state [user-id :user/token-version] token-version))

(defmethod users* :default
  [state _event]
  state)

(defn all-users
  [context]
  (rmp/project context :user/users))

(defn user
  [context user-id]
  (get (all-users context) user-id))

(defn normalize-email
  [email-address]
  (some-> email-address string/trim string/lower-case))

(defn user-by-email
  [context email-address]
  (let [normalized (normalize-email email-address)]
    (->> (vals (all-users context))
         (filter #(= normalized (:user/email-address %)))
         first)))

(defn email-addresses
  [context]
  (->> (vals (all-users context))
       (map :user/email-address)
       (into #{})))

(defn token-version
  [context user-id]
  (when-let [record (user context user-id)]
    (or (:user/token-version record) 0)))

(defn pending-reset-token
  [context user-id]
  (:user/pending-reset-token (user context user-id)))

(defn pending-email-verification-token
  [context user-id]
  (:user/pending-email-verification-token (user context user-id)))

(defreadmodel :user users
  {:events user-event-types
   :version 1}
  [state event]
  (users* state event))
