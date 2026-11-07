package com.core.service.team;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.core.constant.TeamMemberStatus;
import com.core.constant.UserSettingKey;
import com.core.dto.TeamUserSummary;
import com.core.entity.DeliveryJob;
import com.core.entity.UserSetting;
import com.core.repository.DeliveryJobRepository;
import com.core.repository.TeamMemberRepository;
import com.core.repository.UserSettingRepository;
import com.core.service.notification.TeamNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The team side of jobs (design §4.2): a driver may operate a job only while it is assigned to them and they are
 * still an ACTIVE member of the owner's team — read from the database on every call. {@link com.core.service.JobService}
 * keeps the owner rule ({@code created_by = username}) and asks here only when the caller is not the owner.
 */
@Service
@RequiredArgsConstructor
public class JobAccessService {

    static final String NOT_A_MEMBER = "เลือกได้เฉพาะสมาชิกในทีม";

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserSettingRepository userSettingRepository;
    private final DeliveryJobRepository deliveryJobRepository;
    private final TeamNotifier teamNotifier;

    /**
     * Who the owner / driver of a job is, as shown on it, whether its owner lets drivers see prices, and their phones
     * — {@code sameTeam} = the pair has an INVITED / ACTIVE team row, the only case either phone may be shown
     * (profile.md US-PROF-07).
     */
    public record JobPeople(TeamUserSummary owner, TeamUserSummary assignedDriver, boolean driverSeesPrice,
                            String ownerPhone, String driverPhone, boolean sameTeam) {

        public JobPeople(TeamUserSummary owner, TeamUserSummary assignedDriver, boolean driverSeesPrice) {
            this(owner, assignedDriver, driverSeesPrice, null, null, false);
        }
    }

    /** canOperateJob minus the owner case: assigned to this user, and they are ACTIVE in the owner's team. */
    public boolean isAssignedActiveDriver(DeliveryJob job, String username) {
        if (job.getAssignedDriverId() == null || username == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .filter(me -> job.getAssignedDriverId().equals(me.getUserId()))
                .map(me -> teamMemberRepository.existsActiveMembership(job.getCreatedBy(), me.getUserId()))
                .orElse(false);
    }

    /** Active jobs assigned to this user from every team they are ACTIVE in (all delivery statuses). */
    public List<DeliveryJob> findAssignedJobs(String username) {
        return userRepository.findByUsername(username)
                .map(me -> deliveryJobRepository.findAssignedToDriver(me.getUserId()))
                .orElse(List.of());
    }

    /**
     * Locks the ACTIVE team row of owner + driver (so a removal waits for the assignment, and then sees the job) and
     * returns the driver.
     *
     * @throws IllegalArgumentException when the driver is not an ACTIVE member of this owner's team
     */
    public User lockActiveMember(String ownerUsername, Integer driverUserId) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException(NOT_A_MEMBER));
        teamMemberRepository.findActiveForUpdate(owner.getUserId(), driverUserId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_A_MEMBER));
        return userRepository.findById(driverUserId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_A_MEMBER));
    }

    /**
     * Tells a driver who had accepted the job that it is no longer theirs (JOB_UNASSIGNED): the owner asked someone
     * else, unassigned, or deleted the job. {@code null} = nobody had it. A new driver is asked, not told
     * ({@link JobDriverRequestService}).
     */
    public void notifyUnassigned(DeliveryJob job, Integer previousDriverId) {
        if (previousDriverId != null) {
            userRepository.findById(previousDriverId).ifPresent(driver -> teamNotifier.jobUnassigned(job, driver));
        }
    }

    /** Owner, driver and price setting of each job, with one query per kind for the whole list. */
    public Map<Long, JobPeople> describe(Collection<DeliveryJob> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }
        Set<String> ownerUsernames = jobs.stream().map(DeliveryJob::getCreatedBy).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> owners = ownerUsernames.isEmpty() ? Map.of()
                : userRepository.findByUsernameIn(ownerUsernames).stream()
                .collect(Collectors.toMap(User::getUsername, Function.identity()));
        Set<Integer> driverIds = jobs.stream().map(DeliveryJob::getAssignedDriverId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, User> drivers = driverIds.isEmpty() ? Map.of()
                : userRepository.findAllById(driverIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Set<Integer> ownersWhoShowPrice = ownersWhoShowPrice(owners.values());
        Set<String> openPairs = openPairs(owners.values(), drivers.keySet());

        Map<Long, JobPeople> people = new HashMap<>();
        for (DeliveryJob job : jobs) {
            User owner = owners.get(job.getCreatedBy());
            User driver = job.getAssignedDriverId() == null ? null : drivers.get(job.getAssignedDriverId());
            people.put(job.getJobId(), new JobPeople(TeamService.toSummary(owner), TeamService.toSummary(driver),
                    owner != null && ownersWhoShowPrice.contains(owner.getUserId()),
                    owner == null ? null : owner.getPhone(), driver == null ? null : driver.getPhone(),
                    owner != null && driver != null && openPairs.contains(pair(owner.getUserId(), driver.getUserId()))));
        }
        return people;
    }

    /** "owner:driver" of every INVITED / ACTIVE row between these owners and drivers. */
    private Set<String> openPairs(Collection<User> owners, Collection<Integer> driverIds) {
        if (owners.isEmpty() || driverIds.isEmpty()) {
            return Set.of();
        }
        return teamMemberRepository.findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(
                        owners.stream().map(User::getUserId).toList(), driverIds, TeamMemberStatus.OPEN).stream()
                .map(row -> pair(row.getOwnerUserId(), row.getMemberUserId()))
                .collect(Collectors.toSet());
    }

    private static String pair(Integer ownerUserId, Integer driverUserId) {
        return ownerUserId + ":" + driverUserId;
    }

    private Set<Integer> ownersWhoShowPrice(Collection<User> owners) {
        List<UserSetting.Key> keys = owners.stream()
                .map(o -> new UserSetting.Key(o.getUserId(), UserSettingKey.DRIVER_SHOW_PRICE))
                .toList();
        if (keys.isEmpty()) {
            return Set.of();
        }
        return userSettingRepository.findAllById(keys).stream()
                .filter(s -> Boolean.parseBoolean(s.getSettingValue()))
                .map(s -> s.getId().getUserId())
                .collect(Collectors.toSet());
    }
}
