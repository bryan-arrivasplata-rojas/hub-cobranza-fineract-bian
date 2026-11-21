package com.bank.collectionhub.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class FineractClient {

    private final WebClient fineractWebClient;

    public FineractClient(WebClient fineractWebClient) {
        this.fineractWebClient = fineractWebClient;
    }

    public Mono<FineractLoanData> getLoanSchedule(String loanReference) {
        return fineractWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/loans").queryParam("externalId", loanReference).build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(searchResponse -> {
                    if (searchResponse == null || !searchResponse.has("pageItems") || searchResponse.get("pageItems").isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, "Préstamo no encontrado en Fineract: " + loanReference));
                    }
                    JsonNode summary = searchResponse.get("pageItems").get(0);
                    long loanId = summary.get("id").asLong();
                    long clientId = summary.get("clientId").asLong();
                    String clientName = summary.path("clientName").asText("Cliente Fineract");

                    Mono<String> clientExternalId = fineractWebClient.get()
                            .uri("/clients/{clientId}", clientId)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(c -> c.hasNonNull("externalId") ? c.get("externalId").asText() : String.valueOf(clientId));

                    Mono<JsonNode> loanDetail = fineractWebClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/loans/{loanId}")
                                    .queryParam("associations", "repaymentSchedule")
                                    .build(loanId))
                            .retrieve()
                            .bodyToMono(JsonNode.class);

                    return Mono.zip(clientExternalId, loanDetail)
                            .map(tuple -> mapSchedule(loanReference, tuple.getT1(), clientName, tuple.getT2()));
                })
                .onErrorMap(e -> !(e instanceof ResponseStatusException),
                        e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Error comunicando con Fineract: " + e.getMessage(), e));
    }

    private FineractLoanData mapSchedule(String loanReference, String customerExternalId, String customerName, JsonNode loanDetail) {
        String currency = loanDetail.path("currency").path("code").asText("PEN");
        JsonNode periods = loanDetail.path("repaymentSchedule").path("periods");
        List<InstallmentData> installments = new ArrayList<>();

        for (JsonNode period : periods) {
            if (!period.hasNonNull("period") || period.get("period").asInt() == 0) {
                continue;
            }
            installments.add(new InstallmentData(
                    period.get("period").asInt(),
                    parseDate(period.get("dueDate")),
                    decimalValue(period, "totalOriginalDueForPeriod"),
                    decimalValue(period, "principalOutstanding"),
                    decimalValue(period, "interestOutstanding"),
                    decimalValue(period, "totalOutstandingForPeriod")
            ));
        }

        return new FineractLoanData(loanReference, customerExternalId, customerName, currency, installments);
    }

    private LocalDate parseDate(JsonNode dateNode) {
        if (dateNode.isArray() && dateNode.size() == 3) {
            return LocalDate.of(dateNode.get(0).asInt(), dateNode.get(1).asInt(), dateNode.get(2).asInt());
        }
        return LocalDate.parse(dateNode.asText());
    }

    private BigDecimal decimalValue(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : BigDecimal.ZERO;
    }

    public record FineractLoanData(
            String loanReference,
            String customerExternalId,
            String customerName,
            String currency,
            List<InstallmentData> installments
    ) {}

    public record InstallmentData(
            int number,
            LocalDate dueDate,
            BigDecimal originalDue,
            BigDecimal pendingPrincipal,
            BigDecimal pendingInterest,
            BigDecimal totalPending
    ) {}
}
