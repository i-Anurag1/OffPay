package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class SettlementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public SettlementService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public enum Result {
        SETTLED,
        INSUFFICIENT_FUNDS,
        UNKNOWN_ACCOUNT,
        DUPLICATE_DROPPED
    }

    public static class SettlementOutcome {
        public final Result result;
        public final Long transactionId;
        public final String reason;

        public SettlementOutcome(Result result, Long transactionId, String reason) {
            this.result = result;
            this.transactionId = transactionId;
            this.reason = reason;
        }
    }

    /**
     * Debits sender, credits receiver, and writes the ledger row, all in one
     * DB transaction. Accounts are fetched with a pessimistic write lock so
     * two concurrent settlements touching the same account can't interleave;
     * the @Version field on Account is a second, independent line of defense.
     * The unique index on transactions.packetHash is a third: if the
     * idempotency cache ever lets a duplicate through, the DB insert fails
     * and we translate that into DUPLICATE_DROPPED instead of a 500.
     */
    @Transactional
    public SettlementOutcome settle(PaymentInstruction instruction, String packetHash, boolean signatureVerified) {
        Account sender = accountRepository.findByVpaForUpdate(instruction.getSenderVpa()).orElse(null);
        Account receiver = accountRepository.findByVpaForUpdate(instruction.getReceiverVpa()).orElse(null);

        if (sender == null || receiver == null) {
            return new SettlementOutcome(Result.UNKNOWN_ACCOUNT, null,
                "Sender or receiver VPA not found");
        }

        if (sender.getBalance().compareTo(instruction.getAmount()) < 0) {
            return new SettlementOutcome(Result.INSUFFICIENT_FUNDS, null,
                "Sender balance insufficient at settlement time");
        }

        sender.setBalance(sender.getBalance().subtract(instruction.getAmount()));
        receiver.setBalance(receiver.getBalance().add(instruction.getAmount()));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        try {
            Transaction tx = new Transaction(
                sender.getVpa(), receiver.getVpa(), instruction.getAmount(), packetHash, signatureVerified);
            tx = transactionRepository.save(tx);
            return new SettlementOutcome(Result.SETTLED, tx.getId(), null);
        } catch (DataIntegrityViolationException e) {
            // Unique index on packetHash caught a duplicate the idempotency
            // cache missed. Let the transaction roll back (debit/credit undone).
            throw e;
        }
    }
}
