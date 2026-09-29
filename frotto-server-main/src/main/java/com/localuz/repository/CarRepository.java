package com.localuz.repository;

import com.localuz.DTO.CarDriverDto;
import com.localuz.domain.Car;
import com.localuz.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the Car entity. */
public interface CarRepository extends JpaRepository<Car, Long> {
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select car from Car car where car.id = :id")
    Optional<Car> findByIdForUpdate(@Param("id") Long id);
    @Query(
        "select new com.localuz.DTO.CarDriverDto(car,driver.name) from Car car " +
        "left join DriverCar driverCar on driverCar.car = car and driverCar.concluded=false " +
        "left join Driver driver on driverCar.driver=driver " +
        "where car.user.login = ?#{principal.username} " +
        "and car.deleted = false"
    )
    List<CarDriverDto> findActiveByCurrentUserAndDriver();

    @Query("select car from Car car where car.user.login = ?#{principal.username}")
    List<Car> findByCurrentUser();

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.deleted = false")
    List<Car> findActiveByCurrentUser();

    @Query(
        "select car.group from Car car where car.user.login = ?#{principal.username} and car.deleted = false and car.group is not null group by car.group"
    )
    List<String> findActiveGroupsByCurrentUser();

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.deleted = false and car.group = :group")
    List<Car> findActiveByCurrentUserAndGroup(@Param("group") String group);

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.id = :id")
    Optional<Car> findByCurrentUserAndId(@Param("id") Long id);

    @Query(
        "select car from Car car " +
        "where car.user.login = ?#{principal.username} " +
        "and car.deleted = false " +
        "and (" +
        ":plate = '' " +
        "or upper(replace(replace(coalesce(car.plate, ''), '-', ''), ' ', '')) like concat('%', :plate, '%')" +
        ") " +
        "order by car.plate asc"
    )
    List<Car> searchByCurrentUserAndPlate(@Param("plate") String plate, Pageable pageable);

    List<Car> findAllByUserId(Long userId);

    Optional<Car> findByIdAndUser(Long id, User user);

    @Query("select count(car) from Car car where car.user.id = :userId and car.deleted = false")
    long countBillableByUserId(@Param("userId") Long userId);

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.deleted = true")
    List<Car> findDeletedByCurrentUser();

    @Query("select car from Car car where car.deleted = true or car.restoredAt is not null order by car.id desc")
    List<Car> findDeletionAudit();
}
