(ns cjbarre.grain-todo-list.foundation.jwt
  (:require [ai.obney.grain.time.interface :as time]
            [buddy.sign.jwt :as jwt]
            [tick.core :as t]))

(defn sign
  [{[n unit :as expire-in] :expire-in
    :keys [payload secret does-not-expire]}]
  (jwt/sign
   (cond-> payload
     true (assoc :iat (->> (time/now) t/instant t/long))
     true (assoc :exp (-> (t/>> (time/now) (t/new-duration 1 :seconds))
                          t/instant
                          t/long))
     does-not-expire (dissoc :exp)
     expire-in (assoc :exp (-> (t/>> (time/now) (t/new-duration n unit))
                               t/instant
                               t/long)))
   secret))

(defn unsign
  [{:keys [token secret]}]
  (jwt/unsign token secret))
