(ns cjbarre.grain-todo-list.ui
  (:require [cjbarre.grain-todo-list.todo-list-service.ui :as service-ui]))

(def shell service-ui/shell)
(def home-page service-ui/home-page)
(def tasks-page service-ui/tasks-page)
(def task-page service-ui/task-page)
(def projects-page service-ui/projects-page)
(def project-page service-ui/project-page)
(def review-page service-ui/review-page)
(def dev-gallery-page service-ui/dev-gallery-page)
