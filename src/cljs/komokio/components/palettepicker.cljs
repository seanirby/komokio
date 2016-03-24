(ns komokio.components.palettepicker
  (:require [goog.style :as gstyle]
            [goog.dom.classes :as gclasses]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.pprint :as pprint]

            [komokio.components.palette :refer [Color]]
            [komokio.components.faceeditor :refer [Face]]
            [komokio.util :as util]))

(defui ColorOption
  static om/Ident
  (ident [this {:keys [color/name]}]
    [:colors/by-name name])

  static om/IQuery
  (query [this]
    [:db/id :color/name :color/rgb])

  Object
  (render [this]
    (let [{:keys [color/rgb]}         (om/props this)
          {:keys [colorHoverHandler
                  colorClickHandler]} (om/get-computed this)]
      (dom/button #js
        {:className    "color color-option"
         :onMouseEnter #(colorHoverHandler rgb)
         :onMouseLeave #(colorHoverHandler nil)
         :onMouseDown  #(colorClickHandler (om/props this))
         :style        #js {:backgroundColor rgb}}))))

(def color-option (om/factory ColorOption {:keyfn :db/id}))


(defui PalettePicker
  static om/IQuery
  (query [this]
    [:palette-picker/coordinates
     :palette-picker/active-face-property
     {:palette-picker/active-face (om/get-query Face)}
     {:colors/list (om/get-query Color)}])

  Object
  (componentInitState [this]
    {})

  (componentDidUpdate [this prev-props prev-state]
    (let [{:keys [face/name]} (:palette-picker/active-face (:palette-picker (om/props this)))]
      (if name
        (util/update-other-code-face-elements name #(gclasses/add % "code-temp-minimize"))
        (util/update-code-elements #(gclasses/remove % "code-temp-minimize")))))

  (render [this]
    (println "rendering palettepicker")
    (let [{:keys [colors/list
                  palette-picker/coordinates
                  palette-picker/active-face
                  palette-picker/active-face-property]} (:palette-picker (om/props this))

          css-property (if (= active-face-property :face/background) "background" "color")
          {face-name :face/name} active-face
          face-color-rgb (get-in active-face [active-face-property :color/rgb])

          colorClickHandler (fn [color]
                              (let [{:keys [db/id face/name]} active-face]
                                (om/transact! this `[(face/update
                                                       {:id       ~id
                                                        :name     ~name
                                                        :bg-or-fg ~active-face-property
                                                        :color    ~color}) ;;:face/name
                                                     ])))
          colorHoverHandler (fn [hover-color-rgb]
                              (let [prop (if (= :face/foreground active-face-property) "color" "background-color")]
                                (if hover-color-rgb
                                  (util/update-code-face-elements face-name #(gstyle/setStyle % prop hover-color-rgb))
                                  (util/update-code-face-elements face-name #(gstyle/setStyle % prop face-color-rgb)))))

          color-options-computed (map #(om/computed % {:colorHoverHandler colorHoverHandler
                                                       :colorClickHandler colorClickHandler}) list)]
      (when coordinates
        (apply
          dom/div
          #js {:id    "palette-picker"
               :style #js {:position "absolute"
                           :top      (:x coordinates)
                           :left     (:y coordinates)}}
          (map color-option color-options-computed))))))

(def palette-picker (om/factory PalettePicker))