(ns merchandising.store
  "SSoT for the ISCO-08 9334 independent retail merchandising &
  restocking practice actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a
  shelf-restocking and inventory-scanning robot performs shelf
  restocking, price-tag verification and out-of-stock flagging under
  this advisor/governor pair, which never dispatches hardware itself
  and never changes a price tag above the store's registered price-
  authorization ceiling). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain (the registered retail location is named `shop` throughout,
  not `store`, to avoid colliding with this namespace's own name):

    client — a registered independent retailer/small grocery chain
             (:client-id, :name)
    shop   — a registered retail store {:shop-id :client-id :name
             :max-price-authorization number
             :planogram-compliance-checked? boolean}.
             `:max-price-authorization` is the registered ceiling a
             proposed price-change amount must not exceed — changing
             a price tag beyond the shop's registered authorization
             ceiling is an unauthorized price change, not routine
             merchandising. `:planogram-compliance-checked?` records
             whether a planogram compliance check has been completed
             — logging a restock complete without a planogram
             compliance check is an unverified restock, not efficient
             service.
    record — a committed operating record (a restock operation) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (shop [s shop-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-shop! [s shop-rec])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (shop [_ shop-id] (get-in @a [:shops shop-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-shop! [s shop-rec]
    (swap! a assoc-in [:shops (:shop-id shop-rec)] shop-rec) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :shops {} :records [] :ledger []}
                                   seed)))))
