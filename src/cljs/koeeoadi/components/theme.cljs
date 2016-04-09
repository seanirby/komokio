(ns koeeoadi.components.theme
  (:require [goog.dom :as gdom]
            [goog.events :as gevents]
            [cljs.reader :as reader]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.pprint :as pprint]
            [koeeoadi.reconciler :refer [reconciler]]
            [koeeoadi.util :as util]
            [koeeoadi.config :as config]
            [koeeoadi.themebuilder :as tb]))

(defn theme-name-begin-edit [comp]
  (om/transact! comp `[(theme/update {:theme/name-temp ~(:theme/name (om/props comp))})])
  (om/update-state! comp assoc :needs-focus true))

(defn theme-rename [comp e]
  (let [input (util/target-value e)]
    (when (and
            (util/no-keycode-or-keycode-is-valid? (util/keycode e))
            (util/valid-file-name? input))
      (om/transact! comp
        `[(theme/rename
            {:new-name  ~input
             :prev-name ~(:theme/name (om/props comp))})]))))

(defn theme-update [comp e]
  (let [input (util/target-value e)]
    (om/transact! comp
      `[(theme/update {:theme/name-temp ~input})])))

(defn select-theme [comp e]
  (let [{theme-map    :theme/map
         active-color :palette-widget/active-color} (om/props comp)
        theme-name (util/target-value e)]
    (om/transact! comp `[(state/merge
                           ~(assoc (get theme-map theme-name)
                              :theme/name
                              theme-name

                              :palette-widget/face-classes-by-color-type
                              (util/faces-to-colorize
                                (get-in theme-map [theme-name :faces/list])
                                (:color-id active-color))))
                         :theme/map])))

(defn new-theme [comp]
  (om/transact! comp `[(theme/new) :theme/map])
  (om/update-state! comp assoc :needs-focus true))

(defn encode-props [props]
  (str "data:text/plain;charset=utf-8;base64,"
    (.btoa js/window
      (pr-str props))))

(defn save-theme-file [comp current-theme]
  (let [link (gdom/getElement "download-link")]
    (aset link "download" (str current-theme ".koee"))
    (aset link "href" (encode-props
                        (select-keys @reconciler
                          [:theme/name  :colors/by-id
                           :colors/list :faces/by-name
                           :faces/list  :user-faces/list
                           :user-faces/by-name])))
    (.click link)))

(defn export-theme-file [current-theme editor]
  (let [link      (gdom/getElement "download-link")
        extension (editor config/editor-file-map)]
    (aset link "download" (str current-theme "-theme." extension))
    (aset link "href" (str "data:text/plain;charset=utf-8," (tb/build-theme editor)))
    (.click link)))

(defn file-list-to-cljs [js-col]
  (-> (clj->js [])
    (.-slice)
    (.call js-col)
    (js->clj)))

(defn load-theme [comp e]
  (let [result        (.. e -target -result)
        encoded-theme (aget (.split result ",") 1)
        decoded-theme (.atob js/window encoded-theme)
        theme         (reader/read-string decoded-theme)]
    (om/transact! comp `[(theme/load ~theme) :theme/map])))

(defn read-theme-file [comp e]
  (let [file-list   (file-list-to-cljs (.. e -target -files))
        file        (first file-list)
        file-reader (js/FileReader.)]
    (when file
      ;; TODO Add some kind of error handling
      ;; TODO look into why I could use set! instead
      (aset file-reader "onload" #(load-theme comp %))
      (.readAsDataURL file-reader file))))

(defn load-theme-file [e]
  (let [input (gdom/getElement "file-input")]
    (.click input)))

(defn theme-actions [comp]
  (dom/div #js {:className "row control-row"}
    (dom/div #js {:className "one-third column"}
      (dom/button #js {:className "inline-button"
                       :onClick #(new-theme comp)}
        (dom/i #js {:className "fa fa-file fa-3x"}))
      (dom/div nil "New"))
    (dom/div #js {:className "one-third column"}
      (dom/button #js {:className "inline-button"
                       :onClick #(save-theme-file comp
                                   (:theme/name (om/props comp)))}
        (dom/i #js {:className "fa fa-save fa-3x"}))
      (dom/div nil "Save"))
    (dom/div #js {:className "one-third column"}
      (dom/input  #js {:type "file"
                       :id "file-input"
                       :accept ".koee"
                       :onChange #(read-theme-file comp %)
                       :style #js {:display "none"}})
      (dom/button #js {:className "inline-button" :onClick load-theme-file}
        (dom/i #js {:className "fa fa-upload fa-3x"}))
      (dom/div nil "Load"))))

(defn theme-select-edit [comp]
  (let [{theme-name      :theme/name
         theme-name-temp :theme/name-temp
         theme-map       :theme/map} (om/props comp)]
    (dom/label nil "Choose theme:"
      (dom/div #js {:className "row control-row"}
        (apply dom/select
          #js {:id "theme-select"
               :onChange #(select-theme comp %)
               :style    (util/display (not theme-name-temp))}
          (map #(util/option % theme-name) (keys theme-map)))

        (dom/input #js {:id          "theme-name-input"
                        :onBlur      #(theme-rename comp %)
                        :onChange    #(theme-update comp %) 
                        :onKeyDown   #(theme-rename comp %)
                        :placeholder theme-name
                        :ref         "editField"
                        :value       (or theme-name-temp theme-name)
                        :style       (util/display theme-name-temp)})

        (dom/button #js {:className "inline-button"
                         :id        "theme-name-edit-button"
                         :onClick   #(theme-name-begin-edit comp)}

          (dom/i #js {:className "fa fa-edit fa-2x"}))))))

(defn theme-export-button [theme-name button-text]
  (dom/button
    #js {:className "export-button"
         :onClick   #(export-theme-file theme-name :emacs)}
    button-text))

(defn theme-export [comp]
  (let [theme-name (:theme/name (om/props comp))]
    (dom/label #js {:className "widget-label"} "Export to:"
      (theme-export-button theme-name "Emacs (GUI only)")
      (theme-export-button theme-name "Vim (GUI only)"))))

(defui Theme
  static om/IQuery
  (query [this]
    '[:theme/name-temp
      :theme/name
      :theme/map
      [:palette-widget/active-color _]])

  Object
  (componentDidUpdate [this prev-props prev-state]
    (when (om/get-state this :needs-focus)
      (let [node (dom/node this "editField")
            len  (.. node -value -length)]
        (.focus node)
        (.setSelectionRange node len len))
      (om/update-state! this assoc :needs-focus nil)))

  (render [this]
    (dom/div #js {:className "widget" :id "actions"}
      (util/widget-title "Theme")
      (theme-select-edit this)
      (theme-actions this)
      (theme-export this))))

(def theme (om/factory Theme))