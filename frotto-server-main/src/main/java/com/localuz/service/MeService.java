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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MeService {

    private static final String ENTITY_NAME = "me";
    private static final String TAX_TYPE_CPF = "CPF";
    private static final String TAX_TYPE_CNPJ = "CNPJ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MeMapper meMapper;

    public MeService(UserRepository userRepository, PasswordEncoder passwordEncoder, MeMapper meMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.meMapper = meMapper;
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
}
