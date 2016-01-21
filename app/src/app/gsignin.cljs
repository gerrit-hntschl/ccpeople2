(ns app.gsignin
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r]
            [app.domain :as domain]
    ;            [material-ui.core :as ui :include-macros true]
            [cljs.core.async :refer [put! chan <! >! buffer]]
            [clojure.string :as str]))

(enable-console-print!)

(defn load-gapi-auth2 []
  (let [c (chan)]
    (.load js/gapi "auth2" #(put! c true))
    c))

(defn auth-instance []
  (.getAuthInstance js/gapi.auth2))

(defn get-google-id-token []
  (-> (auth-instance) .-currentUser .get .getAuthResponse .-id_token))

(defn get-auth-token []
  (-> (auth-instance) .-currentUser .get .getAuthResponse .-access_token))

(defn handle-user-change
  [u]
  (let [profile (.getBasicProfile u)]
    (if (.isSignedIn u)
      (swap! domain/app-state
             (partial merge-with merge)
             {:user
              {:user/full-name  (.getName profile)
               :user/image-url  (.getImageUrl profile)
               :user/id-token   (get-google-id-token)
               :user/access-token (get-auth-token)
               :user/signed-in? (.isSignedIn u)}})
      (swap! domain/app-state
             dissoc
             :user))))

(defonce _ (go
             (<! (load-gapi-auth2))
             (.init js/gapi.auth2
                    #js {"client_id" "493824973703-h2ambsalvru64vmegfnebmobp3sel4c7.apps.googleusercontent.com"
                         "scope"     "profile email openid https://www.googleapis.com/auth/admin.directory.user.alias.readonly"})
             (let [current-user (.-currentUser (auth-instance))]
               (.listen current-user handle-user-change))))

(defn signed-in? [app-state]
  (boolean (get-in app-state [:user :user/signed-in?])))

(defn initials [{:keys [user/full-name]}]
  (->> (str/split full-name " ")
       (map first)
       (map str/upper-case)
       (str/join)))

(defn signed-in-component []
  (let [state @domain/app-state
        user (:user state)
        image-url (-> user :user/image-url)]
    (when (signed-in? state)
      [:button
      (cond-> {:on-click #(.signOut (auth-instance))
               :style {:cursor "pointer"}} image-url (assoc :src image-url))
      (when-not image-url (initials user))])))

(defn sign-in-component []
  (let [state @domain/app-state]
    (if-not (signed-in? state)
      [:div
       [:button {:on-click #(.signIn (auth-instance))
                 :style    {:margin "16px 32px 0px 32px"}}
        "Sign in"]]
      [:div
       [:button {:label    "Logout"
                 :on-click #(.signOut (auth-instance))}
        "Logout " (:user/full-name (:user state))]]
      )))




