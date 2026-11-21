# Hub de Cobranza — Integración Apache Fineract 1.12.1 y Java 21 (BIAN)

Este proyecto implementa un módulo externo de cobranza desarrollado en **Java 21** y **Spring Boot (WebFlux)** que se conecta mediante HTTP/REST a una instancia real de **Apache Fineract 1.12.1**. Su objetivo es consultar los cronogramas de préstamos activos, filtrar las cuotas exigibles dentro de una ventana de tiempo y mantener órdenes locales de cobranza en su propia base de datos **PostgreSQL**, separando capital e intereses sin modificar ni consultar directamente la base de datos del core bancario.

---

## 1. Decisiones de Diseño y Enfoque BIAN

Para estructurar las responsabilidades del microservicio tomé como referencia el Service Landscape de **BIAN V14.0**, específicamente el dominio **Customer Billing** (dentro de *Operations & Execution -> Account Management*), ya que su función es consolidar las obligaciones periódicas de pago de un cliente y hacer seguimiento de su estado sin alterar la contabilidad del core:

* **Control Record (`BillingBatch`):** Representa cada ejecución o lote de sincronización, guardando la fecha de corte (`cutOffDate`), el fin de horizonte (`horizonEndDate`), la fecha de ejecución y cuántas órdenes se procesaron.
* **Behavior Qualifier (`CollectionOrder`):** Representa cada orden de cobranza generada a partir de una cuota del préstamo. Se actualiza de forma **idempotente** (usando la combinación préstamo + número de cuota): si vuelve a ejecutarse una sincronización tras un pago parcial o total en Fineract, se actualiza el saldo y el estado (`PENDING` -> `PARTIALLY_PAID` -> `PAID`) sobre el mismo registro sin duplicarlo.
* **Separación de responsabilidades (MVC pragmático):**
  * `config`: Configuración de Spring Data JPA, WebClient hacia Fineract (con manejo de SSL local), CORS y Swagger/OpenAPI.
  * `model`: Entidades JPA (`BillingBatch`, `CollectionOrder`) y el enum de estados (`OrderStatus`).
  * `service`: Lógica de negocio (`CustomerBillingService`) que filtra cuotas con saldo pendiente hasta el fin de horizonte y calcula los estados según lo que devuelve Fineract.
  * `adapter`: Controlador REST (`CustomerBillingController`), cliente HTTP reactivo (`FineractClient`) y repositorios JPA (`BatchRepository`, `OrderRepository`).

La arquitectura propuesta para desplegar esta solución en **AWS** (usando ECS Fargate, Amazon RDS Multi-AZ para PostgreSQL y MariaDB, Application Load Balancer y redes privadas aisladas) se encuentra documentada en `docs/aws-architecture.drawio` y `docs/aws-architecture.drawio.png`, junto con el detalle del mapeo en `docs/bian-mapping.md` y la evidencia de ejecución en `docs/evidencia-demostracion.md`.

---

## 2. Estructura del Repositorio

~~~text
proyecto-hub-cobranza/
├── fineract/               # Script SQL de tenants y script REST para preparar cliente, producto y préstamo
├── collection-hub/         # Aplicación Java 21 + Spring Boot WebFlux + Flyway + pruebas unitarias
├── collection-hub-ui/      # Panel web ligero (Nginx + Tailwind) para probar sincronizaciones y abonos visualmente
├── docs/                   # Diagrama AWS (.drawio y .png), propuesta BIAN, OpenAPI, Postman y evidencia
├── docker-compose.yml      # Orquestación de los 5 contenedores y redes aisladas
└── reset-demo.sh           # Script rápido para reversar pagos en Fineract y limpiar órdenes del Hub
~~~

---

## 3. Requisitos Previos y Puertos

* **Docker** y **Docker Compose** instalados.
* **curl** y **bash** (en Windows se puede usar Git Bash).

Una vez levantado el entorno, los servicios quedan disponibles en:

* **Panel Visual del Hub (UI de apoyo):** http://localhost:3000
* **API REST del Hub (Java 21):** http://localhost:8080
* **Documentación Swagger UI:** http://localhost:8080/swagger-ui.html
* **Apache Fineract 1.12.1 (API Core):** https://localhost:8443/fineract-provider/api/v1 (Tenant: `default`, Usuario: `mifos`, Contraseña: `password`)
* **PostgreSQL 16 (BD del Hub):** `localhost:5432` (BD: `collection_hub`, Usuario: `hub_user`, Contraseña: `hub_password`)
* **MariaDB 11.4 (BD de Fineract):** `localhost:3306` (BDs: `fineract_tenants`, `fineract_default`)

---

## 4. Cómo Levantar y Preparar el Entorno con Docker

1. **Construir e iniciar todos los contenedores:**
~~~bash
docker compose up -d --build
~~~
*(Nota: En el primer arranque, Apache Fineract tarda alrededor de 1 minuto en completar sus migraciones iniciales).*

2. **Cargar la configuración funcional y los datos del caso de negocio en Fineract:**
~~~bash
./fineract/scripts/setup-fineract.sh
~~~
Este script habilita la moneda `PEN`, crea el cliente `Ana Demo Integración` (`CLIENT-DEMO-001`), configura el producto `DEMO-CUMULATIVE` (12 cuotas mensuales, 1.00% mensual, cronograma `CUMULATIVE` y estrategia estándar `mifos-standard-strategy`), desembolsa el préstamo `LOAN-DEMO-001` por `S/ 12,000.00` al `15/09/2026` y avanza la fecha de negocio de Fineract al `15/10/2026`.

3. **Verificar que la API del Hub responde:**
~~~bash
curl -s http://localhost:8080/actuator/health
~~~

---

## 5. Demostración del Flujo Completo

La demostración puede hacerse desde el **panel visual en http://localhost:3000** (guía paso a paso conectada al estado real), importando la colección **`docs/postman-collection.json`** en Postman o ejecutando los siguientes comandos `curl`:

1. **Primera sincronización (corte `2026-10-15`, horizonte `2026-11-15`):**
~~~bash
curl -s -X POST "http://localhost:8080/api/v1/billing-batches/synchronizations" \
  -H "Content-Type: application/json" \
  -d '{"cutOffDate":"2026-10-15","horizonEndDate":"2026-11-15","loanReferences":["LOAN-DEMO-001"]}'
~~~
Genera el lote y crea dos órdenes en estado `PENDING`: la cuota #1 por `S/ 1,120.00` (`S/ 1,000.00` capital + `S/ 120.00` interés) y la cuota #2 por `S/ 1,110.00` (`S/ 1,000.00` capital + `S/ 110.00` interés).

2. **Registrar primer pago parcial de `S/ 500.00` en Fineract y volver a sincronizar:**
~~~bash
curl -sk -X POST "https://localhost:8443/fineract-provider/api/v1/loans/1/transactions?command=repayment" \
  -u "mifos:password" -H "Fineract-Platform-TenantId: default" -H "Content-Type: application/json" \
  -d '{"transactionDate":"15 October 2026","transactionAmount":500.00,"paymentTypeId":1,"locale":"en","dateFormat":"dd MMMM yyyy"}'

curl -s -X POST "http://localhost:8080/api/v1/billing-batches/synchronizations" \
  -H "Content-Type: application/json" \
  -d '{"cutOffDate":"2026-10-15","horizonEndDate":"2026-11-15","loanReferences":["LOAN-DEMO-001"]}'
~~~
Fineract cubre primero los `S/ 120.00` de interés y amortiza `S/ 380.00` de capital. Al sincronizar, la orden #1 pasa a `PARTIALLY_PAID` con `S/ 620.00` pendientes.

3. **Pagar el saldo restante de la primera cuota (`S/ 620.00`) y sincronizar nuevamente:**
~~~bash
curl -sk -X POST "https://localhost:8443/fineract-provider/api/v1/loans/1/transactions?command=repayment" \
  -u "mifos:password" -H "Fineract-Platform-TenantId: default" -H "Content-Type: application/json" \
  -d '{"transactionDate":"15 October 2026","transactionAmount":620.00,"paymentTypeId":1,"locale":"en","dateFormat":"dd MMMM yyyy"}'

curl -s -X POST "http://localhost:8080/api/v1/billing-batches/synchronizations" \
  -H "Content-Type: application/json" \
  -d '{"cutOffDate":"2026-10-15","horizonEndDate":"2026-11-15","loanReferences":["LOAN-DEMO-001"]}'
~~~
La orden #1 queda en estado `PAID` con saldo `S/ 0.00` y la orden #2 se mantiene vigente en `PENDING` por `S/ 1,110.00`.

4. **Consultar órdenes locales filtradas:**
~~~bash
curl -s "http://localhost:8080/api/v1/collection-orders?loanReference=LOAN-DEMO-001&dueDateTo=2026-11-15"
~~~

---

## 6. Pruebas Automatizadas, Reinicio y Apagado

* **Ejecutar pruebas unitarias del módulo Java (con contenedor Maven, sin necesidad de instalar Maven en local):**
~~~bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd)/collection-hub:/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn test
~~~

* **Reiniciar los datos del ejercicio en segundos (reversa pagos en Fineract y limpia las tablas del Hub):**
~~~bash
./reset-demo.sh
~~~

* **Detener los contenedores conservando los datos:**
~~~bash
docker compose stop
~~~

* **Eliminar todo el entorno y sus volúmenes de base de datos:**
~~~bash
docker compose down -v --remove-orphans
~~~
