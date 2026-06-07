(ns cjbarre.grain-todo-list.foundation.auth-interceptors
  (:require [cognitect.anomalies :as anom]
            [io.pedestal.interceptor :as interceptor]))

(defn authenticated?
  [ctx]
  (some? (:auth-claims ctx)))

(defn auth-user-id
  [ctx]
  (get-in ctx [:auth-claims :user-id]))

(defn user-tag
  [user-id]
  [:user user-id])

(defn- parse-uuid-value
  [value]
  (cond
    (uuid? value) value
    (string? value) (try
                      (java.util.UUID/fromString value)
                      (catch Exception _ value))
    :else value))

(defn normalize-claims
  [claims]
  (when (map? claims)
    (cond-> claims
      (contains? claims :user-id) (update :user-id parse-uuid-value)
      (contains? claims :tenant-id) (update :tenant-id parse-uuid-value))))

(defn extract-auth-cookie-interceptor
  [{:keys [verify-token cookie-name]
    :or {cookie-name "auth-token"}}]
  (interceptor/interceptor
   {:name ::extract-auth-cookie
    :enter
    (fn [ctx]
      (let [token (get-in ctx [:request :cookies cookie-name :value])
            claims (when (and token verify-token)
                     (try
                       (normalize-claims (verify-token token))
                       (catch Exception _ nil)))]
        (cond-> ctx
          claims (assoc-in [:grain/additional-context :auth-claims] claims))))}))

(def current-user-context-interceptor
  (interceptor/interceptor
   {:name ::current-user-context
    :enter
    (fn [ctx]
      (let [user-id (get-in ctx [:grain/additional-context :auth-claims :user-id])]
        (cond-> ctx
          user-id (assoc-in [:grain/additional-context :current-user/id] user-id))))}))

(defn anomaly?
  [result]
  (contains? result ::anom/category))

(defn set-cookie
  [ctx token]
  (assoc-in ctx [:response :cookies "auth-token"]
            {:value token
             :http-only true
             :secure false
             :same-site :lax
             :path "/"}))

(defn clear-cookie
  [ctx]
  (assoc-in ctx [:response :cookies "auth-token"]
            {:value ""
             :http-only true
             :secure false
             :same-site :lax
             :path "/"
             :max-age 0}))

(def auth-cookie-interceptor
  (interceptor/interceptor
   {:name ::auth-cookie
    :leave
    (fn [ctx]
      (let [command (:grain/command ctx)
            result (:grain/command-result ctx)]
        (cond
          (or (nil? command) (anomaly? result))
          ctx

          (and (= :user/login (:command/name command))
               (:auth/token result))
          (set-cookie ctx (:auth/token result))

          (= :user/logout (:command/name command))
          (clear-cookie ctx)

          :else ctx)))}))
