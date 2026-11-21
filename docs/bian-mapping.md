# Propuesta de Arquitectura Funcional bajo Estándar BIAN V14.0

## 1. Referencias Oficiales
* **Estándar:** BIAN (Banking Industry Architecture Network) Service Landscape **V14.0** — [https://bian.org/deliverables/service-landscape/](https://bian.org/deliverables/service-landscape/)
* **Core Bancario:** Apache Fineract **1.12.1** — [https://fineract.apache.org/docs/1.12.1/](https://fineract.apache.org/docs/1.12.1/)

---

## 2. Service Domains BIAN Identificados y Responsabilidades

| Área BIAN | Service Domain | Rol en la Solución | Responsabilidad Concreta |
| :--- | :--- | :--- | :--- |
| **Operations & Execution** -> *Account Management* | **Customer Billing** *(Domino Central del Hub)* | **Hub de Cobranza (Java 21 + PostgreSQL)** | Orquesta lotes de facturación/cobranza (`BillingBatch`), calcula las obligaciones exigibles dentro de una ventana (`cutOffDate` a `horizonEndDate`) y mantiene el estado de las órdenes locales (`CollectionOrder`) desglosando capital e interés. |
| **Operations & Execution** -> *Loans and Deposits* | **Consumer Loan** | **Apache Fineract 1.12.1 (Core)** | Administra el ciclo de vida del préstamo (`LOAN-DEMO-001`), el cronograma `CUMULATIVE` de 12 cuotas, el cálculo de intereses (`1.00%` mensual) y la aplicación de abonos (`mifos-standard-strategy`). |
| **Reference Data** -> *Party* | **Party Reference Data Directory** | **Apache Fineract 1.12.1 (Clients API)** | Provee los datos maestros y la referencia externa del cliente (`CLIENT-DEMO-001` — *Ana Demo Integración*) consultados por el Hub durante la sincronización. |

---

## 3. Mapeo de Artefactos BIAN en el Modelo de Datos y API

1. **Control Record (`BillingBatch`):**
   * **Recurso REST:** `POST /api/v1/billing-batches/synchronizations` y `GET /api/v1/billing-batches/{batchId}`.
   * **Función:** Controla cada ejecución periódica de cobranza mediante fecha de corte (`2026-10-15`), fin de horizonte (`2026-11-15`), estado de ejecución (`COMPLETED` / `FAILED`) y conteo de órdenes procesadas.
2. **Behavior Qualifier (`CollectionOrder`):**
   * **Recurso REST:** `GET /api/v1/collection-orders` (filtros por préstamo, vencimiento y situación).
   * **Función:** Representa cada obligación exigible (`ORD-LOAN-DEMO-001-01`, `ORD-LOAN-DEMO-001-02`) con identificador único, referencia externa del cliente, cuota de origen, moneda (`PEN`), vencimiento, `pendingPrincipal`, `pendingInterest`, `totalDue` y ciclo de estados idempotente (`PENDING` -> `PARTIALLY_PAID` -> `PAID`).

---

## 4. Justificación de Servicios AWS y Relación con BIAN

* **Amazon ECS (AWS Fargate):** Ejecuta de forma desacoplada y sin servidores los contenedores de **Customer Billing (Hub Java 21)**, **Consumer Loan (Apache Fineract 1.12.1)** y la interfaz operativa en subredes privadas, respetando el aislamiento entre Service Domains de BIAN.
* **Amazon RDS Multi-AZ (PostgreSQL 16 y MariaDB 11.4):** Garantiza la independencia de datos por dominio (*Database-per-Service*): PostgreSQL almacena exclusivamente los `Control Records` y `Behavior Qualifiers` de *Customer Billing*, mientras MariaDB soporta el core transaccional de *Consumer Loan* sin acceso cruzado a tablas.
* **Application Load Balancer (ALB) + AWS WAF + Secrets Manager:** Expone los endpoints REST de forma segura con TLS 1.3, controla el tráfico perimetral y gestiona las credenciales de integración entre dominios sin exponer secretos en código.
