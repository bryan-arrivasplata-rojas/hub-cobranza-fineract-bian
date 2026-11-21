package com.bank.collectionhub.adapter;

import com.bank.collectionhub.model.CollectionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CollectionOrder, UUID>, JpaSpecificationExecutor<CollectionOrder> {

    Optional<CollectionOrder> findByLoanReferenceAndInstallmentNumber(String loanReference, int installmentNumber);

    List<CollectionOrder> findByLastBatchIdOrderByInstallmentNumberAsc(UUID lastBatchId);
}
