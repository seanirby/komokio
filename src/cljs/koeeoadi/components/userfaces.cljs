(ns koeeoadi.components.userfaces
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [koeeoadi.util :as util]
            [koeeoadi.config :as config]
            [koeeoadi.reconciler :refer [reconciler]]))

(declare user-faces-comp)

(defn face-update [comp props]
  (om/transact! comp `[(user-face/update ~props)]))

(defn face-edit-name [comp face-name new-name needs-focus]
  (om/transact! comp `[(user-face/update
                         {:face/name      ~face-name
                          :face/name-temp ~new-name})])
  (when needs-focus
    (om/update-state! comp assoc :needs-focus true)))

(defn face-add [comp]
  (let [{user-faces :user-faces/list} (om/props comp)
        faces-sorted (sort-by #(- (:face/id %)) user-faces)
        new-id       (inc (:face/id (first faces-sorted)))
        new-name     (str "new-face-" new-id)]
    (om/transact! comp
      `[(user-face/add {:face/id        ~new-id
                        :face/name      ~new-name
                        :face/editor    :emacs
                        :face/color-bg  nil
                        :face/color-fg  nil
                        :face/bold      false
                        :face/italic    false
                        :face/underline false})])))

(defn face-remove [comp]
  (om/transact! comp `[(user-face/remove) :user-faces]))

(defn color-change [comp color-type e]
  (let [{face-name    :face/name}    (om/props comp)
        {colors-by-id :colors/by-id} (om/get-computed comp)

        value (util/target-value e)
        color (if (= value "None")
                nil
                [:colors/by-id (.parseInt js/window
                                 (util/target-value e))])]
    (face-update comp {:face/name face-name
                       color-type color})))

(defn color-option [{:keys [color/hex color/id] :as color} current-color]
  (let [selected? (= current-color color)]
    (dom/option
      #js {:style     #js {:backgroundColor hex}
           :selected  selected?
           :value     id}
      hex)))

(defui UserFace
  static om/Ident
  (ident [this {:keys [face/name]}]
    [:user-faces/by-name name])

  static om/IQuery
  (query [this]
    [:colors/by-id
     :face/id
     :face/name
     :face/name-temp
     :face/bold
     :face/italic
     :face/underline
     :face/editor
     {:face/color-bg [:color/id :color/hex]}
     {:face/color-fg [:color/id :color/hex]}])

  Object
  (componentDidUpdate [this prev-props prev-state]
    (when (om/get-state this :needs-focus)
      (let [node (dom/node this "editField")
            len  (.. node -value -length)]
        (.focus node)
        (.setSelectionRange node len len))
      (om/update-state! this assoc :needs-focus nil)))

  (render [this]
    (let [{id        :face/id
           name      :face/name
           name-temp :face/name-temp
           bold      :face/bold
           italic    :face/italic
           color-bg  :face/color-bg
           color-fg  :face/color-fg
           underline :face/underline
           editor    :face/editor}   (om/props this)

          colors-by-id (:colors/by-id (om/get-computed this))
          colors-list  (vals colors-by-id)
          bg-hex       (:color/hex color-bg)
          fg-hex       (:color/hex color-fg)
          editors      (keys config/editor-file-map)]
      (dom/tr nil
        (dom/td nil
          (dom/input #js {:value     name-temp
                          :onBlur    #(when (and (not= name name-temp)
                                              (util/valid-face-name? name-temp))
                                        (om/transact! this `[(user-face/change-name) :user-faces]))
                          :onKeyDown #(when (and (not= name name-temp)
                                              (util/valid-face-name? name-temp) (= 13 (util/keycode %)))
                                        (om/transact! this `[(user-face/change-name) :user-faces]))
                          :onChange  #(face-edit-name this name (util/target-value %) false)
                          :style     (util/display name-temp)
                          :ref       "editField"})
          (dom/div #js {:className "row"
                        :onClick   #(face-edit-name this name name true)
                        :style     (util/display (not name-temp))}
            (dom/span nil name)
            (dom/button #js {:className "inline-button"}
              (dom/i #js {:className "fa fa-edit fa-2x"}))))

        (dom/td nil
          (apply dom/select
            #js {:style    #js {:backgroundColor (or bg-hex nil)}
                 :onChange #(color-change this :face/color-bg %)}
            (dom/option #js {:value "None"} "None")
            (map #(color-option % color-bg) colors-list)))

        (dom/td nil
          (apply dom/select
            #js {:style    #js {:backgroundColor (or fg-hex nil)}
                 :onChange #(color-change this :face/color-fg %)}
            (dom/option #js {:value "None"} "None")
            (map #(color-option % color-fg) colors-list)))

        ;; BOLD
        (dom/td nil
          (dom/input
            #js {:type    "checkbox"
                 :checked bold
                 :onClick #(face-update this {:face/name name :face/bold (util/target-checked %)})}))
        ;; ITALIC
        (dom/td nil
          (dom/input
            #js {:type    "checkbox"
                 :checked italic
                 :onClick #(face-update this {:face/name name :face/italic (util/target-checked %)})}))
        ;; UNDERLINE
        (dom/td nil
          (dom/input
            #js {:type    "checkbox"
                 :checked underline
                 :onClick #(face-update this {:face/name name :face/underline (util/target-checked %)})}))

        (dom/td nil
          (apply dom/select #js {:onChange #(face-update this {:face/name   name
                                                               :face/editor (keyword (util/target-value %))})}
            (map #(util/option (clojure.core/name %) (clojure.core/name editor)) editors)))

        (dom/td nil
          (dom/button #js {:onClick #(face-remove this)}
            (dom/i #js {:className "fa fa-close fa-2x"})))))))

(def user-face (om/factory UserFace {:keyfn :face/name}))

(defui UserFaces
  static om/IQuery
  (query [this]
    [:colors/by-id
     {:user-faces/list (om/get-query UserFace)}])

  Object
  (render [this]
    (let [{colors-by-id    :colors/by-id
           user-faces-list :user-faces/list}  (om/props this)

          {:keys [active]} (om/get-state this)]
      (dom/div #js {:id "user-faces-modal"
                    :className (str "modal " (if active "" "hide"))}
        (dom/div #js {:className "modal-content-container"}
          (dom/div #js {:className "modal-content"}
            (dom/h5
              #js {:className "modal-title"}
              "User Faces")
            (dom/button
              #js {:onClick #(face-add this)}
              "Add Face")
            (dom/div #js {:id          "user-faces-scroll-container"
                          :cellSpacing 0
                          :cellPadding 0
                          :border      0}
              (dom/table #js {:id "user-faces-container"
                              :cellPadding 0
                              :cellSpacing 0}
                (apply dom/tbody nil
                  (dom/tr nil
                    (dom/td nil "Name")
                    (dom/td nil "Background")
                    (dom/td nil "Foreground")
                    (dom/td nil "Bold")
                    (dom/td nil "Italic")
                    (dom/td nil "Underline")
                    (dom/td nil "Editor")
                    (dom/td nil ""))
                  ;; TODO is it the right thing to be sorting here?
                  (map #(user-face (om/computed % {:colors/by-id colors-by-id})) (sort-by :face/id user-faces-list)))))
            (dom/button #js {:className "modal-close-button"
                             :onClick #(om/update-state! this assoc :active false)} "CLOSE")))))))

(def user-faces (om/factory UserFaces))

(defn user-faces-comp []
  (om/class->any reconciler UserFaces))
