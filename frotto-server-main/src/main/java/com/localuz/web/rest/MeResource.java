package com.localuz.web.rest;

import com.localuz.security.SecurityUtils;
import com.localuz.service.MeService;
import com.localuz.service.dto.ChangePasswordDTO;
import com.localuz.service.dto.MeResponseDTO;
import com.localuz.service.dto.UpdatePersonalDTO;
import com.localuz.service.dto.UpdateTaxDataDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeResource {

    private static final String ENTITY_NAME = "me";

    private final MeService meService;

    public MeResource(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponseDTO> getMe() {
        return ResponseEntity.ok(meService.getMe(getCurrentUserLogin()));
    }

    @PatchMapping("/me")
    public ResponseEntity<MeResponseDTO> updatePersonal(@Valid @RequestBody UpdatePersonalDTO updatePersonalDTO) {
        return ResponseEntity.ok(meService.updatePersonal(getCurrentUserLogin(), updatePersonalDTO));
    }

    @PatchMapping("/me/tax-data")
    public ResponseEntity<MeResponseDTO> updateTaxData(@Valid @RequestBody UpdateTaxDataDTO updateTaxDataDTO) {
        return ResponseEntity.ok(meService.updateTaxData(getCurrentUserLogin(), updateTaxDataDTO));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        meService.changePassword(getCurrentUserLogin(), changePasswordDTO);
        return ResponseEntity.ok(Collections.singletonMap("message", "Senha alterada com sucesso"));
    }

    private String getCurrentUserLogin() {
        return SecurityUtils
            .getCurrentUserLogin()
            .orElseThrow(() -> new BadRequestAlertException("Usuario autenticado nao encontrado", ENTITY_NAME, "usernotfound"));
    }
}
