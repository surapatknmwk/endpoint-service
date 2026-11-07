package com.core.service.team;

import com.authen.entity.Role;
import com.authen.entity.User;
import com.authen.event.UserLoggedInEvent;
import com.authen.repository.UserRepository;
import com.core.entity.Notification;
import com.core.entity.TeamMember;
import com.core.entity.UserDevice;
import com.core.repository.TeamMemberRepository;
import com.core.repository.UserDeviceRepository;
import com.core.service.notification.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** New-device alert for drivers (driver-risk ข้อ 15). */
@ExtendWith(MockitoExtension.class)
class DriverDeviceWatcherTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 9, 0);
    private static final String DEVICE = "3f2b8c1e-aaaa-bbbb";
    private static final String IPHONE_LINE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile Line/13.20.0";

    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationPublisher notificationPublisher;

    private DriverDeviceWatcher watcher() {
        return new DriverDeviceWatcher(userDeviceRepository, teamMemberRepository, userRepository,
                notificationPublisher, Clock.fixed(NOW.atZone(BANGKOK).toInstant(), BANGKOK));
    }

    private static User user(int id, String username, String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private void givenNewDevice(int userId, long otherRecentDevices) {
        when(userDeviceRepository.findByUserIdAndDeviceId(userId, DEVICE)).thenReturn(Optional.empty());
        when(userDeviceRepository.countByUserIdAndDeviceIdNotAndLastSeenAtAfter(userId, DEVICE, NOW.minusDays(30)))
                .thenReturn(otherRecentDevices);
        when(userDeviceRepository.save(any())).thenAnswer(inv -> {
            UserDevice device = inv.getArgument(0);
            device.setUserDeviceId(77L);
            return device;
        });
    }

    @Test
    void should_rememberDeviceAndTellEveryActiveOwner_when_driverLogsInFromNewDevice() {
        //GIVEN — driver in 2 teams, used another device within 30 days
        User driver = user(3, "driver1", "DRIVER");
        givenNewDevice(3, 1);
        when(teamMemberRepository.findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(3, "ACTIVE")).thenReturn(List.of(
                TeamMember.builder().ownerUserId(1).memberUserId(3).build(),
                TeamMember.builder().ownerUserId(2).memberUserId(3).build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "admin", "USER_VIP_0")));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, "owner2", "USER_VIP_0")));

        //WHEN
        watcher().onLogin(new UserLoggedInEvent(driver, DEVICE, IPHONE_LINE, "203.0.113.9"));

        //THEN
        ArgumentCaptor<UserDevice> saved = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(saved.capture());
        assertThat(saved.getValue().getDeviceLabel()).isEqualTo("iPhone · LINE");
        assertThat(saved.getValue().getFirstSeenAt()).isEqualTo(NOW);
        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPublisher, org.mockito.Mockito.times(2)).publish(sent.capture());
        assertThat(sent.getAllValues()).extracting(Notification::getRecipientUsername).containsExactly("admin", "owner2");
        assertThat(sent.getAllValues()).extracting(Notification::getDedupeKey).containsOnly("DRIVER_NEW_DEVICE:77");
        assertThat(sent.getAllValues().getFirst().getMessage()).contains("iPhone · LINE");
    }

    @Test
    void should_rememberWithoutAlert_when_driversFirstDevice() {
        //GIVEN — zero: no other device in 30 days
        givenNewDevice(3, 0);
        //WHEN
        watcher().onLogin(new UserLoggedInEvent(user(3, "driver1", "DRIVER"), DEVICE, IPHONE_LINE, null));
        //THEN
        verify(userDeviceRepository).save(any());
        verifyNoInteractions(notificationPublisher, teamMemberRepository);
    }

    @Test
    void should_rememberWithoutAlert_when_ownerLogsInFromNewDevice() {
        //GIVEN
        givenNewDevice(1, 3);
        //WHEN
        watcher().onLogin(new UserLoggedInEvent(user(1, "admin", "USER_VIP_0"), DEVICE, IPHONE_LINE, null));
        //THEN
        verifyNoInteractions(notificationPublisher, teamMemberRepository);
    }

    @Test
    void should_onlyRefreshLastSeen_when_deviceIsKnown() {
        //GIVEN
        UserDevice known = UserDevice.builder().userDeviceId(5L).userId(3).deviceId(DEVICE)
                .firstSeenAt(NOW.minusDays(9)).lastSeenAt(NOW.minusDays(1)).build();
        when(userDeviceRepository.findByUserIdAndDeviceId(3, DEVICE)).thenReturn(Optional.of(known));
        //WHEN
        watcher().onLogin(new UserLoggedInEvent(user(3, "driver1", "DRIVER"), DEVICE, IPHONE_LINE, "198.51.100.4"));
        //THEN
        assertThat(known.getLastSeenAt()).isEqualTo(NOW);
        assertThat(known.getLastIp()).isEqualTo("198.51.100.4");
        assertThat(known.getFirstSeenAt()).isEqualTo(NOW.minusDays(9));
        verify(userDeviceRepository, never()).countByUserIdAndDeviceIdNotAndLastSeenAtAfter(any(), any(), any());
        verifyNoInteractions(notificationPublisher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "has space inside!", "x' or 1=1 --"})
    void should_ignoreLogin_when_deviceIdIsMissingOrMalformed(String deviceId) {
        //WHEN — interface: old clients send nothing; anything odd is treated as no id
        watcher().onLogin(new UserLoggedInEvent(user(3, "driver1", "DRIVER"), deviceId, IPHONE_LINE, null));
        //THEN
        verifyNoInteractions(userDeviceRepository, notificationPublisher);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 | Android · Chrome",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) Version/17.4 Mobile/15E148 Safari/604.1 | iPhone · Safari",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0 Safari/537.36 Edg/124.0 | Windows · Edge",
            "curl/8.4.0 | เครื่องอื่น · เบราว์เซอร์อื่น"})
    void should_labelDeviceForPeople_when_readingUserAgent(String userAgent, String expected) {
        assertThat(DriverDeviceWatcher.label(userAgent)).isEqualTo(expected);
        assertThat(DriverDeviceWatcher.label(null)).isEqualTo("เครื่องที่ไม่รู้จัก");
    }
}
