(ns cjbarre.grain-todo-list.foundation.email
  (:refer-clojure :exclude [send])
  (:require [com.brunobonacci.mulog :as u]))

(defprotocol Email
  (send [this args]))

(defrecord LoggerEmail [sent]
  Email
  (send [_ args]
    (u/log ::email-sent
           :email/to (:to args)
           :email/subject (:subject args)
           :email/body-html (:body-html args))
    (when sent
      (swap! sent conj args))
    args))

(defn logger-email
  ([] (logger-email nil))
  ([sent]
   (->LoggerEmail sent)))
