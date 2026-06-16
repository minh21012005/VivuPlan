package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.UserWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {
    Optional<UserWallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from UserWallet w where w.user.id = :userId")
    Optional<UserWallet> lockByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserWallet w
            set w.planCredits = w.planCredits - 1,
                w.updatedAt = CURRENT_TIMESTAMP
            where w.user.id = :userId
              and w.planCredits > 0
            """)
    int decrementPlanCreditIfAvailable(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserWallet w
            set w.editCredits = w.editCredits - 1,
                w.updatedAt = CURRENT_TIMESTAMP
            where w.user.id = :userId
              and w.editCredits > 0
            """)
    int decrementEditCreditIfAvailable(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserWallet w
            set w.suggestionCredits = w.suggestionCredits - 1,
                w.updatedAt = CURRENT_TIMESTAMP
            where w.user.id = :userId
              and w.suggestionCredits > 0
            """)
    int decrementSuggestionCreditIfAvailable(@Param("userId") Long userId);
}
