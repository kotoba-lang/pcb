(ns pcb.netlist
  "Netlist export (SPICE, Verilog gate-level, EDIF) and BOM generation.
  Restored from kami-eda's `netlist` module (deleted PR #82)."
  (:require [clojure.string :as str]))

(def netlist-formats #{:spice :verilog-gate-level :edif})

(defn bom-entry [designator value footprint quantity]
  {:designator designator :value value :footprint footprint :quantity quantity})

(defn generate-bom
  "Bill of materials from placed symbols. Symbols sharing the same
  `(value, library-ref)` are grouped and counted, sorted by combined
  designator string."
  [symbols]
  (let [groups (reduce
                (fn [groups sym]
                  (let [key [(:value sym) (:library-ref sym)]]
                    (-> groups
                        (update-in [key :quantity] (fnil inc 0))
                        (update-in [key :footprint] #(or % (:library-ref sym)))
                        (update-in [key :designators] (fnil conj []) (:designator sym)))))
                {} symbols)]
    (vec (sort-by :designator
                  (map (fn [[[value _] {:keys [footprint quantity designators]}]]
                         (bom-entry (str/join ", " designators) value footprint quantity))
                       groups)))))

(defn- find-net-name [nets sym-id pin-number]
  (or (some (fn [[_ net]] (when (some #(= % [sym-id pin-number]) (:pins net)) (:name net)))
            nets)
      "?"))

(defn spice-netlist
  "Render a SPICE netlist string for `symbols` connected via `nets`."
  [symbols nets]
  (str "* KAMI EDA — SPICE netlist\n"
       (apply str
              (for [sym symbols]
                (str (:designator sym) " "
                     (apply str (for [p (:pins sym)] (str (find-net-name nets (:id sym) (:number p)) " ")))
                     (:value sym) "\n")))
       ".end\n"))

(defn export-netlist
  "Export a netlist in `format` (`:spice`/`:verilog-gate-level`/`:edif`).
  Only SPICE is fully implemented; other formats return a stub."
  [format symbols nets]
  (case format
    :spice (spice-netlist symbols nets)

    :verilog-gate-level
    (str "// KAMI EDA — Verilog gate-level netlist\n"
         "module top;\n"
         (apply str (for [[_ net] nets] (str "  wire " (:name net) ";\n")))
         "endmodule\n")

    :edif
    (str "(edif kami_eda\n"
         "  (edifVersion 2 0 0)\n"
         "  (edifLevel 0)\n"
         "  (keywordMap (keywordLevel 0))\n"
         (apply str (for [sym symbols] (str "  (instance " (:designator sym) " (viewRef " (:library-ref sym) "))\n")))
         ")\n")))
