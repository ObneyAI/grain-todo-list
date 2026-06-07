(ns cjbarre.grain-todo-list.user-service.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [buddy.hashers :as hashers]
            [cjbarre.grain-todo-list.jwt :as jwt]
            [cjbarre.grain-todo-list.user-service.read-models :as rm]
            [cognitect.anomalies :as anom]))

(defn anomaly
  [category message]
  {::anom/category category ::anom/message message})

(defn field-anomaly
  [category message field field-message]
  (assoc (anomaly category message) :error/explain {field [field-message]}))

(defn conflict [message] (anomaly ::anom/conflict message))
(defn field-conflict [message field field-message]
  (field-anomaly ::anom/conflict message field field-message))
(defn forbidden [message] (anomaly ::anom/forbidden message))
(defn not-found [message] (anomaly ::anom/not-found message))

(defn invalid-credentials []
  (field-conflict "Invalid credentials."
                  :password
                  "Invalid credentials."))

(defn make-event
  [event]
  (->event event))

(defn authenticated?
  [ctx]
  (some? (:auth-claims ctx)))

(defn make-session-token
  [{:keys [jwt-secret tenant-id]} user]
  (jwt/sign {:payload {:user-id (str (:user/id user))
                       :email (:user/email-address user)
                       :tenant-id (str tenant-id)
                       :token-version (or (:user/token-version user) 0)}
             :secret jwt-secret
             :expire-in [24 :hours]}))

(defn make-reset-token
  [{:keys [jwt-secret]} user]
  (jwt/sign {:payload {:user-id (str (:user/id user))
                       :email (:user/email-address user)}
             :secret jwt-secret
             :expire-in [30 :minutes]}))

(defn make-email-verification-token
  [{:keys [jwt-secret]} user]
  (jwt/sign {:payload {:user-id (str (:user/id user))
                       :email (:user/email-address user)}
             :secret jwt-secret
             :expire-in [24 :hours]}))

(defn auth-user-id
  [context]
  (get-in context [:auth-claims :user-id]))

(defcommand :user sign-up
  {:authorized? (constantly true)}
  [{{:keys [email-address password confirm-password]} :command :as context}]
  (let [email-address (rm/normalize-email email-address)]
    (cond
      (contains? (rm/email-addresses context) email-address)
      (field-conflict "Email already registered."
                      :email-address
                      "An account already exists for this email.")

      (not= password confirm-password)
      (field-conflict "Passwords do not match."
                      :confirm-password
                      "Passwords do not match.")

      :else
      (let [user-id (random-uuid)]
        {:command-result/events
         (let [user {:user/id user-id
                     :user/email-address email-address}
               verification-token (make-email-verification-token context user)]
           [(make-event {:type :user/signed-up
                         :tags #{[:user user-id]}
                         :body {:user-id user-id
                                :email-address email-address
                                :password (hashers/derive password)}})
            (make-event {:type :user/email-verification-requested
                         :tags #{[:user user-id]}
                         :body {:user-id user-id
                                :email-address email-address
                                :verification-token verification-token}})])
         :datastar/signals {:__toast "Account created. Check your email to verify your address."}}))))

(defcommand :user login
  {:authorized? (constantly true)}
  [{{:keys [email-address password]} :command :as context}]
  (let [{user-id :user/id
         stored-password :user/password
         active? :user/active
         :as user} (rm/user-by-email context email-address)]
    (cond
      (nil? user)
      (invalid-credentials)

      (not (:valid (hashers/verify password stored-password)))
      (invalid-credentials)

      (false? active?)
      (forbidden "Account is inactive.")

      :else
      {:command-result/events
       [(make-event {:type :user/logged-in
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :email-address (:user/email-address user)}})]
       :auth/token (make-session-token context user)
       :datastar/signals {:__redirect "/"}})))

(defcommand :user logout
  {:authorized? authenticated?}
  [context]
  (let [user-id (auth-user-id context)
        current-version (rm/token-version context user-id)]
    (if (nil? current-version)
      (not-found "User not found.")
      {:command-result/events
       [(make-event {:type :user/logged-out
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :token-version (inc current-version)}})]
       :datastar/signals {:__redirect "/auth/sign-in"}})))

(defcommand :user set-password
  {:authorized? authenticated?}
  [{{:keys [password]} :command :as context}]
  (let [user-id (auth-user-id context)]
    {:command-result/events
     [(make-event {:type :user/password-set
                   :tags #{[:user user-id]}
                   :body {:user-id user-id
                          :password (hashers/derive password)}})]
     :datastar/signals {:__toast "Password updated."}}))

(defcommand :user verify-email
  {:authorized? (constantly true)}
  [{{:keys [verification-token]} :command :keys [jwt-secret] :as context}]
  (try
    (let [payload (jwt/unsign {:token verification-token :secret jwt-secret})
          user-id (parse-uuid (:user-id payload))
          user (rm/user context user-id)
          pending-token (rm/pending-email-verification-token context user-id)]
      (cond
        (nil? user)
        (not-found "Email verification link is invalid.")

        (nil? pending-token)
        (conflict "Email is already verified.")

        (not= verification-token pending-token)
        (conflict "Email verification link is invalid.")

        :else
        {:command-result/events
         [(make-event {:type :user/email-verified
                       :tags #{[:user user-id]}
                       :body {:user-id user-id
                              :email-address (:user/email-address user)}})]
         :datastar/signals {:__toast "Email verified."
                            :__redirect "/auth/sign-in"}}))
    (catch Exception _e
      (conflict "Email verification link is invalid."))))

(defcommand :user request-password-reset
  {:authorized? (constantly true)}
  [{{:keys [email-address]} :command :as context}]
  (if-let [user (rm/user-by-email context email-address)]
    (let [reset-token (make-reset-token context user)
          user-id (:user/id user)]
      {:command-result/events
       [(make-event {:type :user/password-reset-requested
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :email-address (:user/email-address user)
                            :reset-token reset-token}})]
       :datastar/signals {:__toast "If that account exists, a reset link has been sent."}})
    {:datastar/signals {:__toast "If that account exists, a reset link has been sent."}}))

(defcommand :user reset-password
  {:authorized? (constantly true)}
  [{{:keys [reset-token password]} :command :keys [jwt-secret] :as context}]
  (try
    (let [payload (jwt/unsign {:token reset-token :secret jwt-secret})
          user-id (parse-uuid (:user-id payload))
          pending-token (rm/pending-reset-token context user-id)]
      (cond
        (nil? pending-token)
        (conflict "Password reset link is invalid.")

        (not= reset-token pending-token)
        (conflict "Password reset link has already been used.")

        :else
        {:command-result/events
         [(make-event {:type :user/password-reset
                       :tags #{[:user user-id]}
                       :body {:user-id user-id
                              :password (hashers/derive password)}})]
         :datastar/signals {:__toast "Password reset."}}))
    (catch Exception _e
      (conflict "Password reset link is invalid."))))
