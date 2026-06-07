(ns cjbarre.grain-todo-list.user-service-test
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.datastar.interface :as ds]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [buddy.hashers :as hashers]
            [cjbarre.auth-interceptors :as auth]
            [cjbarre.grain-todo-list.email :as email]
            [cjbarre.grain-todo-list.jwt :as jwt]
            [cjbarre.grain-todo-list.user-service.commands]
            [cjbarre.grain-todo-list.user-service.queries]
            [cjbarre.grain-todo-list.user-service.read-models :as rm]
            [cjbarre.grain-todo-list.user-service.schemas]
            [cjbarre.grain-todo-list.user-service.todo-processors]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [cognitect.anomalies :as anom]
            [io.pedestal.http.sse :as sse]
            [malli.core :as m])
  (:import [java.time OffsetDateTime]))

(def now (OffsetDateTime/parse "2026-05-22T12:00:00Z"))

(defn test-context
  []
  (let [event-store (es/start {:conn {:type :in-memory}})
        cache (kv/start
               (lmdb/->KV-Store-LMDB
                {:storage-dir (str "/tmp/grain-user-test-" (random-uuid))
                 :db-name "test"}))
        sent-emails (atom [])]
    {:event-store event-store
     :cache cache
     :tenant-id (random-uuid)
     :jwt-secret "test-secret"
     :app-base "http://localhost:8080"
     :email-from "noreply@example.test"
     :email-client (email/logger-email sent-emails)
     :sent-emails sent-emails
     :command-registry (cp/global-command-registry)
     :query-registry (qp/global-query-registry)
     ::stop (fn []
              (kv/stop cache)
              (es/stop event-store))}))

(defn with-context
  [f]
  (let [ctx (test-context)]
    (try
      (f ctx)
      (finally
        ((::stop ctx))))))

(use-fixtures :each
  (fn [f]
    (rmp/l1-clear!)
    (f)
    (rmp/l1-clear!)))

(defn command
  [name fields]
  (merge {:command/id (random-uuid)
          :command/name name
          :command/timestamp now}
         fields))

(defn process!
  [ctx name fields]
  (cp/process-command (assoc ctx :command (command name fields))))

(defn query
  [name fields]
  (merge {:query/id (random-uuid)
          :query/name name
          :query/timestamp now}
         fields))

(defn run-query
  [ctx name fields]
  (qp/process-query (assoc ctx :query (query name fields))))

(defn event-of-type
  [result event-type]
  (some #(when (= event-type (:event/type %)) %) (:command-result/events result)))

(defn anomaly?
  [result]
  (contains? result ::anom/category))

(defn verify-token
  [ctx token]
  (try
    (let [claims (jwt/unsign {:token token :secret (:jwt-secret ctx)})
          user-id (parse-uuid (:user-id claims))]
      (when (= (get claims :token-version 0)
               (rm/token-version ctx user-id))
        claims))
    (catch Exception _ nil)))

(deftest user-schema-test
  (testing "kept auth schemas validate"
    (is (m/validate :user/sign-up
                    (command :user/sign-up
                             {:email-address "user@example.com"
                              :password "ValidPass1"
                              :confirm-password "ValidPass1"})))
    (is (m/validate :user/login
                    (command :user/login
                             {:email-address "user@example.com"
                              :password "ValidPass1"})))
    (is (m/validate :user/reset-password
                    (command :user/reset-password
                             {:reset-token "token"
                              :password "ValidPass1"})))
    (is (m/validate :user/verify-email
                    (command :user/verify-email
                             {:verification-token "token"})))
    (is (not (m/validate :user/sign-up
                         (command :user/sign-up
                                  {:email-address "bad"
                                   :password "short"}))))))

(deftest sign-up-login-and-logout-test
  (with-context
   (fn [ctx]
     (let [sign-up (process! ctx :user/sign-up
                             {:email-address "USER@Example.com"
                              :password "ValidPass1"
                              :confirm-password "ValidPass1"})
           signed-up (event-of-type sign-up :user/signed-up)
           verification-requested (event-of-type sign-up :user/email-verification-requested)
           verification-token (:verification-token verification-requested)
           user-id (:user-id signed-up)]
       (is signed-up)
       (is verification-requested)
       (is (= "user@example.com" (:email-address signed-up)))
       (is (= user-id (:user-id verification-requested)))
       (is (= verification-token
              (rm/pending-email-verification-token ctx user-id)))
       (is (false? (:user/email-verified (rm/user ctx user-id))))
       (is (true? (:user/active (rm/user ctx user-id))))
       (is (:valid (hashers/verify "ValidPass1" (:password signed-up))))
       (is (= ::anom/conflict
              (::anom/category
               (process! ctx :user/sign-up
                         {:email-address "user@example.com"
                          :password "ValidPass1"
                          :confirm-password "ValidPass1"}))))
       (is (= ["An account already exists for this email."]
              (:email-address (:error/explain
                               (process! ctx :user/sign-up
                                         {:email-address "user@example.com"
                                          :password "ValidPass1"
                                          :confirm-password "ValidPass1"})))))

       (testing "password confirmation mismatch is field keyed"
         (let [mismatch (process! ctx :user/sign-up
                                  {:email-address "new@example.com"
                                   :password "ValidPass1"
                                   :confirm-password "Different1"})]
           (is (= ::anom/conflict (::anom/category mismatch)))
           (is (= ["Passwords do not match."]
                  (:confirm-password (:error/explain mismatch))))))

       (testing "login returns a valid auth token before email verification"
         (let [login (process! ctx :user/login
                               {:email-address "user@example.com"
                                :password "ValidPass1"})
               claims (jwt/unsign {:token (:auth/token login)
                                   :secret (:jwt-secret ctx)})]
           (is (event-of-type login :user/logged-in))
           (is (= (str user-id) (:user-id claims)))
           (is (= 0 (:token-version claims)))
           (is (= claims (verify-token ctx (:auth/token login))))))

       (testing "email verification marks the address verified"
         (let [verified (process! ctx :user/verify-email
                                  {:verification-token verification-token})
               reused (process! ctx :user/verify-email
                                {:verification-token verification-token})]
           (is (event-of-type verified :user/email-verified))
           (is (true? (:user/email-verified (rm/user ctx user-id))))
           (is (nil? (rm/pending-email-verification-token ctx user-id)))
           (is (= ::anom/conflict (::anom/category reused)))))

       (testing "login rejects bad credentials"
         (let [wrong-password (process! ctx :user/login
                                        {:email-address "user@example.com"
                                         :password "WrongPass1"})
               unknown-email (process! ctx :user/login
                                       {:email-address "missing@example.com"
                                        :password "WrongPass1"})]
           (is (= ::anom/conflict (::anom/category wrong-password)))
           (is (= ["Invalid credentials."]
                  (:password (:error/explain wrong-password))))
           (is (= ::anom/conflict (::anom/category unknown-email)))
           (is (= ["Invalid credentials."]
                  (:password (:error/explain unknown-email))))))

       (testing "logout invalidates existing tokens"
         (let [login (process! ctx :user/login
                               {:email-address "user@example.com"
                                :password "ValidPass1"})
               token (:auth/token login)
               logout (process! (assoc ctx :auth-claims {:user-id user-id})
                                :user/logout
                                {})]
           (is (event-of-type logout :user/logged-out))
           (is (= 1 (rm/token-version ctx user-id)))
           (is (nil? (verify-token ctx token)))))))))

(deftest email-verification-email-test
  (with-context
    (fn [ctx]
      (let [sign-up (process! ctx :user/sign-up
                              {:email-address "verify@example.com"
                               :password "ValidPass1"
                               :confirm-password "ValidPass1"})
            event (event-of-type sign-up :user/email-verification-requested)
            verification-token (:verification-token event)
            processor-var (resolve 'cjbarre.grain-todo-list.user-service.todo-processors/user-email-verification-email)
            result ((deref processor-var)
                    (assoc ctx :event event))]
        ((:result/effect result))
        (is (= 1 (count @(:sent-emails ctx))))
        (is (string/includes? (-> @(:sent-emails ctx) first :body-html)
                              "/auth/verify-email?verification-token="))
        (is (string/includes? (-> @(:sent-emails ctx) first :body-html)
                              verification-token))))))

(deftest password-reset-test
  (with-context
    (fn [ctx]
      (let [sign-up (process! ctx :user/sign-up
                              {:email-address "reset@example.com"
                               :password "ValidPass1"
                               :confirm-password "ValidPass1"})
            user-id (:user-id (event-of-type sign-up :user/signed-up))
            request (process! ctx :user/request-password-reset
                              {:email-address "reset@example.com"})
            event (event-of-type request :user/password-reset-requested)
            reset-token (:reset-token event)]
        (is (= user-id (:user-id event)))
        (is (= reset-token (rm/pending-reset-token ctx user-id)))

        (testing "reset event can be delivered by logger email"
          (let [processor-var (resolve 'cjbarre.grain-todo-list.user-service.todo-processors/user-password-reset-email)
                result ((deref processor-var)
                        (assoc ctx :event event))]
            ((:result/effect result))
            (is (= 1 (count @(:sent-emails ctx))))
            (is (string/includes? (-> @(:sent-emails ctx) first :body-html)
                                  reset-token))))

        (testing "reset token is one-time use"
          (let [reset (process! ctx :user/reset-password
                                {:reset-token reset-token
                                 :password "NewPass123"})]
            (is (event-of-type reset :user/password-reset))
            (is (nil? (rm/pending-reset-token ctx user-id)))
            (is (= ::anom/conflict
                   (::anom/category
                    (process! ctx :user/reset-password
                              {:reset-token reset-token
                               :password "NewPass123"}))))))))))

(defn action-context
  [ctx command-name payload]
  {:request {:body (json/write-str
                    {"payload" (assoc (into {} (map (fn [[k v]] [(name k) v]) payload))
                                      "command/name" (str command-name))})}
   :grain/additional-context (select-keys ctx [:auth-claims])})

(deftest datastar-action-cookie-test
  (with-context
    (fn [ctx]
      (let [sign-up (process! ctx :user/sign-up
                              {:email-address "cookie@example.com"
                               :password "ValidPass1"
                               :confirm-password "ValidPass1"})
            user-id (:user-id (event-of-type sign-up :user/signed-up))
            handler (ds/action-handler ctx {})]
        (with-redefs [sse/start-stream
                      (fn [callback pedestal-context _heartbeat-delay]
                        (let [event-ch (async/chan 1)]
                          (callback event-ch {:request (:request pedestal-context)})
                          (assoc pedestal-context
                                 :response {:status 200}
                                 :event (async/poll! event-ch))))]
          (let [entered ((:enter handler)
                         (action-context ctx :user/login
                                         {:email-address "cookie@example.com"
                                          :password "ValidPass1"}))
                left ((:leave auth/auth-cookie-interceptor) entered)]
            (is (= :user/login (get-in entered [:grain/command :command/name])))
            (is (not (anomaly? (:grain/command-result entered))))
            (is (string? (get-in left [:response :cookies "auth-token" :value]))))

          (testing "failed login does not set cookie"
            (let [entered ((:enter handler)
                           (action-context ctx :user/login
                                           {:email-address "cookie@example.com"
                                            :password "WrongPass1"}))
                  left ((:leave auth/auth-cookie-interceptor) entered)]
              (is (anomaly? (:grain/command-result entered)))
              (is (nil? (get-in left [:response :cookies "auth-token"])))))

          (testing "logout clears cookie"
            (let [entered ((:enter handler)
                           (assoc (action-context ctx :user/logout {})
                                  :grain/additional-context {:auth-claims {:user-id user-id}}))
                  left ((:leave auth/auth-cookie-interceptor) entered)]
              (is (= :user/logout (get-in entered [:grain/command :command/name])))
              (is (= "" (get-in left [:response :cookies "auth-token" :value])))
              (is (= 0 (get-in left [:response :cookies "auth-token" :max-age]))))))))))

(deftest auth-pages-test
  (with-context
    (fn [ctx]
      (doseq [query-name [:user/sign-in-page
                         :user/sign-up-page
                         :user/forgot-password-page
                         :user/verify-email-page
                         :user/reset-password-page]]
        (let [result (run-query ctx query-name {:verification-token "token"
                                                :reset-token "token"})
              leaves (tree-seq coll? seq (:datastar/hiccup result))
              attrs (filter map? leaves)]
          (is (= :div#app (first (:datastar/hiccup result))))
          (is (some #(contains? % :data-on:submit__prevent) attrs))
          (when (= query-name :user/sign-in-page)
            (is (some #(contains? % :data-on-signal-patch) attrs))
            (is (some #(and (map? %)
                            (string? (:data-text %))
                            (string/includes? (:data-text %) "password-error"))
                      attrs)))
          (is (not (some #(and (string? %) (re-find #"flash|staff|admin|role|permission|magic" %))
                         leaves))))))))
