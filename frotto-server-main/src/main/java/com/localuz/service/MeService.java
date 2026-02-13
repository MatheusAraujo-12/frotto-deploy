package com.localuz.service;

import com.localuz.domain.User;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.ChangePasswordDTO;
import com.localuz.service.dto.MeResponseDTO;
import com.localuz.service.dto.UpdatePersonalDTO;
import com.localuz.service.dto.UpdateTaxDataDTO;
import com.localuz.service.mapper.MeMapper;
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
    private final AWSS3FileService awsS3FileService;

    public MeService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        MeMapper meMapper,
        AWSS3FileService awsS3FileService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.meMapper = meMapper;
        this.awsS3FileService = awsS3FileService;
    }

    @Transactional(readOnly = true)
    public MeResponseDTO getMe(String currentUser) {
        User user = getCurrentUser(currentUser);
        return meMapper.toDto(user);
    }

    public MeResponseDTO updatePersonal(String currentUser, UpdatePersonalDTO dto) {
        User user = getCurrentUser(currentUser);

        String personalCpf = normalizeDigits(dto.getPersonalCpf());
        validateCpf(personalCpf, "personalcpf");

        user.setFirstName(normalizeText(dto.getFirstName()));
        user.setLastName(normalizeText(dto.getLastName()));
        user.setImageUrl(normalizeText(dto.getImageUrl()));
        user.setLangKey(normalizeText(dto.getLangKey()));
        user.setPersonalName(normalizeText(dto.getPersonalName()));
        user.setPersonalCpf(personalCpf);
        user.setPersonalBirthDate(dto.getPersonalBirthDate());
        user.setPersonalEmail(normalizeEmail(dto.getPersonalEmail()));
        user.setPersonalPhone(normalizeDigits(dto.getPersonalPhone()));

        User savedUser = userRepository.save(user);
        return meMapper.toDto(savedUser);
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
        return meMapper.toDto(savedUser);
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
        if (!contentType.startsWith("image/")) {
            throw new BadRequestAlertException("Arquivo de imagem invalido", ENTITY_NAME, "invalidavatar");
        }

        User user = getCurrentUser(currentUser);
        String fileName = awsS3FileService.uploadFile(file, "USER_" + user.getId());
        if (StringUtils.isBlank(fileName)) {
            throw new BadRequestAlertException("Falha ao enviar imagem", ENTITY_NAME, "avataruploadfailed");
        }

        user.setImageUrl(fileName);
        User savedUser = userRepository.save(user);
        return meMapper.toDto(savedUser);
    }

    public MeResponseDTO removeAvatar(String currentUser) {
        User user = getCurrentUser(currentUser);
        String currentImageUrl = StringUtils.trimToNull(user.getImageUrl());

        if (currentImageUrl != null) {
            String fileName = extractFileName(currentImageUrl);
            try {
                awsS3FileService.deleteFile(fileName);
            } catch (Exception ex) {
                log.warn("Falha ao remover avatar do S3: {}", fileName, ex);
            }
        }

        user.setImageUrl(null);
        User savedUser = userRepository.save(user);
        return meMapper.toDto(savedUser);
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
}
