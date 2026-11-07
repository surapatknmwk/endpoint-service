package com.core.service.profile;

import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.DriverAvailability;
import com.core.dto.AvailabilityRequest;
import com.core.dto.ProfileResponse;
import com.core.dto.ProfileUpdateRequest;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.UserProfile;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.UserProfileRepository;
import com.core.service.clients.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ProfileService} (docs/user-stories/profile.md US-PROF-01…03).
 * <ul>
 *   <li>WHAT — the caller's phone / address are validated and stored; availability is for DRIVER accounts only;
 *   the one-line address text is built from the codes.</li>
 *   <li>WHEN — valid / malformed phone, known / unknown subdistrict, address line without subdistrict, clearing;
 *   owner vs driver; same status twice; no profile row yet.</li>
 *   <li>THEN — the values returned and what is (not) saved; the exception and its message.</li>
 * </ul>
 * Z: no profile row · O: one field · I: blank / malformed input · E: unknown subdistrict, owner on driver endpoint ·
 * S: driver saves a full address. Many / boundary are list concerns covered in TeamServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Bangkok")).toInstant(),
            ZoneId.of("Asia/Bangkok"));
    private static final AddressInfoDto THATCHOENGCHUM = AddressInfoDto.builder()
            .subdistrictCode("470101").subdistrictName("ธาตุเชิงชุม")
            .districtCode("4701").districtName("เมืองสกลนคร")
            .provinceCode("47").provinceName("สกลนคร").build();

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private DeliveryJobRepository deliveryJobRepository;
    @Mock
    private MasterDataService masterDataService;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userRepository, userProfileRepository, deliveryJobRepository,
                masterDataService, CLOCK);
        lenient().when(masterDataService.findBySubdistrict("470101")).thenReturn(Optional.of(THATCHOENGCHUM));
    }

    private User loggedIn(int id, String username, String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setRole(role);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private static ProfileUpdateRequest request(String phone, String line, String subdistrictCode) {
        return ProfileUpdateRequest.builder().firstName("ประยุทธ").lastName("ขับดี").phone(phone)
                .addressLine(line).subdistrictCode(subdistrictCode).build();
    }

    // ---- read ----

    @Test
    void should_answerAvailableAndNoAddress_when_driverHasNoProfileRow() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        when(userProfileRepository.findById(3)).thenReturn(Optional.empty());
        when(deliveryJobRepository.findDeliveringDrivers(List.of(3))).thenReturn(List.of());
        //WHEN
        ProfileResponse result = profileService.getMyProfile("driver1");
        //THEN
        assertThat(result.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(result.getAddress()).isNull();
        assertThat(result.isDelivering()).isFalse();
    }

    @Test
    void should_answerNoAvailabilityNorDelivering_when_ownerReadsProfile() {
        //GIVEN
        loggedIn(1, "admin", "USER_VIP_0");
        when(userProfileRepository.findById(1)).thenReturn(Optional.empty());
        //WHEN
        ProfileResponse result = profileService.getMyProfile("admin");
        //THEN
        assertThat(result.getAvailability()).isNull();
        assertThat(result.isDelivering()).isFalse();
        verify(deliveryJobRepository, never()).findDeliveringDrivers(any());
    }

    // ---- update contact ----

    @Test
    void should_storeDigitsAndDeriveDistrictProvince_when_driverSavesFullAddress() {
        //GIVEN
        User me = loggedIn(3, "driver1", "DRIVER");
        when(userProfileRepository.findById(3)).thenReturn(Optional.empty());
        //WHEN
        ProfileResponse result = profileService.updateMyProfile("driver1",
                request("089-100 0001", "  45 ถ.สุขเกษม ", "470101"));
        //THEN
        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(saved.capture());
        assertThat(me.getPhone()).isEqualTo("0891000001");
        assertThat(saved.getValue().getAddressLine()).isEqualTo("45 ถ.สุขเกษม");
        assertThat(saved.getValue().getDistrictCode()).isEqualTo("4701");
        assertThat(saved.getValue().getProvinceCode()).isEqualTo("47");
        assertThat(saved.getValue().getAvailabilityStatus()).isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(result.getAddress().getSubdistrictName()).isEqualTo("ธาตุเชิงชุม");
    }

    @Test
    void should_rejectWithoutSaving_when_phoneIsNotTenDigitsStartingWithZero() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        //WHEN / THEN
        assertThatThrownBy(() -> profileService.updateMyProfile("driver1", request("1891000001", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ProfileService.PHONE_INVALID);
        assertThatThrownBy(() -> profileService.updateMyProfile("driver1", request("08910000", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ProfileService.PHONE_INVALID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_reject_when_subdistrictDoesNotExist() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        when(masterDataService.findBySubdistrict("999999")).thenReturn(Optional.empty());
        //WHEN / THEN
        assertThatThrownBy(() -> profileService.updateMyProfile("driver1", request(null, null, "999999")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ProfileService.SUBDISTRICT_NOT_FOUND);
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_reject_when_addressLineHasNoSubdistrict() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        //WHEN / THEN
        assertThatThrownBy(() -> profileService.updateMyProfile("driver1", request(null, "45 ถ.สุขเกษม", " ")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ProfileService.SUBDISTRICT_REQUIRED);
    }

    @Test
    void should_clearWholeAddressButKeepAvailability_when_subdistrictIsNull() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        UserProfile existing = UserProfile.builder().userId(3).addressLine("45 ถ.สุขเกษม").subdistrictCode("470101")
                .districtCode("4701").provinceCode("47").availabilityStatus(DriverAvailability.BUSY).build();
        when(userProfileRepository.findById(3)).thenReturn(Optional.of(existing));
        //WHEN
        ProfileResponse result = profileService.updateMyProfile("driver1", request("", null, null));
        //THEN
        assertThat(existing.getAddressLine()).isNull();
        assertThat(existing.getSubdistrictCode()).isNull();
        assertThat(existing.getProvinceCode()).isNull();
        assertThat(existing.getAvailabilityStatus()).isEqualTo(DriverAvailability.BUSY);
        assertThat(result.getAddress()).isNull();
        assertThat(result.getPhone()).isNull();
    }

    @Test
    void should_notCreateProfileRow_when_ownerSavesNameOnlyWithoutAddress() {
        //GIVEN
        loggedIn(1, "admin", "USER_VIP_0");
        when(userProfileRepository.findById(1)).thenReturn(Optional.empty());
        //WHEN
        profileService.updateMyProfile("admin", request("0812000001", null, null));
        //THEN
        verify(userRepository).save(any());
        verify(userProfileRepository, never()).save(any());
    }

    // ---- availability ----

    @Test
    void should_refuseWith403_when_ownerSetsAvailability() {
        //GIVEN
        loggedIn(1, "admin", "USER_VIP_0");
        //WHEN / THEN
        assertThatThrownBy(() -> profileService.updateAvailability("admin", new AvailabilityRequest("BUSY")))
                .isInstanceOf(PermissionDeniedException.class);
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void should_reject_when_statusIsNotOneOfThree() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        //WHEN / THEN
        assertThatThrownBy(() -> profileService.updateAvailability("driver1", new AvailabilityRequest("SLEEPING")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ProfileService.AVAILABILITY_INVALID);
    }

    @Test
    void should_createRowWithStatusAndTime_when_driverSetsAvailabilityFirstTime() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        when(userProfileRepository.findById(3)).thenReturn(Optional.empty());
        when(deliveryJobRepository.findDeliveringDrivers(List.of(3))).thenReturn(List.of(3));
        //WHEN
        ProfileResponse result = profileService.updateAvailability("driver1", new AvailabilityRequest("off"));
        //THEN
        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(saved.capture());
        assertThat(saved.getValue().getAvailabilityStatus()).isEqualTo(DriverAvailability.OFF);
        assertThat(saved.getValue().getAvailabilityUpdatedAt()).isEqualTo(NOW);
        assertThat(result.getAvailability()).isEqualTo(DriverAvailability.OFF);
        assertThat(result.isDelivering()).isTrue();
    }

    @Test
    void should_changeNothing_when_sameStatusIsSetAgain() {
        //GIVEN
        loggedIn(3, "driver1", "DRIVER");
        LocalDateTime earlier = NOW.minusDays(1);
        UserProfile existing = UserProfile.builder().userId(3).availabilityStatus(DriverAvailability.BUSY)
                .availabilityUpdatedAt(earlier).build();
        when(userProfileRepository.findById(3)).thenReturn(Optional.of(existing));
        //WHEN
        profileService.updateAvailability("driver1", new AvailabilityRequest("BUSY"));
        //THEN
        verify(userProfileRepository, never()).save(any());
        assertThat(existing.getAvailabilityUpdatedAt()).isEqualTo(earlier);
    }

    // ---- address text ----

    @Test
    void should_buildOneLineWithThaiPrefixes_when_profileHasLineAndArea() {
        //GIVEN
        UserProfile profile = UserProfile.builder().addressLine("45 ถ.สุขเกษม").subdistrictCode("470101").build();
        //WHEN
        String text = ProfileService.addressText(profile, THATCHOENGCHUM);
        //THEN
        assertThat(text).isEqualTo("45 ถ.สุขเกษม ต.ธาตุเชิงชุม อ.เมืองสกลนคร จ.สกลนคร");
    }

    @Test
    void should_returnNull_when_noSubdistrict() {
        //GIVEN
        UserProfile profile = UserProfile.builder().availabilityStatus(DriverAvailability.OFF).build();
        //WHEN
        String text = ProfileService.addressText(profile, null);
        //THEN
        assertThat(text).isNull();
    }
}
