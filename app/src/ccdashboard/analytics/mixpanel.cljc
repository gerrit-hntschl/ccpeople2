(ns ccdashboard.analytics.mixpanel)

(defn identify [identifier]
  #?(:cljs (.identify js/mixpanel identifier)))

(defn track [event-name & [other-opts]]
  #?(:cljs (if other-opts
             (.track js/mixpanel event-name (clj->js other-opts))
             (.track js/mixpanel event-name))))

(defn set-env [env]
  {:pre [(keyword? env)]}
  #?(:cljs (.register_once js/mixpanel #js{"env" (name env)})))
