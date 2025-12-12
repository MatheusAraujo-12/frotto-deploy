package com.localuz.repository;

import com.localuz.DTO.CarDriverDto;
import com.localuz.domain.Car;
import com.localuz.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Car entity. */
@SuppressWarnings("unused")
@Repository
public interface CarRepository extends JpaRepository<Car, Long> {
    @Query(
        "select new com.localuz.DTO.CarDriverDto(car,driver.name) from Car car " +
        "left join DriverCar driverCar on driverCar.car = car and driverCar.concluded=false " +
        "left join Driver driver on driverCar.driver=driver " +
        "where car.user.login = ?#{principal.username} " +
        "and car.active= true"
    )
    List<CarDriverDto> findActiveByCurrentUserAndDriver();

    @Query("select car from Car car where car.user.login = ?#{principal.username}")
    List<Car> findByCurrentUser();

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.active= true")
    List<Car> findActiveByCurrentUser();

    @Query(
        "select car.group from Car car where car.user.login = ?#{principal.username} and car.active= true and car.group is not null group by car.group"
    )
    List<String> findActiveGroupsByCurrentUser();

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.active= true and car.group = :group")
    List<Car> findActiveByCurrentUserAndGroup(@Param("group") String group);

    @Query("select car from Car car where car.user.login = ?#{principal.username} and car.id = :id")
    Optional<Car> findByCurrentUserAndId(@Param("id") Long id);

    List<Car> findAllByUserId(Long userId);

    Optional<Car> findByIdAndUser(Long id, User user);
}
