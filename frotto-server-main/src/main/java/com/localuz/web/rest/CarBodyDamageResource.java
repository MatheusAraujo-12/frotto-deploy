package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import com.localuz.repository.CarBodyDamageRepository;
import com.localuz.repository.CarRepository;
import com.localuz.service.AWSS3FileService;
import com.localuz.service.dto.BodyDamageDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.mail.Multipart;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/** REST controller for managing {@link com.localuz.domain.CarBodyDamage}. */
@RestController
@RequestMapping("/api")
@Transactional
public class CarBodyDamageResource {

    private final Logger log = LoggerFactory.getLogger(CarBodyDamageResource.class);

    private static final String ENTITY_NAME = "carBodyDamage";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CarBodyDamageRepository carBodyDamageRepository;

    private final CarRepository carRepository;
    private final AWSS3FileService awsS3Service;

    public CarBodyDamageResource(
        CarBodyDamageRepository carBodyDamageRepository,
        CarRepository carRepository,
        AWSS3FileService awsS3Service
    ) {
        this.carBodyDamageRepository = carBodyDamageRepository;
        this.carRepository = carRepository;
        this.awsS3Service = awsS3Service;
    }

    @GetMapping("/car-body-damages/car/{carId}")
    public List<CarBodyDamage> getCarBodyDamagesByCar(@PathVariable Long carId) {
        log.debug("REST request to get CarBodyDamages  by carId : {}", carId);
        List<CarBodyDamage> carBodyDamages = carBodyDamageRepository.findByCurrentUserAndCarIdByDate(carId);
        return carBodyDamages;
    }

    @GetMapping("/car-body-damages/car/{carId}/active")
    public List<CarBodyDamage> getActiveCarBodyDamagesByCar(@PathVariable Long carId) {
        log.debug("REST request to get Active CarBodyDamages by carId : {}", carId);
        List<CarBodyDamage> activeCarBodyDamages = carBodyDamageRepository.findActiveByCurrentUserAndCarIdByDate(carId);
        return activeCarBodyDamages;
    }

    @GetMapping("/car-body-damages/{id}")
    public CarBodyDamage getCarBodyDamageById(@PathVariable Long id) {
        log.debug("REST request to get CarBodyDamages  by id : {}", id);
        Optional<CarBodyDamage> carBodyDamage = carBodyDamageRepository.findByCurrentUserAndCarBdId(id);
        if (carBodyDamage.isPresent()) {
            return carBodyDamage.get();
        }
        return null;
    }

    @PostMapping("/car-body-damages/car/{carId}")
    public ResponseEntity<CarBodyDamage> createCarBodyDamageByCar(@PathVariable Long carId, @ModelAttribute BodyDamageDTO bodyDamageDto)
        throws URISyntaxException {
        CarBodyDamage carBodyDamage = fromDto(bodyDamageDto);

        log.debug("REST request to save CarBodyDamage : {}", carBodyDamage);
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        carBodyDamage.setCar(existingCarOpt.get());
        String filePath = "";
        if (hasMultipartFile(bodyDamageDto.getFile())) {
            filePath = awsS3Service.uploadFile(bodyDamageDto.getFile(), "01");
        } else if (hasBase64(bodyDamageDto.getFileBase64())) {
            filePath = awsS3Service.uploadBase64(bodyDamageDto.getFileBase64(), "01");
        }
        carBodyDamage.setImagePath(filePath);

        String filePath2 = "";
        if (hasMultipartFile(bodyDamageDto.getFile2())) {
            filePath2 = awsS3Service.uploadFile(bodyDamageDto.getFile2(), "02");
        } else if (hasBase64(bodyDamageDto.getFile2Base64())) {
            filePath2 = awsS3Service.uploadBase64(bodyDamageDto.getFile2Base64(), "02");
        }
        carBodyDamage.setImagePath2(filePath2);
        CarBodyDamage result = carBodyDamageRepository.save(carBodyDamage);
        return ResponseEntity
            .created(new URI("/api/car-body-damages/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/car-body-damages/{id}")
    public ResponseEntity<Void> deleteCarBodyDamage(@PathVariable Long id) {
        log.debug("REST request to delete CarBodyDamage : {}", id);
        Optional<CarBodyDamage> carBodyDamage = carBodyDamageRepository.findByCurrentUserAndCarBdId(id);
        if (!carBodyDamage.isPresent()) {
            throw new BadRequestAlertException("Car Body Damage not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        String imagePath = carBodyDamage.get().getImagePath();
        String imagePath2 = carBodyDamage.get().getImagePath2();
        carBodyDamageRepository.deleteById(id);
        if (imagePath != null) {
            awsS3Service.deleteFile(imagePath);
        }
        if (imagePath2 != null) {
            awsS3Service.deleteFile(imagePath2);
        }
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PatchMapping(value = "/car-body-damages/{id}")
    public ResponseEntity<CarBodyDamage> partialUpdateCarBodyDamage(
        @PathVariable(value = "id", required = false) final Long id,
        @ModelAttribute BodyDamageDTO carBodyDamage
    ) {
        log.debug("REST request to partial update CarBodyDamage partially : {}, {}", id, carBodyDamage.toString());
        if (carBodyDamage.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, carBodyDamage.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<CarBodyDamage> carBodyDamageOpt = carBodyDamageRepository.findByCurrentUserAndCarBdId(id);
        if (!carBodyDamageOpt.isPresent()) {
            throw new BadRequestAlertException("Car Body Damage not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        CarBodyDamage existingCarBodyDamage = carBodyDamageOpt.get();
        if (carBodyDamage.getDate() != null) {
            existingCarBodyDamage.setDate(carBodyDamage.getDate());
        }
        if (carBodyDamage.getResponsible() != null) {
            existingCarBodyDamage.setResponsible(carBodyDamage.getResponsible());
        }
        if (carBodyDamage.getPart() != null) {
            existingCarBodyDamage.setPart(carBodyDamage.getPart());
        }
        if (hasMultipartFile(carBodyDamage.getFile()) || hasBase64(carBodyDamage.getFileBase64())) {
            if (existingCarBodyDamage.getImagePath() != null && !existingCarBodyDamage.getImagePath().isEmpty()) {
                awsS3Service.deleteFile(existingCarBodyDamage.getImagePath());
            }
            String filePath = hasMultipartFile(carBodyDamage.getFile())
                ? awsS3Service.uploadFile(carBodyDamage.getFile(), "01")
                : awsS3Service.uploadBase64(carBodyDamage.getFileBase64(), "01");
            existingCarBodyDamage.setImagePath(filePath);
        }
        if (hasMultipartFile(carBodyDamage.getFile2()) || hasBase64(carBodyDamage.getFile2Base64())) {
            if (existingCarBodyDamage.getImagePath2() != null && !existingCarBodyDamage.getImagePath2().isEmpty()) {
                awsS3Service.deleteFile(existingCarBodyDamage.getImagePath2());
            }
            String filePath2 = hasMultipartFile(carBodyDamage.getFile2())
                ? awsS3Service.uploadFile(carBodyDamage.getFile2(), "02")
                : awsS3Service.uploadBase64(carBodyDamage.getFile2Base64(), "02");
            existingCarBodyDamage.setImagePath2(filePath2);
        }
        if (carBodyDamage.getCost() != null) {
            existingCarBodyDamage.setCost(carBodyDamage.getCost());
        }
        if (carBodyDamage.getResolved() != null) {
            existingCarBodyDamage.setResolved(carBodyDamage.getResolved());
        }
        carBodyDamageRepository.save(existingCarBodyDamage);

        return ResponseUtil.wrapOrNotFound(
            carBodyDamageOpt,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, carBodyDamage.getId().toString())
        );
    }

    @GetMapping("/admin/car-body-damages")
    public List<CarBodyDamage> getAllCarBodyDamages() {
        log.debug("REST request to get all CarBodyDamages");
        return carBodyDamageRepository.findAll();
    }

    private CarBodyDamage fromDto(BodyDamageDTO dto) {
        CarBodyDamage carBodyDamage = new CarBodyDamage();
        carBodyDamage.setDate(dto.getDate());
        carBodyDamage.setResponsible(dto.getResponsible());
        carBodyDamage.setPart(dto.getPart());
        carBodyDamage.setCost(dto.getCost());
        carBodyDamage.setResolved(dto.getResolved());
        return carBodyDamage;
    }

    private boolean hasMultipartFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private boolean hasBase64(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
