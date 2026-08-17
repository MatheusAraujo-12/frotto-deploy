package com.localuz.repository;

import com.localuz.domain.Expense;
import com.localuz.domain.Inspection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the Expense entity. */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @Query(
        "select expense from Expense expense  join expense.inspection.car car  where car.user.login = ?#{principal.username} and expense.id = :id"
    )
    Optional<Expense> findByCurrentUserAndExpenseId(@Param("id") Long id);

    List<Expense> findAllByInspection(Inspection inspection);

    @Modifying
    @Query("delete from Expense expense where expense.id in :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);
}
