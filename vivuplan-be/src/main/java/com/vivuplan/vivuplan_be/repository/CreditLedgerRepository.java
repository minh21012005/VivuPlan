package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.CreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditLedgerRepository extends JpaRepository<CreditLedger, Long> {
}
