package com.core.service.profile;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.DriverAvailability;
import com.core.dto.AvailabilityRequest;
import com.core.dto.ContactInfo;
import com.core.dto.ProfileResponse;
import com.core.dto.ProfileUpdateRequest;
import com.core.dto.master.AddressInfoDto;
import com.core.entity.UserProfile;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.UserProfileRepository;
import com.core.service.AreaSelectionGuard;
import com.core.service.clients.MasterDataService;
import com.master.repository.SubdistrictRepository;
import com.core.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The caller's own phone, home address and (driver) availability, plus the batch lookups the team and job
 * screens use to show them to the other side. Who may see someone else's data (same team, INVITED / ACTIVE)
 * is decided by the callers. Story: docs/user-stories/profile.md.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final String PHONE_INVALID = "เบอร์โทรต้องเป็นตัวเลข 10 หลัก ขึ้นต้นด้วย 0";
    static final String SUBDISTRICT_NOT_FOUND = "ไม่พบตำบลที่เลือก";
    static final String SUBDISTRICT_REQUIRED = "กรุณาเลือกตำบล";
    static final String AVAILABILITY_INVALID = "สถานะรับงานไม่ถูกต้อง";
    static final String DRIVER_ONLY = "ไม่มีสิทธิ์ทำรายการนี้";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DeliveryJobRepository deliveryJobRepository;
    private final MasterDataService masterDataService;
    private final Clock clock;
    private final SubdistrictRepository subdistrictRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(String username) {
        User me = currentUser(username);
        return toResponse(me, userProfileRepository.findById(me.getUserId()).orElse(null));
    }

    /**
     * Name and phone go to {@code users}, the address to {@code user_profiles}. Blank = cleared; a null subdistrict
     * clears the whole address. District and province always come from the subdistrict, never from the client.
     */
    @Transactional
    public ProfileResponse updateMyProfile(String username, ProfileUpdateRequest request) {
        User me = currentUser(username);
        String phone = normalizePhone(request.getPhone());
        String addressLine = blankToNull(request.getAddressLine());
        String subdistrictCode = blankToNull(request.getSubdistrictCode());
        if (addressLine != null && subdistrictCode == null) {
            throw new IllegalArgumentException(SUBDISTRICT_REQUIRED);
        }
        AddressInfoDto area = subdistrictCode == null ? null : masterDataService.findBySubdistrict(subdistrictCode)
                .orElseThrow(() -> new IllegalArgumentException(SUBDISTRICT_NOT_FOUND));

        Optional<UserProfile> existing = userProfileRepository.findById(me.getUserId());
        if (subdistrictCode != null) {
            // ที่อยู่ประจำเดิมที่ถูกปิดไปแล้วเก็บไว้ได้ — เลือกตำบลที่ปิดอยู่เป็นค่าใหม่ไม่ได้ (admin.md US-ADM-06)
            String current = existing.map(UserProfile::getSubdistrictCode).orElse(null);
            subdistrictRepository.findByCode(subdistrictCode)
                    .ifPresent(chosen -> AreaSelectionGuard.requireSelectable(chosen, current));
        }

        me.setFirstName(blankToNull(request.getFirstName()));
        me.setLastName(blankToNull(request.getLastName()));
        me.setPhone(phone);
        userRepository.save(me);

        UserProfile profile = existing.orElse(null);
        if (area != null || profile != null) {
            profile = profile != null ? profile : newProfile(me.getUserId());
            profile.setAddressLine(area == null ? null : addressLine);
            profile.setSubdistrictCode(area == null ? null : area.getSubdistrictCode());
            profile.setDistrictCode(area == null ? null : area.getDistrictCode());
            profile.setProvinceCode(area == null ? null : area.getProvinceCode());
            profile.setUpdatedAt(LocalDateTime.now(clock));
            profile.setUpdatedBy(me.getUsername());
            userProfileRepository.save(profile);
        }
        return toResponse(me, profile);
    }

    /** DRIVER only. Setting the current value again changes nothing. */
    @Transactional
    public ProfileResponse updateAvailability(String username, AvailabilityRequest request) {
        User me = currentUser(username);
        if (!TeamService.isDriver(me)) {
            throw new PermissionDeniedException(DRIVER_ONLY);
        }
        String status = request.getStatus() == null ? null : request.getStatus().trim().toUpperCase();
        if (!DriverAvailability.ALL.contains(status)) {
            throw new IllegalArgumentException(AVAILABILITY_INVALID);
        }
        UserProfile profile = userProfileRepository.findById(me.getUserId()).orElse(null);
        if (profile == null || !status.equals(profile.getAvailabilityStatus())) {
            LocalDateTime now = LocalDateTime.now(clock);
            profile = profile != null ? profile : newProfile(me.getUserId());
            profile.setAvailabilityStatus(status);
            profile.setAvailabilityUpdatedAt(now);
            profile.setUpdatedAt(now);
            profile.setUpdatedBy(me.getUsername());
            userProfileRepository.save(profile);
        }
        return toResponse(me, profile);
    }

    // ---- batch lookups for team / job screens (callers apply the same-team rule) ----

    /** Phone and one-line address per user id. Every given user is in the map. */
    @Transactional(readOnly = true)
    public Map<Integer, ContactInfo> contactsOf(Collection<User> users) {
        List<User> known = users.stream().filter(Objects::nonNull).toList();
        if (known.isEmpty()) {
            return Map.of();
        }
        Map<Integer, UserProfile> profiles = profilesById(known.stream().map(User::getUserId).toList());
        Map<String, AddressInfoDto> areas = areasOf(profiles.values());
        Map<Integer, ContactInfo> contacts = new HashMap<>();
        for (User user : known) {
            UserProfile profile = profiles.get(user.getUserId());
            // areas may be Map.of(), which throws on a null key — profile without an address
            AddressInfoDto area = profile == null || profile.getSubdistrictCode() == null
                    ? null : areas.get(profile.getSubdistrictCode());
            contacts.put(user.getUserId(), new ContactInfo(user.getPhone(), addressText(profile, area)));
        }
        return contacts;
    }

    /** Availability per driver id; no profile row = AVAILABLE. */
    @Transactional(readOnly = true)
    public Map<Integer, String> availabilityOf(Collection<Integer> driverUserIds) {
        Map<Integer, UserProfile> profiles = profilesById(driverUserIds);
        return driverUserIds.stream().distinct().collect(Collectors.toMap(Function.identity(),
                id -> Optional.ofNullable(profiles.get(id)).map(UserProfile::getAvailabilityStatus)
                        .orElse(DriverAvailability.AVAILABLE)));
    }

    /** Of these drivers, the ones with a started job in a team they are still ACTIVE in ("กำลังส่งงาน"). */
    @Transactional(readOnly = true)
    public Set<Integer> deliveringOf(Collection<Integer> driverUserIds) {
        if (driverUserIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(deliveryJobRepository.findDeliveringDrivers(driverUserIds));
    }

    /**
     * {@code 45 ถ.สุขเกษม ต.ธาตุเชิงชุม อ.เมืองสกลนคร จ.สกลนคร} — null without a subdistrict. Missing names (unknown
     * code) are left out.
     */
    static String addressText(UserProfile profile, AddressInfoDto area) {
        if (profile == null || profile.getSubdistrictCode() == null) {
            return null;
        }
        String text = Stream.of(
                        blankToNull(profile.getAddressLine()),
                        prefixed("ต.", area == null ? null : area.getSubdistrictName()),
                        prefixed("อ.", area == null ? null : area.getDistrictName()),
                        prefixed("จ.", area == null ? null : area.getProvinceName()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? null : text;
    }

    /** Removes dashes and spaces; the rest must be 10 digits starting with 0. Blank = null. */
    static String normalizePhone(String raw) {
        String phone = blankToNull(raw == null ? null : raw.replaceAll("[\\s-]", ""));
        if (phone != null && !phone.matches("0\\d{9}")) {
            throw new IllegalArgumentException(PHONE_INVALID);
        }
        return phone;
    }

    // ---- helpers ----

    private ProfileResponse toResponse(User me, UserProfile profile) {
        boolean driver = TeamService.isDriver(me);
        ProfileResponse.Address address = null;
        if (profile != null && profile.getSubdistrictCode() != null) {
            AddressInfoDto area = masterDataService.findBySubdistrict(profile.getSubdistrictCode()).orElse(null);
            address = ProfileResponse.Address.builder()
                    .line(profile.getAddressLine())
                    .subdistrictCode(profile.getSubdistrictCode())
                    .districtCode(profile.getDistrictCode())
                    .provinceCode(profile.getProvinceCode())
                    .subdistrictName(area == null ? null : area.getSubdistrictName())
                    .districtName(area == null ? null : area.getDistrictName())
                    .provinceName(area == null ? null : area.getProvinceName())
                    .build();
        }
        return ProfileResponse.builder()
                .username(me.getUsername())
                .firstName(me.getFirstName())
                .lastName(me.getLastName())
                .phone(me.getPhone())
                .pictureUrl(me.getPictureUrl())
                .address(address)
                .availability(driver ? Optional.ofNullable(profile).map(UserProfile::getAvailabilityStatus)
                        .orElse(DriverAvailability.AVAILABLE) : null)
                .delivering(driver && deliveringOf(List.of(me.getUserId())).contains(me.getUserId()))
                .build();
    }

    private Map<Integer, UserProfile> profilesById(Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
    }

    private Map<String, AddressInfoDto> areasOf(Collection<UserProfile> profiles) {
        Set<String> codes = profiles.stream().map(UserProfile::getSubdistrictCode).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return codes.isEmpty() ? Map.of() : masterDataService.findBySubdistricts(codes);
    }

    private static UserProfile newProfile(Integer userId) {
        return UserProfile.builder().userId(userId).availabilityStatus(DriverAvailability.AVAILABLE).build();
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private static String prefixed(String prefix, String name) {
        return StringUtils.hasText(name) ? prefix + name.trim() : null;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
