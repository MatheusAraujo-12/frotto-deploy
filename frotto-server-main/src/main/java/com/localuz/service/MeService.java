package com.localuz.service;

import com.localuz.domain.User;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.ChangePasswordDTO;
import com.localuz.service.dto.MeResponseDTO;
import com.localuz.service.dto.UpdatePersonalDTO;
import com.localuz.service.dto.UpdateTaxDataDTO;
import com.localuz.service.mapper.MeMapper;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.service.storage.StorageCategory;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.vm.ManagedUserVM;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class MeService {

    private static final String ENTITY_NAME = "me";
    private static final String TAX_TYPE_CPF = "CPF";
    private static final String TAX_TYPE_CNPJ = "CNPJ";
    private final Logger log = LoggerFactory.getLogger(MeService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MeMapper meMapper;
    private final FileStorageGateway fileStorage;

    public MeService(UserRepository userRepository, PasswordEncoder passwordEncoder, MeMapper meMapper, FileStorageGateway fileStorage) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.meMapper = meMapper;
        this.fileStorage = fileStorage;
    }

    @Transactional(readOnly = true)
    public MeResponseDTO getMe(String currentUser) {
        User user = getCurrentUser(currentUser);
        return toDto(user);
    }

    public MeResponseDTO updatePersonal(String currentUser, UpdatePersonalDTO dto) {
        User user = getCurrentUser(currentUser);

        String personalCpf = normalizeDigits(dto.getPersonalCpf());
        validateCpf(personalCpf, "personalcpf");

        user.setFirstName(normalizeText(dto.getFirstName()));
        user.setLastName(normalizeText(dto.getLastName()));
        // imageUrl in the payload is ignored: the avatar key only changes through upload/remove, so a client can
        // never point it at an arbitrary key or URL that would later be served or deleted as a stored file.
        user.setLangKey(normalizeText(dto.getLangKey()));
        user.setPersonalName(normalizeText(dto.getPersonalName()));
        user.setPersonalCpf(personalCpf);
        user.setPersonalBirthDate(dto.getPersonalBirthDate());
        user.setPersonalEmail(normalizeEmail(dto.getPersonalEmail()));
        user.setPersonalPhone(normalizeDigits(dto.getPersonalPhone()));

        User savedUser = userRepository.save(user);
        return toDto(savedUser);
    }

    public MeResponseDTO updateTaxData(String currentUser, UpdateTaxDataDTO dto) {
        User user = getCurrentUser(currentUser);
        String taxPersonType = normalizeTaxPersonType(dto.getTaxPersonType());

        if (!TAX_TYPE_CPF.equals(taxPersonType) && !TAX_TYPE_CNPJ.equals(taxPersonType)) {
            throw new BadRequestAlertException("Tipo de pessoa fiscal invalido", ENTITY_NAME, "invalidtaxtype");
        }

        user.setTaxPersonType(taxPersonType);

        if (TAX_TYPE_CPF.equals(taxPersonType)) {
            String taxCpf = normalizeDigits(dto.getTaxCpf());
            validateCpf(taxCpf, "taxcpf");

            user.setTaxLandlordName(normalizeText(dto.getTaxLandlordName()));
            user.setTaxCpf(taxCpf);
            user.setTaxEmail(normalizeEmail(dto.getTaxEmail()));
            user.setTaxPhone(normalizeDigits(dto.getTaxPhone()));

            user.setTaxCompanyName(null);
            user.setTaxCnpj(null);
            user.setTaxIe(null);
            user.setTaxContactPhone(null);
            user.setTaxAddress(null);
        } else {
            String taxCnpj = normalizeDigits(dto.getTaxCnpj());
            validateCnpj(taxCnpj, "taxcnpj");

            user.setTaxCompanyName(normalizeText(dto.getTaxCompanyName()));
            user.setTaxCnpj(taxCnpj);
            user.setTaxIe(normalizeText(dto.getTaxIe()));
            user.setTaxContactPhone(normalizeDigits(dto.getTaxContactPhone()));
            user.setTaxAddress(normalizeText(dto.getTaxAddress()));

            user.setTaxLandlordName(null);
            user.setTaxCpf(null);
            user.setTaxEmail(null);
            user.setTaxPhone(null);
        }

        User savedUser = userRepository.save(user);
        return toDto(savedUser);
    }

    public void changePassword(String currentUser, ChangePasswordDTO dto) {
        if (isPasswordLengthInvalid(dto.getNewPassword())) {
            throw new BadRequestAlertException("Nova senha invalida", ENTITY_NAME, "invalidnewpassword");
        }

        User user = getCurrentUser(currentUser);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BadRequestAlertException("Senha antiga invalida", ENTITY_NAME, "invalidoldpassword");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    public MeResponseDTO uploadAvatar(String currentUser, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestAlertException("Imagem nao enviada", ENTITY_NAME, "emptyavatar");
        }

        String contentType = StringUtils.defaultString(file.getContentType());
        String originalFilename = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/") && !isAllowedImageFilename(originalFilename)) {
            throw new BadRequestAlertException("Arquivo de imagem invalido", ENTITY_NAME, "invalidavatar");
        }

        User user = getCurrentUser(currentUser);
        String previousKey = user.getImageUrl();
        String key = storeUserImage(StorageCategory.AVATAR, file, "USER_" + user.getId(), "avataruploadfailed");

        user.setImageUrl(key);
        User savedUser = userRepository.save(user);
        fileStorage.deleteAfterCommit(previousKey);
        return toDto(savedUser);
    }

    public MeResponseDTO removeAvatar(String currentUser) {
        User user = getCurrentUser(currentUser);
        String currentImageUrl = StringUtils.trimToNull(user.getImageUrl());

        if (currentImageUrl != null) {
            releaseStoredImage(currentImageUrl, "avatar");
        }

        user.setImageUrl(null);
        User savedUser = userRepository.save(user);
        return toDto(savedUser);
    }

    public MeResponseDTO uploadLogo(String currentUser, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestAlertException("Imagem nao enviada", ENTITY_NAME, "emptylogo");
        }

        String contentType = StringUtils.defaultString(file.getContentType());
        String originalFilename = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/") && !isAllowedImageFilename(originalFilename)) {
            throw new BadRequestAlertException("Arquivo de imagem invalido", ENTITY_NAME, "invalidlogo");
        }

        User user = getCurrentUser(currentUser);
        String previousKey = user.getLogoUrl();
        String key = storeUserImage(StorageCategory.LOGO, file, "LOGO_" + user.getId(), "logouploadfailed");

        user.setLogoUrl(key);
        User savedUser = userRepository.save(user);
        fileStorage.deleteAfterCommit(previousKey);
        return toDto(savedUser);
    }

    public MeResponseDTO removeLogo(String currentUser) {
        User user = getCurrentUser(currentUser);
        String currentLogoUrl = StringUtils.trimToNull(user.getLogoUrl());

        if (currentLogoUrl != null) {
            releaseStoredImage(currentLogoUrl, "logomarca");
        }

        user.setLogoUrl(null);
        User savedUser = userRepository.save(user);
        return toDto(savedUser);
    }

    private MeResponseDTO toDto(User user) {
        MeResponseDTO dto = meMapper.toDto(user);
        dto.setAvatarUrl(fileStorage.displayUrl(user.getImageUrl()));
        dto.setLogoAccessUrl(fileStorage.displayUrl(user.getLogoUrl()));
        return dto;
    }

    /**
     * Stores the new image before the user row changes. Local mode: validated by real content (400) or refused when
     * the volume is unavailable (503) with nothing changed; the new file is removed again if the transaction rolls
     * back. S3 mode: the legacy upload, unchanged.
     */
    private String storeUserImage(StorageCategory category, MultipartFile file, String legacyIdentifier, String failureKey) {
        String key = fileStorage.store(category, file, legacyIdentifier);
        if (StringUtils.isBlank(key)) {
            throw new BadRequestAlertException("Falha ao enviar imagem", ENTITY_NAME, failureKey);
        }
        fileStorage.deleteOnRollback(key);
        return key;
    }

    /**
     * Local mode: the stored value is a key; its file is deleted only after the removal commits, historical keys are
     * never deleted and nothing reaches S3. S3 mode: the legacy immediate delete, failures ignored (unchanged).
     */
    private void releaseStoredImage(String storedValue, String label) {
        if (fileStorage.isLocalMode()) {
            fileStorage.deleteAfterCommit(storedValue);
            return;
        }
        String fileName = extractFileName(storedValue);
        try {
            fileStorage.deleteFromLegacyS3(fileName);
        } catch (Exception ex) {
            log.warn("Falha ao remover {} do S3: {}", label, fileName, ex);
        }
    }

    private User getCurrentUser(String currentUser) {
        return userRepository
            .findOneByLogin(currentUser)
            .orElseThrow(() -> new BadRequestAlertException("Usuario autenticado nao encontrado", ENTITY_NAME, "usernotfound"));
    }

    private String normalizeTaxPersonType(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue == null ? null : normalizedValue.toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        String normalizedValue = normalizeText(value);
        return normalizedValue == null ? null : normalizedValue.toLowerCase(Locale.ROOT);
    }

    private String normalizeDigits(String value) {
        String normalizedValue = StringUtils.defaultString(value).replaceAll("\\D", StringUtils.EMPTY);
        return StringUtils.isBlank(normalizedValue) ? null : normalizedValue;
    }

    private String normalizeText(String value) {
        String normalizedValue = StringUtils.trimToNull(value);
        return StringUtils.isBlank(normalizedValue) ? null : normalizedValue;
    }

    private void validateCpf(String cpf, String errorKey) {
        if (cpf != null && !cpf.matches("\\d{11}")) {
            throw new BadRequestAlertException("CPF deve conter 11 digitos", ENTITY_NAME, errorKey);
        }
    }

    private void validateCnpj(String cnpj, String errorKey) {
        if (cnpj != null && !cnpj.matches("\\d{14}")) {
            throw new BadRequestAlertException("CNPJ deve conter 14 digitos", ENTITY_NAME, errorKey);
        }
    }

    private boolean isPasswordLengthInvalid(String password) {
        return (
            StringUtils.isEmpty(password) ||
            password.length() < ManagedUserVM.PASSWORD_MIN_LENGTH ||
            password.length() > ManagedUserVM.PASSWORD_MAX_LENGTH
        );
    }

    private String extractFileName(String imageUrl) {
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        int index = imageUrl.lastIndexOf('/');
        if (index < 0 || index == imageUrl.length() - 1) {
            return imageUrl;
        }
        return imageUrl.substring(index + 1);
    }

    private boolean isAllowedImageFilename(String filename) {
        return (
            filename.endsWith(".jpg") ||
            filename.endsWith(".jpeg") ||
            filename.endsWith(".png") ||
            filename.endsWith(".webp") ||
            filename.endsWith(".heic")
        );
    }
}
