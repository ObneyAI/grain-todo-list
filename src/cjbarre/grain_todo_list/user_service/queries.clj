(ns cjbarre.grain-todo-list.user-service.queries
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.user-service.ui :as ui]))

(defn render
  [page]
  (ds-ui/hiccup page))

(defquery :user sign-in-page
  {:authorized? (constantly true)
   :datastar/path "/auth/sign-in"
   :datastar/title "Sign In"
   :datastar/fps 0}
  [_ctx]
  {:query/result {:page :sign-in}
   :datastar/hiccup (render (ui/sign-in-page))})

(defquery :user sign-up-page
  {:authorized? (constantly true)
   :datastar/path "/auth/sign-up"
   :datastar/title "Create Account"
   :datastar/fps 0}
  [_ctx]
  {:query/result {:page :sign-up}
   :datastar/hiccup (render (ui/sign-up-page))})

(defquery :user forgot-password-page
  {:authorized? (constantly true)
   :datastar/path "/auth/forgot-password"
   :datastar/title "Forgot Password"
   :datastar/fps 0}
  [_ctx]
  {:query/result {:page :forgot-password}
   :datastar/hiccup (render (ui/forgot-password-page))})

(defquery :user verify-email-page
  {:authorized? (constantly true)
   :datastar/path "/auth/verify-email"
   :datastar/title "Verify Email"
   :datastar/fps 0}
  [{{:keys [verification-token]} :query}]
  {:query/result {:page :verify-email}
   :datastar/hiccup (render (ui/verify-email-page {:verification-token verification-token}))})

(defquery :user reset-password-page
  {:authorized? (constantly true)
   :datastar/path "/auth/reset-password"
   :datastar/title "Reset Password"
   :datastar/fps 0}
  [{{:keys [reset-token]} :query}]
  {:query/result {:page :reset-password}
   :datastar/hiccup (render (ui/reset-password-page {:reset-token reset-token}))})
