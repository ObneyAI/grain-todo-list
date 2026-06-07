(ns cjbarre.grain-todo-list.service.todo-list-service.queries
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.foundation.auth-interceptors :as auth]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.service.todo-list-service.ui :as ui]))

(defn render
  [page]
  (ds-ui/hiccup page))

(defn current-user-id
  [ctx]
  (or (:current-user/id ctx)
      (auth/auth-user-id ctx)))

(defn authenticated?
  [ctx]
  (some? (current-user-id ctx)))

(defn with-current-user
  [ctx]
  (cond-> ctx
    (and (nil? (:current-user/id ctx)) (current-user-id ctx))
    (assoc :current-user/id (current-user-id ctx))))

(defn owns-task-query?
  [ctx]
  (and (authenticated? ctx)
       (if-let [task-id (get-in ctx [:query :task-id])]
         (contains? (rm/all-tasks (with-current-user ctx)) task-id)
         true)))

(defn owns-project-query?
  [ctx]
  (and (authenticated? ctx)
       (if-let [project-id (get-in ctx [:query :project-id])]
         (contains? (rm/all-projects (with-current-user ctx)) project-id)
         true)))

(defn workspace-data
  [ctx]
  {:tasks (vec (rm/active-tasks ctx))
   :due-soon (vec (rm/due-soon-tasks ctx))
   :inactive (vec (rm/inactive-tasks ctx))
   :projects (vec (rm/active-project-summaries ctx))
   :project-summaries (vec (rm/project-summaries ctx))
   :review-projects (vec (rm/active-project-summaries ctx))
   :projects-without-next-action (vec (rm/projects-without-next-action ctx))
   :review (rm/current-weekly-review ctx)})

(defquery :todo home-page
  {:authorized? authenticated?
   :datastar/path "/"
   :datastar/title "Grain Todo"
   :grain/read-models {:todo/tasks 1
                       :todo/projects 1
                       :todo/weekly-review 1}}
  "Main todo workspace."
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (render (ui/home-page data))}))

(defquery :todo tasks-page
  {:authorized? authenticated?
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [tasks (vec (rm/active-tasks ctx))
        projects (vec (rm/active-project-summaries ctx))]
    {:query/result {:tasks tasks :projects projects}
     :datastar/hiccup (render (ui/tasks-page {:tasks tasks :projects projects}))}))

(defquery :todo task-page
  {:authorized? owns-task-query?
   :datastar/path "/task"
   :datastar/title "Task"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [task-id]} :query :as ctx}]
  (let [task (get (rm/all-tasks ctx) task-id)
        projects (vec (rm/active-project-summaries ctx))]
    {:query/result {:task task :projects projects}
     :datastar/hiccup (render (ui/task-page {:task task :projects projects}))}))

(defquery :todo projects-page
  {:authorized? authenticated?
   :datastar/path "/projects"
   :datastar/title "Projects"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [projects (vec (rm/project-summaries ctx))]
    {:query/result {:projects projects}
     :datastar/hiccup (render (ui/projects-page {:projects projects}))}))

(defquery :todo project-page
  {:authorized? owns-project-query?
   :datastar/path "/project"
   :datastar/title "Project"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [project-id]} :query :as ctx}]
  (let [project (get (rm/all-projects ctx) project-id)
        tasks (vec (rm/tasks-for-project ctx project-id))
        projects (vec (rm/active-project-summaries ctx))]
    {:query/result {:project (when project
                               (assoc project :task-counts (rm/project-task-counts ctx project-id)))
                    :tasks tasks
                    :projects projects}
     :datastar/hiccup (render (ui/project-page {:project (when project
                                                           (assoc project :task-counts (rm/project-task-counts ctx project-id)))
                                                :tasks tasks
                                                :projects projects}))}))

(defquery :todo review-page
  {:authorized? authenticated?
   :datastar/path "/review"
   :datastar/title "Weekly Review"
   :grain/read-models {:todo/tasks 1 :todo/projects 1 :todo/weekly-review 1}}
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (render (ui/review-page data))}))

(defquery :todo dev-gallery-page
  {:authorized? (constantly true)
   :datastar/path "/dev/gallery"
   :datastar/title "Dev UI Gallery"}
  [_ctx]
  {:query/result {}
   :datastar/hiccup (render (ui/dev-gallery-page))})
