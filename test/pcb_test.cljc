(ns pcb-test
  "Restoration-fidelity tests — one per original kami-eda Rust test
  (kami-engine/kami-eda/src/lib.rs `mod tests`, deleted PR #82)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [pcb]
            [pcb.schematic :as schematic]
            [pcb.layout :as layout]
            [pcb.netlist :as netlist]
            [pcb.erc :as erc]))

(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? (the-ns 'pcb)))))

;; mirrors `schematic_place_and_delete`
(deftest schematic-place-and-delete
  (let [[id sch] (schematic/place-symbol (schematic/schematic) "R_0402" "R1" "10k" [0.0 0.0] 0.0 false [])]
    (is (= 1 (count (:symbols sch))))
    (let [[deleted? sch] (schematic/delete-entity sch id)]
      (is deleted?)
      (is (empty? (:symbols sch))))))

;; mirrors `schematic_generate_netlist`
(deftest schematic-generate-netlist
  (let [pin-a (schematic/pin {:name "1" :number "1" :pin-type :passive :position [1.0 0.0] :orientation :right})
        pin-b (schematic/pin {:name "1" :number "1" :pin-type :passive :position [-1.0 0.0] :orientation :left})
        [_ sch] (schematic/place-symbol (schematic/schematic) "R_0402" "R1" "10k" [0.0 0.0] 0.0 false [pin-a])
        [_ sch] (schematic/place-symbol sch "R_0402" "R2" "4.7k" [4.0 0.0] 0.0 false [pin-b])
        [_ sch] (schematic/route-wire sch [1.0 0.0] [3.0 0.0] 1)
        netlist (schematic/generate-netlist sch)]
    (is (= 1 (count netlist)))
    (let [net (get netlist 1)]
      (is (= 2 (count (:pins net)))))))

;; mirrors `pcb_drc_trace_width`
(deftest pcb-drc-trace-width
  (let [board (layout/pcb-board [[0.0 0.0] [100.0 0.0] [100.0 100.0] [0.0 100.0]]
                                 [(layout/layer-def :front "F.Cu" 0.035)])
        [_ lay] (layout/route-trace (layout/pcb-layout board) 1 [[0.0 0.0] [10.0 0.0]] 0.05 :front)
        violations (layout/run-drc lay)]
    (is (some #(= (:rule-id %) "PCB_MIN_TRACE_WIDTH") violations))))

;; mirrors `pcb_drc_via_drill`
(deftest pcb-drc-via-drill
  (let [board (layout/pcb-board [[0.0 0.0] [50.0 50.0]] [])
        [_ lay] (layout/add-via (layout/pcb-layout board) 10.0 10.0 0.1 0.3 1)
        violations (layout/run-drc lay)]
    (is (some #(= (:rule-id %) "PCB_MIN_VIA_DRILL") violations))
    (is (some #(= (:rule-id %) "PCB_MIN_ANNULAR_RING") violations))))

;; mirrors `erc_duplicate_designator`
(deftest erc-duplicate-designator
  (let [[_ sch] (schematic/place-symbol (schematic/schematic) "R_0402" "R1" "10k" [0.0 0.0] 0.0 false [])
        [_ sch] (schematic/place-symbol sch "C_0402" "R1" "100n" [5.0 0.0] 0.0 false [])
        violations (erc/run-erc sch)]
    (is (some #(= (:rule-id %) "ERC_DUPLICATE_DESIGNATOR") violations))))

;; mirrors `netlist_bom_generation`
(deftest netlist-bom-generation
  (let [symbols [{:id 1 :library-ref "R_0402" :designator "R1" :value "10k"
                  :position [0.0 0.0] :rotation 0.0 :mirror false :pins []}
                 {:id 2 :library-ref "R_0402" :designator "R2" :value "10k"
                  :position [5.0 0.0] :rotation 0.0 :mirror false :pins []}]
        bom (netlist/generate-bom symbols)]
    (is (= 1 (count bom)))
    (is (= 2 (:quantity (first bom))))
    (is (= "10k" (:value (first bom))))))

;; mirrors `netlist_spice_export`
(deftest netlist-spice-export
  (let [p (schematic/pin {:name "1" :number "1" :pin-type :passive :position [0.0 0.0] :orientation :right})
        sym {:id 1 :library-ref "R_0402" :designator "R1" :value "10k"
             :position [0.0 0.0] :rotation 0.0 :mirror false :pins [p]}
        nets {1 {:id 1 :name "VCC" :pins [[1 "1"]]}}
        spice (netlist/export-netlist :spice [sym] nets)]
    (is (str/includes? spice "R1"))
    (is (str/includes? spice "VCC"))
    (is (str/includes? spice ".end"))))
