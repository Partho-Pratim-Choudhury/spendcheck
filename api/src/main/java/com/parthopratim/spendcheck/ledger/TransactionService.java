package com.parthopratim.spendcheck.ledger;

import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.common.ConflictException;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.input.RawInputRepository;
import com.parthopratim.spendcheck.ledger.TransactionDtos.CreateTransactionRequest;
import com.parthopratim.spendcheck.ledger.TransactionDtos.TransactionResponse;
import com.parthopratim.spendcheck.ledger.TransactionDtos.UpdateTransactionRequest;
import com.parthopratim.spendcheck.plan.BudgetPlanRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofDays(1);

    public record CreateResult(TransactionResponse transaction, boolean created) {}

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final RawInputRepository rawInputs;
    private final BudgetPlanRepository plans;
    private final UserTime userTime;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories,
                              RawInputRepository rawInputs, BudgetPlanRepository plans, UserTime userTime,
                              PlatformTransactionManager txManager) {
        this.transactions = transactions;
        this.categories = categories;
        this.rawInputs = rawInputs;
        this.plans = plans;
        this.userTime = userTime;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    /**
     * Idempotent on the client id. Not one transaction: if a concurrent request with the same id wins the
     * insert, ours fails on the primary key and is rolled back, so the replay must read in a fresh one.
     */
    public CreateResult create(long userId, CreateTransactionRequest req) {
        try {
            return tx.execute(status -> createOrReplay(userId, req));
        } catch (DataIntegrityViolationException e) {
            // Map inside the transaction: the response reads the lazy category.
            return readTx.execute(status -> transactions.findById(req.id()).map(t -> replay(userId, t)))
                    .orElseThrow(() -> e);
        }
    }

    private CreateResult createOrReplay(long userId, CreateTransactionRequest req) {
        Optional<Transaction> existing = transactions.findById(req.id());
        if (existing.isPresent()) {
            return replay(userId, existing.get());
        }
        Category category = usableCategory(userId, req.categoryId());
        checkSpentAt(req.spentAt());
        if (req.rawInputId() != null && !rawInputs.existsByIdAndUserId(req.rawInputId(), userId)) {
            throw new FieldValidationException("rawInputId", "raw input does not exist");
        }
        Transaction t = new Transaction(req.id(), userId, req.amountPaise(), category, blankToNull(req.merchant()),
                blankToNull(req.note()), req.spentAt(), req.needOrWant(), req.source(),
                TransactionStatus.CONFIRMED);
        t.setRawInputId(req.rawInputId());
        t.setPlanId(activePlanCovering(userId, req.spentAt()));
        transactions.saveAndFlush(t);
        return new CreateResult(TransactionResponse.from(t), true);
    }

    private static CreateResult replay(long userId, Transaction t) {
        if (!t.getUserId().equals(userId)) {
            throw new ConflictException("Transaction id " + t.getId() + " is already in use.");
        }
        return new CreateResult(TransactionResponse.from(t), false);
    }

    @Transactional
    public TransactionResponse update(long userId, UUID id, UpdateTransactionRequest req) {
        Transaction t = owned(userId, id);
        if (t.getStatus() == TransactionStatus.CANCELLED) {
            throw new ConflictException("Transaction " + id + " is cancelled and cannot be edited.");
        }
        if (req.amountPaise() != null) {
            t.setAmountPaise(req.amountPaise());
        }
        if (req.categoryId() != null && !req.categoryId().equals(t.getCategory().getId())) {
            t.setCategory(usableCategory(userId, req.categoryId()));
        }
        if (req.merchant() != null) {
            t.setMerchant(blankToNull(req.merchant()));
        }
        if (req.note() != null) {
            t.setNote(blankToNull(req.note()));
        }
        if (req.spentAt() != null) {
            checkSpentAt(req.spentAt());
            t.setSpentAt(req.spentAt());
            t.setPlanId(activePlanCovering(userId, req.spentAt()));
        }
        if (req.needOrWant() != null) {
            t.setNeedOrWant(req.needOrWant());
        }
        transactions.flush();
        return TransactionResponse.from(t);
    }

    /** Soft delete; repeating it is a no-op. */
    @Transactional
    public void cancel(long userId, UUID id) {
        Transaction t = owned(userId, id);
        if (t.getStatus() != TransactionStatus.CANCELLED) {
            t.setStatus(TransactionStatus.CANCELLED);
        }
    }

    /**
     * Chat "undo": cancels the user's most recently created CONFIRMED transaction if it was created after
     * {@code since}. Returns what was cancelled.
     */
    @Transactional
    public Optional<TransactionResponse> undoLatest(long userId, Instant since) {
        return transactions.findFirstByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(userId,
                        TransactionStatus.CONFIRMED, since)
                .map(t -> {
                    t.setStatus(TransactionStatus.CANCELLED);
                    transactions.flush();
                    return TransactionResponse.from(t);
                });
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(long userId, LocalDate from, LocalDate to, Long categoryId,
                                          NeedOrWant needOrWant) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException("from", "must not be after 'to'");
        }
        ZoneId zone = userTime.zone(userId);
        Specification<Transaction> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.notEqual(root.get("status"), TransactionStatus.CANCELLED));
        if (from != null) {
            Instant start = from.atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("spentAt"), start));
        }
        if (to != null) {
            Instant endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("spentAt"), endExclusive));
        }
        if (categoryId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (needOrWant != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("needOrWant"), needOrWant));
        }
        Sort newestFirst = Sort.by(Sort.Order.desc("spentAt"), Sort.Order.desc("createdAt"));
        return transactions.findAll(spec, newestFirst).stream().map(TransactionResponse::from).toList();
    }

    private Transaction owned(long userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found."));
    }

    private Category usableCategory(long userId, long categoryId) {
        Category c = categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new FieldValidationException("categoryId", "category does not exist"));
        if (c.isArchived()) {
            throw new FieldValidationException("categoryId", "category is archived");
        }
        return c;
    }

    /** The ACTIVE plan whose period contains the spend's date (user tz), or null. */
    private Long activePlanCovering(long userId, Instant spentAt) {
        LocalDate date = LocalDate.ofInstant(spentAt, userTime.zone(userId));
        return plans.findActiveIdCovering(userId, date).orElse(null);
    }

    private void checkSpentAt(Instant spentAt) {
        if (spentAt.isAfter(userTime.now().plus(MAX_FUTURE_SKEW))) {
            throw new FieldValidationException("spentAt", "must not be more than 1 day in the future");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
