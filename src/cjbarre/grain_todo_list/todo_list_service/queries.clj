(ns cjbarre.grain-todo-list.todo-list-service.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.todo-list-service.schemas :as schemas]
            [cjbarre.grain-todo-list.todo-list-service.ui :as ui]))

(defn workspace-data
  [ctx]
  {:buckets (into {} (map (fn [bucket] [bucket (vec (rm/tasks-for-bucket ctx bucket))]) schemas/buckets))
   :deferred (vec (rm/deferred-tasks ctx))
   :due-soon (vec (rm/due-soon-tasks ctx))
   :inactive (vec (rm/inactive-tasks ctx))
   :projects (vec (rm/active-projects ctx))
   :project-summaries (vec (rm/project-summaries ctx))
   :review-projects (vec (rm/active-project-summaries ctx))
   :projects-without-next-action (vec (rm/projects-without-next-action ctx))
   :review (rm/current-weekly-review ctx)})

(defquery :todo home-page
  {:authorized? (constantly true)
   :datastar/path "/"
   :datastar/title "Grain Todo"
   :grain/read-models {:todo/tasks 1
                       :todo/projects 1
                       :todo/weekly-review 1}}
  "Main GTD workspace."
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (ui/home-page data)}))

(defquery :todo tasks-page
  {:authorized? (constantly true)
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [bucket]} :query :as ctx}]
  (let [bucket (or bucket :inbox)
        tasks (vec (rm/tasks-for-bucket ctx bucket))]
    {:query/result {:bucket bucket :tasks tasks :projects (vec (rm/active-projects ctx))}
     :datastar/hiccup (ui/tasks-page {:bucket bucket :tasks tasks :projects (vec (rm/active-projects ctx))})}))

(defquery :todo task-page
  {:authorized? (constantly true)
   :datastar/path "/task"
   :datastar/title "Task"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [task-id]} :query :as ctx}]
  (let [task (get (rm/all-tasks ctx) task-id)
        projects (vec (rm/active-projects ctx))]
    {:query/result {:task task :projects projects}
     :datastar/hiccup (ui/task-page {:task task :projects projects})}))

(defquery :todo projects-page
  {:authorized? (constantly true)
   :datastar/path "/projects"
   :datastar/title "Projects"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [projects (vec (rm/project-summaries ctx))]
    {:query/result {:projects projects}
     :datastar/hiccup (ui/projects-page {:projects projects})}))

(defquery :todo project-page
  {:authorized? (constantly true)
   :datastar/path "/project"
   :datastar/title "Project"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [project-id]} :query :as ctx}]
  (let [project (get (rm/all-projects ctx) project-id)
        tasks (vec (rm/tasks-for-project ctx project-id))]
    {:query/result {:project (when project
                               (assoc project :task-counts (rm/project-task-counts ctx project-id)))
                    :tasks tasks
                    :projects (vec (rm/active-projects ctx))}
     :datastar/hiccup (ui/project-page {:project (when project
                                                   (assoc project :task-counts (rm/project-task-counts ctx project-id)))
                                        :tasks tasks
                                        :projects (vec (rm/active-projects ctx))})}))

(defquery :todo review-page
  {:authorized? (constantly true)
   :datastar/path "/review"
   :datastar/title "Weekly Review"
   :grain/read-models {:todo/tasks 1 :todo/projects 1 :todo/weekly-review 1}}
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (ui/review-page data)}))

(defquery :todo dev-gallery-page
  {:authorized? (constantly true)
   :datastar/path "/dev/gallery"
   :datastar/title "Dev UI Gallery"}
  [_ctx]
  {:query/result {}
   :datastar/hiccup (ui/dev-gallery-page)})
