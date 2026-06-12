package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.CreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditLedgerRepository extends JpaRepository<CreditLedger, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CreditLedger ledger SET ledger.trip = null WHERE ledger.trip.id = :tripId")
    int detachFromTrip(@Param("tripId") Long tripId);
}
