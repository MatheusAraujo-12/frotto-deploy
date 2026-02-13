package com.localuz.service.mapper;

import com.localuz.domain.User;
import com.localuz.service.dto.MeResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class MeMapper {

    public MeResponseDTO toDto(User user) {
        MeResponseDTO dto = new MeResponseDTO();
        dto.setLogin(user.getLogin());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setImageUrl(user.getImageUrl());
        dto.setLangKey(user.getLangKey());
        dto.setPersonalName(user.getPersonalName());
        dto.setPersonalCpf(user.getPersonalCpf());
        dto.setPersonalBirthDate(user.getPersonalBirthDate());
        dto.setPersonalEmail(user.getPersonalEmail());
        dto.setPersonalPhone(user.getPersonalPhone());
        dto.setTaxPersonType(user.getTaxPersonType());
        dto.setTaxLandlordName(user.getTaxLandlordName());
        dto.setTaxCpf(user.getTaxCpf());
        dto.setTaxEmail(user.getTaxEmail());
        dto.setTaxPhone(user.getTaxPhone());
        dto.setTaxCompanyName(user.getTaxCompanyName());
        dto.setTaxCnpj(user.getTaxCnpj());
        dto.setTaxIe(user.getTaxIe());
        dto.setTaxContactPhone(user.getTaxContactPhone());
        dto.setTaxAddress(user.getTaxAddress());
        return dto;
    }
}
