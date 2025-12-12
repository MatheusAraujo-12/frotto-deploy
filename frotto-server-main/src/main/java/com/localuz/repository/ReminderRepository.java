package com.localuz.repository;

import com.localuz.domain.Reminder;
import com.localuz.domain.Reminder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Reminder entity. */
@SuppressWarnings("unused")
@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    @Query(
        "select reminder from Reminder reminder  join reminder.car car  where car.user.login = ?#{principal.username} and car.id = :carId"
    )
    List<Reminder> findByCurrentUserAndCarId(@Param("carId") Long carId);

    @Query(
        "select reminder from Reminder reminder  join reminder.car car  where car.user.login = ?#{principal.username} and reminder.id = :id"
    )
    Optional<Reminder> findByCurrentUserAndReminderId(@Param("id") Long id);
}
