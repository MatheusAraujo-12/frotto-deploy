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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
public class MeResource {

    private static final String ENTITY_NAME = "me";

    private final MeService meService;

    public MeResource(MeService meService) {
        this.meService = meService;
    }

    @GetMapping
    public ResponseEntity<MeResponseDTO> getMe() {
        return ResponseEntity.ok(meService.getMe(getCurrentUserLogin()));
    }

    @PatchMapping
    public ResponseEntity<MeResponseDTO> updatePersonal(@Valid @RequestBody UpdatePersonalDTO updatePersonalDTO) {
        return ResponseEntity.ok(meService.updatePersonal(getCurrentUserLogin(), updatePersonalDTO));
    }

    @PatchMapping("/tax-data")
    public ResponseEntity<MeResponseDTO> updateTaxData(@Valid @RequestBody UpdateTaxDataDTO updateTaxDataDTO) {
        return ResponseEntity.ok(meService.updateTaxData(getCurrentUserLogin(), updateTaxDataDTO));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        meService.changePassword(getCurrentUserLogin(), changePasswordDTO);
        return ResponseEntity.ok(Collections.singletonMap("message", "Senha alterada com sucesso"));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MeResponseDTO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(meService.uploadAvatar(getCurrentUserLogin(), file));
    }

    @PatchMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MeResponseDTO> patchAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(meService.uploadAvatar(getCurrentUserLogin(), file));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<MeResponseDTO> removeAvatar() {
        return ResponseEntity.ok(meService.removeAvatar(getCurrentUserLogin()));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MeResponseDTO> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(meService.uploadLogo(getCurrentUserLogin(), file));
    }

    @PatchMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MeResponseDTO> patchLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(meService.uploadLogo(getCurrentUserLogin(), file));
    }

    @DeleteMapping("/logo")
    public ResponseEntity<MeResponseDTO> removeLogo() {
        return ResponseEntity.ok(meService.removeLogo(getCurrentUserLogin()));
    }

    private String getCurrentUserLogin() {
        return SecurityUtils
            .getCurrentUserLogin()
            .orElseThrow(() -> new BadRequestAlertException("Usuario autenticado nao encontrado", ENTITY_NAME, "usernotfound"));
    }
}
