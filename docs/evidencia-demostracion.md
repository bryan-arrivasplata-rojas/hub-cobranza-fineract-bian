# Evidencia de Ejecución y Flujo Real (Fineract 1.12.1 + Collection Hub)

## 1. Datos Parametrizados en Apache Fineract
* **Cliente:** Ana Demo Integración (`CLIENT-DEMO-001`, activado el `2026-09-01`).
* **Producto y Préstamo:** `DEMO-CUMULATIVE` / `LOAN-DEMO-001` por `PEN 12,000.00` a 12 cuotas mensuales (`CUMULATIVE`, tasa `1.00%` mensual, estrategia `mifos-standard-strategy`), desembolsado el `2026-09-15` y con fecha de negocio avanzada al `2026-10-15`[cite: 1].
* **Ventana de Sincronización:** Fecha de corte `2026-10-15` y fin de horizonte `2026-11-15` (31 días)[cite: 1].

---

## 2. Secuencia Ejecutada y Resultados Verificados

### Paso 1 — Primera Sincronización (Estado Inicial)
* **Request:** `POST /api/v1/billing-batches/synchronizations` (`cutOffDate: 2026-10-15`, `horizonEndDate: 2026-11-15`, `loanReferences: ["LOAN-DEMO-001"]`).
* **Resultado en PostgreSQL (`collection_orders`):**
  * `ORD-LOAN-DEMO-001-01` | Vencimiento: `2026-10-15` | Capital: `1000.00` | Interés: `120.00` | Total: `1120.00` | Estado: **`PENDING`**
  * `ORD-LOAN-DEMO-001-02` | Vencimiento: `2026-11-15` | Capital: `1000.00` | Interés: `110.00` | Total: `1110.00` | Estado: **`PENDING`**

### Paso 2 — Abono Parcial de `PEN 500.00` en Fineract + Segunda Sincronización
* **Pago en Fineract:** `POST /fineract-provider/api/v1/loans/1/transactions?command=repayment` (`transactionDate: "15 October 2026"`, `transactionAmount: 500.00`).
* **Aplicación de pago (`mifos-standard-strategy`):** Cubre `PEN 120.00` de interés y amortiza `PEN 380.00` de capital de la cuota #1.
* **Resultado tras 2.ª Sincronización:**
  * `ORD-LOAN-DEMO-001-01` | Capital Pend.: `620.00` | Interés Pend.: `0.00` | Total: `620.00` | Estado: **`PARTIALLY_PAID`**
  * `ORD-LOAN-DEMO-001-02` | Capital Pend.: `1000.00` | Interés Pend.: `110.00` | Total: `1110.00` | Estado: **`PENDING`**

### Paso 3 — Liquidación de Saldo Cuota 1 (`PEN 620.00`) en Fineract + Tercera Sincronización
* **Pago en Fineract:** `POST /fineract-provider/api/v1/loans/1/transactions?command=repayment` (`transactionDate: "15 October 2026"`, `transactionAmount: 620.00`).
* **Resultado tras 3.ª Sincronización:**
  * `ORD-LOAN-DEMO-001-01` | Capital Pend.: `0.00` | Interés Pend.: `0.00` | Total: `0.00` | Estado: **`PAID`**
  * `ORD-LOAN-DEMO-001-02` | Capital Pend.: `1000.00` | Interés Pend.: `110.00` | Total: `1110.00` | Estado: **`PENDING`**

---

## 3. Pruebas Automatizadas del Módulo Java
Ejecutadas mediante Maven (`mvn test`) validando el filtrado por fin de horizonte, el cálculo de saldos y estados (`PENDING`, `PARTIALLY_PAID`, `PAID`) y la actualización idempotente de órdenes existentes sin duplicar registros.
