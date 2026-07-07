# kotoba-lang/pcb

Zero-dep portable `.cljc` — restored from the legacy `kami-engine/kami-eda`
Rust crate (deleted in kotoba-lang/kami-engine PR #82 "Remove Rust workspace
from kami-engine") as part of the **clj-wgsl migration** (ADR-2607010930,
`com-junkawasaki/root`).

KAMI EDA: schematic capture, PCB layout, netlist/BOM generation, ERC/DRC
validation.

**Named `pcb`, not `eda`** — `kotoba-lang/eda` already exists as an
unrelated, mature repo (OpenSTA-class timing/PVT slack analysis +
OpenROAD-class grid routing/overflow checks, its own docs site + evidence
output design). This repo restores the legacy `kami-eda` schematic/PCB
tool under a collision-free name.

| Namespace | Restored from | Purpose |
|---|---|---|
| `pcb.schematic` | `schematic` | Symbol placement, wire routing, net labelling, netlist extraction |
| `pcb.layout` | `pcb` | Footprint placement, trace routing, vias, copper zone pours, DRC (named `pcb.layout` not `pcb.pcb` — parent namespace is already `pcb`) |
| `pcb.netlist` | `netlist` | SPICE/Verilog-gate-level/EDIF export (all 3 fully implemented) + BOM generation |
| `pcb.erc` | `erc` | Electrical Rule Check — 5 rules (unconnected pin, output conflict, undriven power, single-pin net, duplicate designator) |

Violations are plain maps matching `kotoba-lang/engineer`'s
`engineer.drc` violation shape (`{:rule-id :severity :message :entity-ids
:location}`). Depends on `kotoba-lang/engineer` for shared contracts.

## Status

Restored — all 4 modules ported from the original 1031-line Rust
`lib.rs`, with all 7 original Rust unit tests mirrored 1:1 in
`test/pcb_test.cljc` (+1 smoke test, +4 netlist-export tests) — 12
tests / 28 assertions, 0 failures. Pure data + pure functions
throughout; no IO/GPU. `pcb.layout/run-drc`'s `fmt3` helper is
reader-conditional (`String/format` on JVM, `.toFixed` on CLJS) for
full portability.

`pcb.netlist/export-netlist` now fully implements all 3 formats (no
stubs): `:spice`, `:verilog-gate-level`, and `:edif` all resolve real
symbol/pin/net connectivity via the shared `find-net-name` helper —
Verilog emits a `module`/`endmodule` block with one instantiation per
symbol and a named port connection per pin; EDIF emits a `(cell top
(view netlist ...))` with `(instance ...)` per symbol and `(net ...)`
per connected net carrying real `(portRef ... (instanceRef ...))`
pairs. Port/portRef labels use the pin's own `:name` when present
(e.g. `.B`/`.E` for a transistor), falling back to `p<number>` for
pins with no name. Known limitations: every pin is a scalar 1-bit
connection (no bus/vector nets or multi-bit ports), and everything
flattens into a single top-level module/cell (no hierarchical
sub-modules/sub-cells).

## Develop

```bash
clojure -M:test
```
