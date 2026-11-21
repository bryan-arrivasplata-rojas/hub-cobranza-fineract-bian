package com.bank.collectionhub.service;

import com.bank.collectionhub.adapter.BatchRepository;
import com.bank.collectionhub.adapter.FineractClient;
import com.bank.collectionhub.adapter.FineractClient.FineractLoanData;
import com.bank.collectionhub.adapter.FineractClient.InstallmentData;
import com.bank.collectionhub.adapter.OrderRepository;
import com.bank.collectionhub.model.BillingBatch;
import com.bank.collectionhub.model.CollectionOrder;
import com.bank.collectionhub.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerBillingService {

    private final FineractClient fineractClient;
    private final BatchRepository batchRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    public CustomerBillingService(FineractClient fineractClient,
                                  BatchRepository batchRepository,
                                  OrderRepository orderRepository,
                                  TransactionTemplate transactionTemplate) {
        this.fineractClient = fineractClient;
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public Mono<BatchResponse> synchronizeBatch(LocalDate cutOffDate, LocalDate horizonEndDate, List<String> loanReferences) {
        return Flux.fromIterable(loanReferences)
                .flatMap(fineractClient::getLoanSchedule)
                .collectList()
                .flatMap(loans -> Mono.fromCallable(() ->
                        transactionTemplate.execute(status -> saveBatch(cutOffDate, horizonEndDate, loans))
                ).subscribeOn(Schedulers.boundedElastic()));
    }

    public BatchResponse saveBatch(LocalDate cutOffDate, LocalDate horizonEndDate, List<FineractLoanData> loans) {
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        String batchCode = "BATCH-" + DateTimeFormatter.BASIC_ISO_DATE.format(cutOffDate)
                + "-" + batchId.toString().substring(0, 6).toUpperCase();

        BillingBatch batch = new BillingBatch(batchId, batchCode, cutOffDate, horizonEndDate, now, 0, "IN_PROGRESS");
        batchRepository.save(batch);

        List<CollectionOrder> orders = new ArrayList<>();

        for (FineractLoanData loan : loans) {
            for (InstallmentData inst : loan.installments()) {
                Optional<CollectionOrder> existing =
                        orderRepository.findByLoanReferenceAndInstallmentNumber(loan.loanReference(), inst.number());

                if (!isEligible(inst, horizonEndDate, existing.isPresent())) {
                    continue;
                }

                CollectionOrder order = existing.orElseGet(CollectionOrder::new);
                if (order.getId() == null) {
                    order.setId(UUID.randomUUID());
                    order.setOrderCode(String.format("ORD-%s-%02d", loan.loanReference(), inst.number()));
                }

                order.setLastBatchId(batchId);
                order.setCustomerExternalId(loan.customerExternalId());
                order.setCustomerName(loan.customerName());
                order.setLoanReference(loan.loanReference());
                order.setInstallmentNumber(inst.number());
                order.setCurrency(loan.currency());
                order.setDueDate(inst.dueDate());
                order.setPendingPrincipal(inst.pendingPrincipal().setScale(2, RoundingMode.HALF_UP));
                order.setPendingInterest(inst.pendingInterest().setScale(2, RoundingMode.HALF_UP));
                order.setTotalDue(inst.totalPending().setScale(2, RoundingMode.HALF_UP));
                order.setStatus(resolveStatus(inst));
                order.setUpdatedAt(Instant.now());

                orders.add(orderRepository.save(order));
            }
        }

        batch.setTotalOrdersProcessed(orders.size());
        batch.setStatus("COMPLETED");
        batchRepository.save(batch);

        return new BatchResponse(batch, orders);
    }

    public boolean isEligible(InstallmentData installment, LocalDate horizonEndDate, boolean alreadyTracked) {
        boolean withinHorizon = !installment.dueDate().isAfter(horizonEndDate);
        boolean hasBalance = installment.totalPending().compareTo(BigDecimal.ZERO) > 0;
        return withinHorizon && (hasBalance || alreadyTracked);
    }

    public OrderStatus resolveStatus(InstallmentData installment) {
        if (installment.totalPending().compareTo(BigDecimal.ZERO) <= 0) {
            return OrderStatus.PAID;
        }
        if (installment.totalPending().compareTo(installment.originalDue()) < 0) {
            return OrderStatus.PARTIALLY_PAID;
        }
        return OrderStatus.PENDING;
    }

    public Mono<BatchResponse> getBatchById(UUID batchId) {
        return Mono.fromCallable(() -> batchRepository.findById(batchId)
                        .map(batch -> new BatchResponse(batch, orderRepository.findByLastBatchIdOrderByInstallmentNumberAsc(batchId))))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    public Flux<CollectionOrder> searchOrders(String loanReference, LocalDate dueDateTo, OrderStatus status) {
        Specification<CollectionOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (loanReference != null && !loanReference.isBlank()) {
                predicates.add(cb.equal(root.get("loanReference"), loanReference));
            }
            if (dueDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueDateTo));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return Mono.fromCallable(() -> orderRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "installmentNumber")))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public record BatchResponse(BillingBatch batch, List<CollectionOrder> orders) {}
}
