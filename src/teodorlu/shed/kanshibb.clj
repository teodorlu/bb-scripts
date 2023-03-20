(ns teodorlu.shed.kanshibb
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.edn :as edn]
   clojure.java.shell
   clojure.pprint
   [clojure.string :as str]))

;; why data?
;;
;;     because then we can build UI for things we want.
;;
;; why code?
;;
;;     because then we can remove boilerplate and allow for extension.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; KANSHIBB CONFIG FORMAT

;; example pure data config

{:config
 [{:profile
   [{:description "laptop right of 34inch monitor"}
    {:output "Samsung Electric Company C34H89x H4ZN501754" :position "0,0"}
    {:output "Chimei Innolux Corporation 0x14F2 Unknown" :position "3440,360"}]}
  {:profile
   [{:description "Laptop only"}
    {:output "Chimei Innolux Corporation 0x14F2 Unknown" :position "0,0"}]}
  {:profile
   [{:description "laptop below 34inch monitor"}
    {:output "Samsung Electric Company C34H89x H4ZN501754" :position "0,0"}
    {:output "Chimei Innolux Corporation 0x14F2 Unknown" :position "760,1440"}]}]}

;; example data & code config
;; Note its quotation.

(quote
 {:init (do (def laptop-output "Chimei Innolux Corporation 0x14F2 Unknown")
            (def position-right-of-34inc "3440,360")
            (def position-below-34inch "760,1440"))
  :config
  [{:profile
    [{:description "laptop right of 34inch monitor"}
     {:output "Samsung Electric Company C34H89x H4ZN501754" :position "0,0"}
     {:output laptop-output :position position-right-of-34inch}]}
   {:profile
    [{:description "Laptop only"}
     {:output "Chimei Innolux Corporation 0x14F2 Unknown" :position "0,0"}]}
   {:profile
    [{:description "laptop below 34inch monitor"}
     {:output "Samsung Electric Company C34H89x H4ZN501754" :position "0,0"}
     {:output laptop-output :position position-below-34inch}]}]})

;; Don't load stuff you don't trust! The config file can execute arbitrary code.
;; But that's for a reason: to support stuff like this:

(quote
 {:init (do (def legacy-config (slurp (fs/expand-home "~/kanshi-legacy-config.txt"))))
  :config
  [{:raw-str legacy-config}
   {:profile
    [{:description "laptop below 34inch monitor"}
     {:output "Samsung Electric Company C34H89x H4ZN501754" :position "0,0"}
     {:output laptop-output :position position-below-34inch}]}]})

;; In other words -- you can easily migrate from writing your kanshi config by
;; hand to autokanshi with an autokanshi config like this:

(quote
 {:init (do (def legacy-config (slurp (fs/expand-home "~/.config/kanshi/config"))))
  :config
  [{:raw-string legacy-config}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; KANSHIBB IMPL

(defn conf->str-raw [conf]
  (let [sections (:config conf)
        comment->str (fn [{:keys [comment]}] (str "\t# " comment))
        output->str (fn [{:keys [output position disable]}]
                      (assert output)
                      (assert (or position disable))
                      (if position
                        ;; enable
                        (str "\t" (str/join " " ["output" (pr-str output)
                                                 "position" position]))
                        ;; disable
                        (str "\t" (str/join " " ["output" (pr-str output) "disable"]))))
        embed-file->str (fn [{:keys [embed-file]}]
                          (slurp embed-file))
        ->str (fn [x] (cond (string? x) x
                            (:comment x) (comment->str x)
                            (:output x) (output->str x)
                            (:embed-file x) (embed-file->str x)
                            :else (comment->str (str "invalid: " (pr-str x)))))]
    (str (str/join "\n" ["profile {"
                         (comment->str {:comment "Generated by kanshi.clj"})
                         (str/join "\n" (map ->str sections))
                         "}"]))))


(defn profile->str [profile]
  (let [comment->str (fn [{:keys [comment]}] (str "\t# " comment))
        output->str (fn [{:keys [output position disable]}]
                      (assert output)
                      (assert (or position disable))
                      (if position
                        ;; enable
                        (str "\t" (str/join " " ["output" (pr-str output)
                                                 "position" position]))
                        ;; disable
                        (str "\t" (str/join " " ["output" (pr-str output) "disable"]))))
        embed-file->str (fn [{:keys [embed-file]}]
                          (slurp embed-file))
        ->str (fn [x] (cond (string? x) x
                            (:comment x) (comment->str x)
                            (:output x) (output->str x)
                            (:embed-file x) (embed-file->str x)
                            :else (comment->str (str "invalid: " (pr-str x)))))]
    (cond (:profile profile)
          (str (str/join "\n" ["profile {"
                               (comment->str {:comment "Generated by kanshi.clj"})
                               (str/join "\n" (map ->str (:profile profile)))
                               "}"]))

          (string? profile)
          profile

          :else
          (comment->str (str "invalid: " (pr-str profile))))))


(defn conf->str [conf]
  (str/join "\n\n" (map profile->str (:config conf))))

(defn kanshibb-str [_opts]
  (let [conf (edn/read-string (slurp (str (fs/expand-home "~/.config/kanshibb/config.edn"))))]
    (println (conf->str (eval conf)))))

(defn kanshibb-edn [_opts]
  (clojure.pprint/pprint (edn/read-string (slurp (str (fs/expand-home "~/.config/kanshibb/config.edn"))))))

#_
(defn kanshibb-reload-kanshi [_opts]
  ;; doesn't work.
  (shell "bash -c \"pkill kanshi; kanshi &; disown\""))
;; instead, do:
;;
;;     $ pkill kanshi; kanshi &; disown
;;
;; from a shell.

(defn -main [& args]
  (cond (= (first args) "edn")           (kanshibb-edn {})
        (= (first args) "str")           (kanshibb-str {})
        ;; doesn't work.
        ;; (= (first args) "reload-kanshi") (kanshibb-reload-kanshi {})
        :else                            (prn 'kanshibb-FTW)))