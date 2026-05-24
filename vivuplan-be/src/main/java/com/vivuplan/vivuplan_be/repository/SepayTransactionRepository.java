package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.SepayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SepayTransactionRepository extends JpaRepository<SepayTransaction, Long> {
    boolean existsBySepayId(String sepayId);
}
