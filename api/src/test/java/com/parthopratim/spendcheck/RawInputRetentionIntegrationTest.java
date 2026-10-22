package com.parthopratim.spendcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parthopratim.spendcheck.input.RawInput;
import com.parthopratim.spendcheck.input.RawInputPurgeJob;
import com.parthopratim.spendcheck.input.RawInputRepository;
import com.parthopratim.spendcheck.input.RawInputType;
import com.parthopratim.spendcheck.ledger.TransactionRepository;
import com.parthopratim.spendcheck.ledger.TransactionStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RawInputRetentionIntegrationTest extends IntegrationTestSupport {

    @Autowired RawInputRepository rawInputs;
    @Autowired TransactionRepository transactions;
    @Autowired RawInputPurgeJob purgeJob;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void purgeDeletesOnlyOldRawInputsAndKeepsTheirTransactions() throws Exception {
        TestUser u = newUser(7);
        long old = rawInputs.save(new RawInput(u.id(), RawInputType.TEXT, "swiggy 450", null)).getId();
        long edge = rawInputs.save(new RawInput(u.id(), RawInputType.TEXT, "chai 20", null)).getId();
        long recent = rawInputs.save(new RawInput(u.id(), RawInputType.VOICE, null, "dmart 2340")).getId();
        jdbc.update("update raw_inputs set created_at = ? where id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(91))), old);
        jdbc.update("update raw_inputs set created_at = ? where id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(89))), edge);

        UUID tx = UUID.randomUUID();
        mvc.perform(asJson(u, post("/api/v1/transactions"), Map.of("id", tx.toString(), "amountPaise", 45_000,
                        "categoryId", u.cat("Food"), "spentAt", clock.instant().toString(), "source", "TEXT",
                        "rawInputId", old)))
                .andExpect(status().isCreated());

        assertThat(purgeJob.purge()).isGreaterThanOrEqualTo(1);
        assertThat(rawInputs.existsById(old)).isFalse();
        assertThat(rawInputs.existsById(edge)).isTrue();
        assertThat(rawInputs.existsById(recent)).isTrue();
        var t = transactions.findById(tx).orElseThrow();
        assertThat(t.getRawInputId()).isNull();
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);

        assertThat(purgeJob.purge()).isZero();   // nothing left that old
    }

    @Test
    void aRawInputLockedForShareCannotBePurgedUntilTheTransactionEnds() throws Exception {
        TestUser u = newUser(7);
        long id = rawInputs.save(new RawInput(u.id(), RawInputType.TEXT, "swiggy 450", null)).getId();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CompletableFuture<Integer> delete = tx.execute(s -> {
            assertThat(rawInputs.lockForShare(id, u.id())).contains(id);
            assertThat(rawInputs.lockForShare(id, u.id() + 1_000_000)).isEmpty();   // another user's: not found
            CompletableFuture<Integer> d = CompletableFuture.supplyAsync(
                    () -> jdbc.update("delete from raw_inputs where id = ?", id));
            assertThatThrownBy(() -> d.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            return d;
        });
        assertThat(delete.get(10, TimeUnit.SECONDS)).isEqualTo(1);   // proceeds once the lock is released
        assertThat(rawInputs.existsById(id)).isFalse();
    }
}
