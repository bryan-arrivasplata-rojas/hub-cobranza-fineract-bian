package com.bank.collectionhub.adapter;

import com.bank.collectionhub.model.CollectionOrder;
import com.bank.collectionhub.model.OrderStatus;
import com.bank.collectionhub.service.CustomerBillingService;
import com.bank.collectionhub.service.CustomerBillingService.BatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Customer Billing (BIAN V14.0)", description = "Gestión de lotes y órdenes de cobranza")
public class CustomerBillingController {

    private final CustomerBillingService billingService;

    public CustomerBillingController(CustomerBillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/billing-batches/synchronizations")
    @Operation(summary = "Initiate Billing Batch Synchronization")
    public Mono<ResponseEntity<BatchResponse>> synchronize(@Valid @RequestBody SyncRequest request) {
        return billingService.synchronizeBatch(request.cutOffDate(), request.horizonEndDate(), request.loanReferences())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping("/billing-batches/{batchId}")
    @Operation(summary = "Retrieve Billing Batch Details")
    public Mono<ResponseEntity<BatchResponse>> getBatch(@PathVariable UUID batchId) {
        return billingService.getBatchById(batchId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/collection-orders")
    @Operation(summary = "Retrieve Collection Orders")
    public Flux<CollectionOrder> getOrders(
            @RequestParam(required = false) String loanReference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) OrderStatus status) {
        return billingService.searchOrders(loanReference, dueDateTo, status);
    }

    public record SyncRequest(
            @NotNull LocalDate cutOffDate,
            @NotNull LocalDate horizonEndDate,
            @NotEmpty List<String> loanReferences
    ) {}
}
