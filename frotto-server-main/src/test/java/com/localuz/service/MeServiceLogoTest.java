package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localuz.domain.User;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.MeResponseDTO;
import com.localuz.service.mapper.MeMapper;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Company logo (Configurações > Meu cadastro): stored separately from the avatar, only for the caller. */
class MeServiceLogoTest {

    private UserRepository userRepository;
    private AWSS3FileService s3;
    private MeService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        s3 = mock(AWSS3FileService.class);
        service = new MeService(userRepository, mock(PasswordEncoder.class), new MeMapper(), s3);
        user = new User();
        user.setId(7L);
        user.setLogin("owner@frotto.test");
        user.setImageUrl("avatar.png");
        when(userRepository.findOneByLogin("owner@frotto.test")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void uploadStoresTheLogoForTheCallerWithoutTouchingTheAvatar() {
        when(s3.uploadFile(any(), anyString())).thenReturn("https://bucket.test/LOGO_7_logo.png");

        MeResponseDTO result = service.uploadLogo("owner@frotto.test", new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2}));

        assertThat(result.getLogoUrl()).isEqualTo("https://bucket.test/LOGO_7_logo.png");
        assertThat(result.getImageUrl()).isEqualTo("avatar.png");
        assertThat(user.getLogoUrl()).isEqualTo("https://bucket.test/LOGO_7_logo.png");
        verify(s3).uploadFile(any(), org.mockito.ArgumentMatchers.eq("LOGO_7"));
    }

    @Test
    void emptyOrNonImageUploadsAreRejectedBeforeReachingStorage() {
        assertThatThrownBy(() -> service.uploadLogo("owner@frotto.test", new MockMultipartFile("file", "logo.png", "image/png", new byte[0])))
            .isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.uploadLogo("owner@frotto.test", new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1})))
            .isInstanceOf(BadRequestAlertException.class);
        verify(s3, never()).uploadFile(any(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void failedStorageUploadNeverClearsOrReplacesTheCurrentLogo() {
        user.setLogoUrl("old.png");
        when(s3.uploadFile(any(), anyString())).thenReturn("");

        assertThatThrownBy(() -> service.uploadLogo("owner@frotto.test", new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1})))
            .isInstanceOf(BadRequestAlertException.class);
        assertThat(user.getLogoUrl()).isEqualTo("old.png");
    }

    @Test
    void removeDeletesTheStoredFileAndClearsTheLogoEvenIfStorageCleanupFails() {
        user.setLogoUrl("https://bucket.test/LOGO_7_logo.png");
        doThrow(new RuntimeException("s3 down")).when(s3).deleteFile("LOGO_7_logo.png");

        MeResponseDTO result = service.removeLogo("owner@frotto.test");

        assertThat(result.getLogoUrl()).isNull();
        assertThat(user.getLogoUrl()).isNull();
        assertThat(user.getImageUrl()).isEqualTo("avatar.png");
        verify(s3).deleteFile("LOGO_7_logo.png");
    }
}
