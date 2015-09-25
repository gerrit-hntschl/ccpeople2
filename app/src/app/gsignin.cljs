(ns app.gsignin
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require  [reagent.core :as r]
             [ajax.core :as ajax]
             [cljs.core.async :refer [put! chan <! >! buffer]]))

(enable-console-print!)
(def user (r/atom {}))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn watch-user [watch-key handler-fn]
  (add-watch user watch-key (fn [_ _ old new]
                                (if (:token new)
                                  ;; todo post + CSRF protection
                                  (ajax/GET "/auth"
                                            {:params        (select-keys new [:token])
                                             :handler       handler-fn
                                             :error-handler error-handler
                                             :response-format :json
                                             :keywords? true})
                                  (handler-fn nil)))))

(defn load-gapi-auth2 []
  (let [c (chan)]
    (.load js/gapi "auth2" #(put! c true))
    c))

(defn auth-instance []
  (.getAuthInstance js/gapi.auth2))

(defn get-google-token []
  (-> (auth-instance) .-currentUser .get .getAuthResponse .-id_token))

(defn handle-user-change
  [u]
  (let [profile (.getBasicProfile u)]
    (reset! user
            {:name       (if profile (.getName profile) nil)
             :image-url  (if profile (.getImageUrl profile) nil)
             :token      (get-google-token)
             :signed-in? (.isSignedIn u)})))

(defonce _ (go
             (<! (load-gapi-auth2))
             (.init js/gapi.auth2
                    #js {"client_id" "493824973703-h2ambsalvru64vmegfnebmobp3sel4c7.apps.googleusercontent.com"
                         "scope"     "profile email openid"})
             (let [current-user (.-currentUser (auth-instance))]
               (.listen current-user handle-user-change))))

(defn sign-in []
  [:div
   (if-not (:signed-in? @user) [:a {:href "#" :on-click #(.signIn (auth-instance))} "Sign In"]
                               [:div
                                [:p
                                 [:strong (:name @user)]
                                 [:br]
                                 [:img {:src (:image-url @user)}]]
                                [:a {:href "#" :on-click #(.signOut (auth-instance))} "Sign Out"]])])




