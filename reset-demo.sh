#!/usr/bin/env bash
set -e

echo "1. Limpiando lotes y órdenes en PostgreSQL (Collection Hub)..."
docker exec -i collection-hub-db psql -U hub_user -d collection_hub -c "TRUNCATE TABLE collection_orders, billing_batches CASCADE;"

echo "2. Consultando y reversando pagos de prueba en Apache Fineract..."
LOAN_JSON=$(curl -sk -u "mifos:password" -H "Fineract-Platform-TenantId: default" \
  "https://localhost:8443/fineract-provider/api/v1/loans/1?associations=transactions")

REPAYMENT_IDS=$(echo "$LOAN_JSON" | docker run --rm -i python:3.12-alpine python -c "
import sys, json
data = json.load(sys.stdin)
txs = [t['id'] for t in data.get('transactions', []) if t.get('type', {}).get('id') == 2 and not t.get('manuallyReversed', False)]
print(' '.join(map(str, sorted(txs, reverse=True))))
")

if [ -z "$REPAYMENT_IDS" ]; then
  echo "   - No se encontraron pagos activos para reversar."
else
  for TX_ID in $REPAYMENT_IDS; do
    curl -sk -X POST "https://localhost:8443/fineract-provider/api/v1/loans/1/transactions/${TX_ID}?command=undo" \
      -u "mifos:password" -H "Fineract-Platform-TenantId: default" -H "Content-Type: application/json" \
      -d '{"transactionDate":"15 October 2026","transactionAmount":0,"locale":"en","dateFormat":"dd MMMM yyyy"}' > /dev/null
    echo "   - Transacción de pago #${TX_ID} reversada."
  done
fi

echo "3. Verificando saldo de la primera cuota en Fineract..."
curl -sk -u "mifos:password" -H "Fineract-Platform-TenantId: default" \
  "https://localhost:8443/fineract-provider/api/v1/loans/1?associations=repaymentSchedule" | \
  grep -o '"totalOutstandingForPeriod": *[0-9.]*' | head -n 3
