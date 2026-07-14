# cloud-itonami-isco-9334

Open Occupation Blueprint for **ISCO-08 9334**: Shelf Fillers.

This repository designs a forkable OSS business for an independent retail merchandising and restocking practice: a shelf-restocking and inventory-scanning robot manages shelf compliance under a governor-gated actor, so the practice keeps its own restocking records instead of renting a closed merchandising SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a shelf-restocking and inventory-scanning robot performs shelf restocking, price-tag verification and out-of-stock flagging under an actor that proposes
actions and an independent **Retail Merchandising Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
price-tag change above the store's registered price-authorization ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
store planogram + inventory feed + restock schedule
        |
        v
Merchandising Advisor -> Retail Merchandising Governor -> restock/report, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `9334`). Required capabilities:

- :robotics
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
