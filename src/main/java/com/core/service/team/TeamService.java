package com.core.service.team;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.common.exception.PermissionDeniedException;
import com.core.constant.AccountRole;
import com.core.constant.TeamMemberStatus;
import com.core.constant.UserSettingKey;
import com.core.dto.TeamMemberResponse;
import com.core.dto.TeamMembershipResponse;
import com.core.dto.TeamSettingsDto;
import com.core.dto.ContactInfo;
import com.core.dto.OpenJobSummary;
import com.core.dto.TeamUserSummary;
import com.core.entity.TeamMember;
import com.core.entity.UserSetting;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.JobDriverRequestRepository;
import com.core.repository.TeamMemberRepository;
import com.core.repository.UserSettingRepository;
import com.core.service.notification.TeamNotifier;
import com.core.service.profile.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An owner invites drivers into their team; a driver answers, and can later leave. Design:
 * docs/task/enhanment/roleAndPermission/design.md §5.1. Every rule is checked against the database on each
 * request, never against the token, so a removal takes effect at once.
 *
 * <p>Rows of someone else are reported exactly like missing ones, so ids cannot be probed.</p>
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    static final String CANNOT_INVITE = "ไม่สามารถเชิญผู้ใช้นี้ได้";
    static final String ALREADY_IN_TEAM = "ผู้ใช้นี้อยู่ในทีมหรือถูกเชิญแล้ว";
    static final String INVITATION_NOT_FOUND = "ไม่พบคำเชิญ";
    static final String INVITATION_CLOSED = "คำเชิญนี้ไม่สามารถตอบได้แล้ว";
    static final String MEMBER_NOT_FOUND = "ไม่พบสมาชิก";
    static final String MEMBER_ALREADY_GONE = "สมาชิกนี้ไม่ได้อยู่ในทีมแล้ว";
    static final String MEMBER_HAS_OPEN_JOBS = "สมาชิกยังมีงานที่ยังไม่ปิด";
    static final String TEAM_NOT_FOUND = "ไม่พบทีม";
    static final String TEAM_HAS_OPEN_JOBS = "ยังมีงานของทีมนี้ที่ยังไม่ปิด";
    static final String OWNER_ONLY = "ไม่มีสิทธิ์ทำรายการนี้";

    private final TeamMemberRepository teamMemberRepository;
    private final UserSettingRepository userSettingRepository;
    private final DeliveryJobRepository deliveryJobRepository;
    private final UserRepository userRepository;
    private final TeamNotifier teamNotifier;
    private final ProfileService profileService;
    private final JobDriverRequestRepository jobDriverRequestRepository;
    private final Clock clock;

    // ---- owner ----

    /**
     * My INVITED and ACTIVE rows, oldest first, each with my open jobs assigned to that driver (count + short list,
     * team.md US-TEAM-03) and the driver's contact, availability, delivering flag and last seen
     * (profile.md US-PROF-04) — one query per kind.
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(String username) {
        User owner = requireOwner(username);
        List<TeamMember> rows = teamMemberRepository.findByOwnerUserIdAndMemberStatusInOrderByCreatedAtAsc(
                owner.getUserId(), TeamMemberStatus.OPEN);
        List<Integer> memberIds = rows.stream().map(TeamMember::getMemberUserId).toList();
        Map<Integer, User> members = usersById(memberIds);
        Map<Integer, List<OpenJobSummary>> openJobs = openJobsByDriver(
                deliveryJobRepository.findOpenAssignedByOwner(owner.getUsername()));
        Map<Integer, Long> pendingRequests = jobDriverRequestRepository.countPendingByDriver(owner.getUserId()).stream()
                .collect(Collectors.toMap(JobDriverRequestRepository.PendingCountByDriver::getDriverUserId,
                        JobDriverRequestRepository.PendingCountByDriver::getPendingCount));
        DriverInfo info = driverInfo(members.values());
        return rows.stream()
                .map(row -> {
                    List<OpenJobSummary> jobs = openJobs.getOrDefault(row.getMemberUserId(), List.of());
                    TeamMemberResponse response = toMemberResponse(row, members.get(row.getMemberUserId()),
                            jobs.size());
                    response.setOpenJobs(jobs);
                    info.fill(response, row, members.get(row.getMemberUserId()));
                    response.setPendingRequestCount(pendingRequests.getOrDefault(row.getMemberUserId(), 0L));
                    return response;
                })
                .toList();
    }

    /**
     * Invites an active DRIVER account. Not found / not a driver / inactive / self all answer the same message,
     * so the form cannot be used to discover usernames or account types.
     */
    @Transactional
    public TeamMemberResponse invite(String username, String inviteeUsername) {
        User owner = requireOwner(username);
        User invitee = findInvitee(inviteeUsername)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(TeamService::isDriver)
                .filter(u -> !u.getUserId().equals(owner.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException(CANNOT_INVITE));

        int created = teamMemberRepository.insertInvitation(
                owner.getUserId(), invitee.getUserId(), LocalDateTime.now(clock), owner.getUsername());
        if (created == 0) {
            throw new IllegalArgumentException(ALREADY_IN_TEAM);
        }
        TeamMember row = teamMemberRepository.findFirstByOwnerUserIdAndMemberUserIdAndMemberStatus(
                        owner.getUserId(), invitee.getUserId(), TeamMemberStatus.INVITED)
                .orElseThrow(() -> new IllegalStateException("Invitation not found right after insert"));
        teamNotifier.invited(row, owner, invitee);
        TeamMemberResponse response = toMemberResponse(row, invitee, 0L);
        driverInfo(List.of(invitee)).fill(response, row, invitee);
        return response;
    }

    /**
     * INVITED → CANCELLED (withdraw the invitation) or ACTIVE → REMOVED. Until the reassignment flow is designed
     * (design §5.4), a member with open assigned jobs cannot be removed.
     */
    @Transactional
    public TeamMemberResponse removeMember(String username, Long teamMemberId) {
        User owner = requireOwner(username);
        TeamMember row = teamMemberRepository.findForUpdate(teamMemberId)
                .filter(r -> r.getOwnerUserId().equals(owner.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException(MEMBER_NOT_FOUND));
        User member = userRepository.findById(row.getMemberUserId()).orElse(null);
        switch (row.getMemberStatus()) {
            case TeamMemberStatus.INVITED -> end(row, TeamMemberStatus.CANCELLED, owner.getUsername());
            case TeamMemberStatus.ACTIVE -> {
                if (deliveryJobRepository.countOpenAssigned(owner.getUsername(), row.getMemberUserId()) > 0) {
                    throw new IllegalArgumentException(MEMBER_HAS_OPEN_JOBS);
                }
                end(row, TeamMemberStatus.REMOVED, owner.getUsername());
                // คำขอรับงานที่ยังไม่ตอบ ไม่กันการนำออก แต่ถูกยกเลิกไปด้วย (design assignConfirm G5)
                jobDriverRequestRepository.cancelPendingOfPair(owner.getUserId(), row.getMemberUserId(),
                        LocalDateTime.now(clock), owner.getUsername());
                teamNotifier.removed(row, owner, member);
            }
            default -> throw new IllegalArgumentException(MEMBER_ALREADY_GONE);
        }
        return toMemberResponse(row, member, 0L);
    }

    @Transactional(readOnly = true)
    public TeamSettingsDto getSettings(String username) {
        User owner = requireOwner(username);
        return new TeamSettingsDto(isShowPriceToDriver(owner.getUserId()));
    }

    @Transactional
    public TeamSettingsDto updateSettings(String username, TeamSettingsDto request) {
        User owner = requireOwner(username);
        boolean showPrice = Boolean.TRUE.equals(request.getShowPriceToDriver());
        userSettingRepository.save(UserSetting.builder()
                .id(new UserSetting.Key(owner.getUserId(), UserSettingKey.DRIVER_SHOW_PRICE))
                .settingValue(String.valueOf(showPrice))
                .updatedAt(LocalDateTime.now(clock))
                .build());
        return new TeamSettingsDto(showPrice);
    }

    /** Whether this owner lets drivers see prices. No row = false. */
    @Transactional(readOnly = true)
    public boolean isShowPriceToDriver(Integer ownerUserId) {
        return userSettingRepository.findById(new UserSetting.Key(ownerUserId, UserSettingKey.DRIVER_SHOW_PRICE))
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(false);
    }

    // ---- driver ----

    /** Invitations addressed to me that I have not answered, newest first. */
    @Transactional(readOnly = true)
    public List<TeamMembershipResponse> listInvitations(String username) {
        User me = currentUser(username);
        List<TeamMember> rows = teamMemberRepository.findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(
                me.getUserId(), TeamMemberStatus.INVITED);
        Map<Integer, User> owners = usersById(rows.stream().map(TeamMember::getOwnerUserId).toList());
        Map<Integer, ContactInfo> contacts = profileService.contactsOf(owners.values());
        return rows.stream()
                .map(row -> withOwnerContact(toMembershipResponse(row, owners.get(row.getOwnerUserId()), 0L), row,
                        contacts))
                .toList();
    }

    /** INVITED → ACTIVE. */
    @Transactional
    public TeamMembershipResponse acceptInvitation(String username, Long teamMemberId) {
        return answerInvitation(username, teamMemberId, true);
    }

    /** INVITED → DECLINED. */
    @Transactional
    public TeamMembershipResponse declineInvitation(String username, Long teamMemberId) {
        return answerInvitation(username, teamMemberId, false);
    }

    /** Teams I am an ACTIVE member of, newest first, each with my open jobs from that owner. */
    @Transactional(readOnly = true)
    public List<TeamMembershipResponse> listMemberships(String username) {
        User me = currentUser(username);
        List<TeamMember> rows = teamMemberRepository.findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(
                me.getUserId(), TeamMemberStatus.ACTIVE);
        Map<Integer, User> owners = usersById(rows.stream().map(TeamMember::getOwnerUserId).toList());
        Map<String, Long> openJobs = deliveryJobRepository.countOpenAssignedByOwner(me.getUserId()).stream()
                .collect(Collectors.toMap(DeliveryJobRepository.OpenJobCountByOwner::getOwnerUsername,
                        DeliveryJobRepository.OpenJobCountByOwner::getOpenJobCount));
        Map<Integer, ContactInfo> contacts = profileService.contactsOf(owners.values());
        return rows.stream()
                .map(row -> {
                    User owner = owners.get(row.getOwnerUserId());
                    long count = owner == null ? 0L : openJobs.getOrDefault(owner.getUsername(), 0L);
                    return withOwnerContact(toMembershipResponse(row, owner, count), row, contacts);
                })
                .toList();
    }

    /** ACTIVE → LEFT. Same temporary rule as {@link #removeMember}: not while I have open jobs of that owner. */
    @Transactional
    public TeamMembershipResponse leaveTeam(String username, Long teamMemberId) {
        User me = currentUser(username);
        TeamMember row = teamMemberRepository.findForUpdate(teamMemberId)
                .filter(r -> r.getMemberUserId().equals(me.getUserId()))
                .filter(r -> TeamMemberStatus.ACTIVE.equals(r.getMemberStatus()))
                .orElseThrow(() -> new IllegalArgumentException(TEAM_NOT_FOUND));
        User owner = userRepository.findById(row.getOwnerUserId()).orElse(null);
        if (owner != null && deliveryJobRepository.countOpenAssigned(owner.getUsername(), me.getUserId()) > 0) {
            throw new IllegalArgumentException(TEAM_HAS_OPEN_JOBS);
        }
        end(row, TeamMemberStatus.LEFT, me.getUsername());
        jobDriverRequestRepository.cancelPendingOfPair(row.getOwnerUserId(), me.getUserId(), LocalDateTime.now(clock),
                me.getUsername());
        teamNotifier.left(row, owner, me);
        return toMembershipResponse(row, owner, 0L);
    }

    // ---- helpers ----

    /** Contact, availability, delivering and last seen of drivers, read once for a whole list. */
    private DriverInfo driverInfo(Collection<User> drivers) {
        List<Integer> ids = drivers.stream().map(User::getUserId).toList();
        return new DriverInfo(profileService.contactsOf(drivers), profileService.availabilityOf(ids),
                profileService.deliveringOf(ids));
    }

    private record DriverInfo(Map<Integer, ContactInfo> contacts, Map<Integer, String> availability,
                              Set<Integer> delivering) {

        /** Same-team rule (profile.md E2): only an open row shows the driver's data. */
        void fill(TeamMemberResponse response, TeamMember row, User driver) {
            if (driver == null || !isOpen(row)) {
                return;
            }
            response.setContact(contacts.get(driver.getUserId()));
            response.setAvailability(availability.get(driver.getUserId()));
            response.setDelivering(delivering.contains(driver.getUserId()));
            response.setLastSeenAt(driver.getLastSeenAt());
        }
    }

    private static TeamMembershipResponse withOwnerContact(TeamMembershipResponse response, TeamMember row,
                                                           Map<Integer, ContactInfo> contacts) {
        if (isOpen(row)) {
            response.setContact(contacts.get(row.getOwnerUserId()));
        }
        return response;
    }

    private static boolean isOpen(TeamMember row) {
        return TeamMemberStatus.OPEN.contains(row.getMemberStatus());
    }

    private TeamMembershipResponse answerInvitation(String username, Long teamMemberId, boolean accept) {
        User me = currentUser(username);
        TeamMember row = teamMemberRepository.findForUpdate(teamMemberId)
                .filter(r -> r.getMemberUserId().equals(me.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException(INVITATION_NOT_FOUND));
        if (!TeamMemberStatus.INVITED.equals(row.getMemberStatus())) {
            throw new IllegalArgumentException(INVITATION_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        row.setRespondedAt(now);
        if (accept) {
            row.setMemberStatus(TeamMemberStatus.ACTIVE);
            touch(row, me.getUsername(), now);
        } else {
            end(row, TeamMemberStatus.DECLINED, me.getUsername());
        }
        User owner = userRepository.findById(row.getOwnerUserId()).orElse(null);
        teamNotifier.answered(row, owner, me, accept);
        return toMembershipResponse(row, owner, 0L);
    }

    private void end(TeamMember row, String finalStatus, String by) {
        LocalDateTime now = LocalDateTime.now(clock);
        row.setMemberStatus(finalStatus);
        row.setEndedAt(now);
        touch(row, by, now);
    }

    private static void touch(TeamMember row, String by, LocalDateTime now) {
        row.setUpdatedAt(now);
        row.setUpdatedBy(by);
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    /** Owner endpoints are already closed to DRIVER by the permission table; this guards the data as well. */
    private User requireOwner(String username) {
        User user = currentUser(username);
        if (isDriver(user)) {
            throw new PermissionDeniedException(OWNER_ONLY);
        }
        return user;
    }

    public static boolean isDriver(User user) {
        return user.getRole() != null && AccountRole.DRIVER.equals(user.getRole().getRoleName());
    }

    /**
     * Exact username first; otherwise a case-insensitive match, but only when exactly one account matches
     * (usernames are unique only as typed).
     */
    private Optional<User> findInvitee(String inviteeUsername) {
        String wanted = inviteeUsername == null ? "" : inviteeUsername.trim();
        if (!StringUtils.hasText(wanted)) {
            return Optional.empty();
        }
        Optional<User> exact = userRepository.findByUsername(wanted);
        if (exact.isPresent()) {
            return exact;
        }
        List<User> matches = userRepository.findByUsernameIgnoreCase(wanted);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private Map<Integer, User> usersById(Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
    }

    public static TeamUserSummary toSummary(User user) {
        if (user == null) {
            return null;
        }
        String name = String.join(" ",
                StringUtils.hasText(user.getFirstName()) ? user.getFirstName().trim() : "",
                StringUtils.hasText(user.getLastName()) ? user.getLastName().trim() : "").trim();
        return TeamUserSummary.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(name.isEmpty() ? user.getUsername() : name)
                .build();
    }

    private static TeamMemberResponse toMemberResponse(TeamMember row, User member, long openJobCount) {
        return TeamMemberResponse.builder()
                .teamMemberId(row.getTeamMemberId())
                .member(toSummary(member))
                .memberStatus(row.getMemberStatus())
                .invitedAt(row.getInvitedAt())
                .respondedAt(row.getRespondedAt())
                .openJobCount(openJobCount)
                .openJobs(List.of())
                .build();
    }

    /** Started jobs (กำลังส่ง) first, then the nearest scheduled date (no date last), then the older job. */
    static final Comparator<OpenJobSummary> OPEN_JOB_ORDER = Comparator
            .comparing((OpenJobSummary job) -> !"IN_PROGRESS".equals(job.getDeliveryStatus()))
            .thenComparing(OpenJobSummary::getScheduledDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(OpenJobSummary::getJobId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Open jobs grouped by driver, each list in {@link #OPEN_JOB_ORDER}. */
    static Map<Integer, List<OpenJobSummary>> openJobsByDriver(List<DeliveryJobRepository.OpenAssignedJob> rows) {
        Map<Integer, List<OpenJobSummary>> byDriver = new HashMap<>();
        for (DeliveryJobRepository.OpenAssignedJob row : rows) {
            byDriver.computeIfAbsent(row.getDriverUserId(), id -> new ArrayList<>()).add(toOpenJobSummary(row));
        }
        byDriver.values().forEach(list -> list.sort(OPEN_JOB_ORDER));
        return byDriver;
    }

    private static OpenJobSummary toOpenJobSummary(DeliveryJobRepository.OpenAssignedJob row) {
        return OpenJobSummary.builder()
                .jobId(row.getJobId())
                .jobCode(row.getJobCode())
                .jobName(row.getJobName())
                .deliveryStatus(row.getDeliveryStatus())
                .scheduledDate(row.getScheduledDate())
                .stopCount(row.getStopCount())
                .completedStopCount(row.getCompletedStopCount())
                .build();
    }

    private static TeamMembershipResponse toMembershipResponse(TeamMember row, User owner, long openJobCount) {
        return TeamMembershipResponse.builder()
                .teamMemberId(row.getTeamMemberId())
                .owner(toSummary(owner))
                .memberStatus(row.getMemberStatus())
                .invitedAt(row.getInvitedAt())
                .respondedAt(row.getRespondedAt())
                .openJobCount(openJobCount)
                .build();
    }
}
