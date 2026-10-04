package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import com.localuz.repository.CarBodyDamageRepository;
import com.localuz.repository.CarRepository;
import com.localuz.service.dto.BodyDamageDTO;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.service.storage.StorageCategory;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.localuz.domain.CarBodyDamage}.
 *
 * <p>Photos go through {@link FileStorageGateway}: in s3 mode the legacy calls and their order are unchanged; in local
 * mode new files are stored before the row changes, removed again on rollback, and replaced/deleted files are only
 * removed after commit (historical keys never). Responses carry the resolved URLs in imageUrl/imageUrl2.
 */
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
    private final FileStorageGateway fileStorage;

    public CarBodyDamageResource(
        CarBodyDamageRepository carBodyDamageRepository,
        CarRepository carRepository,
        FileStorageGateway fileStorage
    ) {
        this.carBodyDamageRepository = carBodyDamageRepository;
        this.carRepository = carRepository;
        this.fileStorage = fileStorage;
    }

    @GetMapping("/car-body-damages/car/{carId}")
    public List<CarBodyDamage> getCarBodyDamagesByCar(@PathVariable Long carId) {
        log.debug("REST request to get CarBodyDamages  by carId : {}", carId);
        List<CarBodyDamage> carBodyDamages = carBodyDamageRepository.findByCurrentUserAndCarIdByDate(carId);
        carBodyDamages.forEach(this::withImageUrls);
        return carBodyDamages;
    }

    @GetMapping("/car-body-damages/car/{carId}/active")
    public List<CarBodyDamage> getActiveCarBodyDamagesByCar(@PathVariable Long carId) {
        log.debug("REST request to get Active CarBodyDamages by carId : {}", carId);
        List<CarBodyDamage> activeCarBodyDamages = carBodyDamageRepository.findActiveByCurrentUserAndCarIdByDate(carId);
        activeCarBodyDamages.forEach(this::withImageUrls);
        return activeCarBodyDamages;
    }

    @GetMapping("/car-body-damages/{id}")
    public CarBodyDamage getCarBodyDamageById(@PathVariable Long id) {
        log.debug("REST request to get CarBodyDamages  by id : {}", id);
        Optional<CarBodyDamage> carBodyDamage = carBodyDamageRepository.findByCurrentUserAndCarBdId(id);
        if (carBodyDamage.isPresent()) {
            return withImageUrls(carBodyDamage.get());
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
        com.localuz.service.VehicleLifecycleService.requireOperational(existingCarOpt.get());
        carBodyDamage.setCar(existingCarOpt.get());
        carBodyDamage.setImagePath(storePhoto(bodyDamageDto.getFile(), bodyDamageDto.getFileBase64(), "01"));
        carBodyDamage.setImagePath2(storePhoto(bodyDamageDto.getFile2(), bodyDamageDto.getFile2Base64(), "02"));
        CarBodyDamage result = carBodyDamageRepository.save(carBodyDamage);
        return ResponseEntity
            .created(new URI("/api/car-body-damages/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(withImageUrls(result));
    }

    @DeleteMapping("/car-body-damages/{id}")
    public ResponseEntity<Void> deleteCarBodyDamage(@PathVariable Long id) {
        log.debug("REST request to delete CarBodyDamage : {}", id);
        Optional<CarBodyDamage> carBodyDamage = carBodyDamageRepository.findByCurrentUserAndCarBdId(id);
        if (!carBodyDamage.isPresent()) {
            throw new BadRequestAlertException("Car Body Damage not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        com.localuz.service.VehicleLifecycleService.requireOperational(carBodyDamage.get().getCar());
        String imagePath = carBodyDamage.get().getImagePath();
        String imagePath2 = carBodyDamage.get().getImagePath2();
        carBodyDamageRepository.deleteById(id);
        if (fileStorage.isLocalMode()) {
            fileStorage.deleteAfterCommit(imagePath);
            fileStorage.deleteAfterCommit(imagePath2);
        } else {
            // Legacy behavior, unchanged.
            if (imagePath != null) {
                fileStorage.deleteFromLegacyS3(imagePath);
            }
            if (imagePath2 != null) {
                fileStorage.deleteFromLegacyS3(imagePath2);
            }
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
        com.localuz.service.VehicleLifecycleService.requireOperational(existingCarBodyDamage.getCar());
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
            existingCarBodyDamage.setImagePath(
                replacePhoto(existingCarBodyDamage.getImagePath(), carBodyDamage.getFile(), carBodyDamage.getFileBase64(), "01")
            );
        }
        if (hasMultipartFile(carBodyDamage.getFile2()) || hasBase64(carBodyDamage.getFile2Base64())) {
            existingCarBodyDamage.setImagePath2(
                replacePhoto(existingCarBodyDamage.getImagePath2(), carBodyDamage.getFile2(), carBodyDamage.getFile2Base64(), "02")
            );
        }
        if (carBodyDamage.getCost() != null) {
            existingCarBodyDamage.setCost(carBodyDamage.getCost());
        }
        if (carBodyDamage.getResolved() != null) {
            existingCarBodyDamage.setResolved(carBodyDamage.getResolved());
        }
        carBodyDamageRepository.save(existingCarBodyDamage);

        return ResponseUtil.wrapOrNotFound(
            carBodyDamageOpt.map(this::withImageUrls),
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, carBodyDamage.getId().toString())
        );
    }

    @GetMapping("/admin/car-body-damages")
    public List<CarBodyDamage> getAllCarBodyDamages() {
        log.debug("REST request to get all CarBodyDamages");
        List<CarBodyDamage> carBodyDamages = carBodyDamageRepository.findAll();
        carBodyDamages.forEach(this::withImageUrls);
        return carBodyDamages;
    }

    /**
     * Stores an uploaded photo (multipart first, then Base64) and returns its key, or "" when none was sent. The key is
     * always generated by the storage: client file names never become paths. Local mode: content validated by its
     * real type and the photo size limit, and the file is removed again if the transaction rolls back.
     */
    private String storePhoto(MultipartFile file, String base64, String legacyIdentifier) {
        String key = "";
        if (hasMultipartFile(file)) {
            key = fileStorage.store(StorageCategory.CAR_DAMAGE, file, legacyIdentifier);
        } else if (hasBase64(base64)) {
            key = fileStorage.storeBase64(StorageCategory.CAR_DAMAGE, base64, legacyIdentifier);
        }
        if (key != null && !key.isEmpty()) {
            fileStorage.deleteOnRollback(key);
        }
        return key;
    }

    /**
     * s3 mode: the legacy order, unchanged (previous object deleted, then the new upload). Local mode: the new file is
     * stored first and the previous one is only deleted after commit; historical keys are never deleted.
     */
    private String replacePhoto(String previousKey, MultipartFile file, String base64, String legacyIdentifier) {
        if (!fileStorage.isLocalMode()) {
            if (previousKey != null && !previousKey.isEmpty()) {
                fileStorage.deleteFromLegacyS3(previousKey);
            }
            return storePhoto(file, base64, legacyIdentifier);
        }
        String key = storePhoto(file, base64, legacyIdentifier);
        fileStorage.deleteAfterCommit(previousKey);
        return key;
    }

    private CarBodyDamage withImageUrls(CarBodyDamage carBodyDamage) {
        carBodyDamage.setImageUrl(fileStorage.displayUrl(carBodyDamage.getImagePath()));
        carBodyDamage.setImageUrl2(fileStorage.displayUrl(carBodyDamage.getImagePath2()));
        return carBodyDamage;
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
