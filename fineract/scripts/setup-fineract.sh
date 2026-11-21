#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://localhost:8443/fineract-provider/api/v1"
AUTH="mifos:password"
TENANT="default"

until curl -skf "https://localhost:8443/fineract-provider/actuator/health" | grep -q '"status":"UP"'; do
  printf "."
  sleep 5
done
echo ""

api_call() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  if [ -n "$data" ]; then
    curl -skS --fail-with-body -X "$method" "${BASE_URL}${path}" \
      -u "$AUTH" \
      -H "Fineract-Platform-TenantId: $TENANT" \
      -H "Content-Type: application/json; charset=UTF-8" \
      -d "$data"
  else
    curl -skS --fail-with-body -X "$method" "${BASE_URL}${path}" \
      -u "$AUTH" \
      -H "Fineract-Platform-TenantId: $TENANT"
  fi
}

api_call PUT "/currencies" '{"currencies":["USD","PEN"]}'
echo ""

GLOBAL_CONFIGS=$(api_call GET "/configurations")
BIZ_DATE_ID=$(echo "$GLOBAL_CONFIGS" | tr '{' '\n' | grep '"name":"enable-business-date"' | sed -E 's/.*"id":([0-9]+).*/\1/')
api_call PUT "/configurations/${BIZ_DATE_ID}" '{"enabled":true}'
echo ""

api_call POST "/businessdate" '{
  "type": "BUSINESS_DATE",
  "date": "15 September 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}'
echo ""

CLIENT_RESP=$(api_call POST "/clients" '{
  "officeId": 1,
  "legalFormId": 1,
  "firstname": "Ana",
  "lastname": "Demo Integraci\u00f3n",
  "externalId": "CLIENT-DEMO-001",
  "active": true,
  "activationDate": "01 September 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}')
CLIENT_ID=$(echo "$CLIENT_RESP" | sed -E 's/.*"clientId":([0-9]+).*/\1/')

PRODUCT_RESP=$(api_call POST "/loanproducts" '{
  "name": "Producto Demo Cumulative",
  "shortName": "DCUM",
  "description": "Préstamo demo 12 cuotas capital constante 1% mensual",
  "currencyCode": "PEN",
  "digitsAfterDecimal": 2,
  "inMultiplesOf": 0,
  "principal": 12000.00,
  "minPrincipal": 1000.00,
  "maxPrincipal": 50000.00,
  "numberOfRepayments": 12,
  "minNumberOfRepayments": 1,
  "maxNumberOfRepayments": 36,
  "repaymentEvery": 1,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 1.00,
  "minInterestRatePerPeriod": 0.10,
  "maxInterestRatePerPeriod": 10.00,
  "interestRateFrequencyType": 2,
  "amortizationType": 0,
  "interestType": 0,
  "interestCalculationPeriodType": 1,
  "transactionProcessingStrategyCode": "mifos-standard-strategy",
  "loanScheduleType": "CUMULATIVE",
  "daysInMonthType": 30,
  "daysInYearType": 360,
  "isInterestRecalculationEnabled": false,
  "accountingRule": 1,
  "locale": "en",
  "dateFormat": "dd MMMM yyyy"
}')
PRODUCT_ID=$(echo "$PRODUCT_RESP" | sed -E 's/.*"resourceId":([0-9]+).*/\1/')

LOAN_RESP=$(api_call POST "/loans" "{
  \"clientId\": ${CLIENT_ID},
  \"productId\": ${PRODUCT_ID},
  \"externalId\": \"LOAN-DEMO-001\",
  \"principal\": 12000.00,
  \"loanTermFrequency\": 12,
  \"loanTermFrequencyType\": 2,
  \"numberOfRepayments\": 12,
  \"repaymentEvery\": 1,
  \"repaymentFrequencyType\": 2,
  \"interestRatePerPeriod\": 1.00,
  \"amortizationType\": 0,
  \"interestType\": 0,
  \"interestCalculationPeriodType\": 1,
  \"transactionProcessingStrategyCode\": \"mifos-standard-strategy\",
  \"expectedDisbursementDate\": \"15 September 2026\",
  \"submittedOnDate\": \"15 September 2026\",
  \"interestChargedFromDate\": \"15 September 2026\",
  \"repaymentsStartingFromDate\": \"15 October 2026\",
  \"loanType\": \"individual\",
  \"locale\": \"en\",
  \"dateFormat\": \"dd MMMM yyyy\"
}")
LOAN_ID=$(echo "$LOAN_RESP" | sed -E 's/.*"loanId":([0-9]+).*/\1/')

api_call POST "/loans/${LOAN_ID}?command=approve" '{
  "approvedOnDate": "15 September 2026",
  "approvedLoanAmount": 12000.00,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}'
echo ""

api_call POST "/loans/${LOAN_ID}?command=disburse" '{
  "actualDisbursementDate": "15 September 2026",
  "transactionAmount": 12000.00,
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}'
echo ""

api_call POST "/businessdate" '{
  "type": "BUSINESS_DATE",
  "date": "15 October 2026",
  "dateFormat": "dd MMMM yyyy",
  "locale": "en"
}'
echo ""
echo "OK -> Client: $CLIENT_ID | Product: $PRODUCT_ID \vert{} Loan:$LOAN_ID"
