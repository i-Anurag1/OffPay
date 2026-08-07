package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    // Pessimistic lock used inside SettlementService's transaction so that a
    // concurrent debit/credit on the same account can't interleave, on top of
    // the optimistic @Version defense-in-depth on the entity itself.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.vpa = :vpa")
    Optional<Account> findByVpaForUpdate(String vpa);

    List<Account> findAllByOrderByVpaAsc();
}
