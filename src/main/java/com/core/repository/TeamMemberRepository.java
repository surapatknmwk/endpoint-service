package com.core.repository;

import com.core.entity.TeamMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /**
     * Invites the member unless the pair already has an open (INVITED / ACTIVE) row — the partial unique index
     * {@code uq_team_members_open} decides, so two simultaneous invitations create one row.
     * Returns 1 when a row was created, 0 when an open one already existed.
     */
    @Modifying
    @Query(value = "insert into team_members (team_member_id, owner_user_id, member_user_id, member_role, "
            + "member_status, invited_at, created_at, created_by) "
            + "values (nextval('team_members_team_member_id_seq'), :ownerUserId, :memberUserId, 'DRIVER', "
            + "'INVITED', :now, :now, :createdBy) "
            + "on conflict (owner_user_id, member_user_id) where member_status in ('INVITED', 'ACTIVE') do nothing",
            nativeQuery = true)
    int insertInvitation(@Param("ownerUserId") Integer ownerUserId,
                         @Param("memberUserId") Integer memberUserId,
                         @Param("now") LocalDateTime now,
                         @Param("createdBy") String createdBy);

    Optional<TeamMember> findFirstByOwnerUserIdAndMemberUserIdAndMemberStatus(Integer ownerUserId,
                                                                             Integer memberUserId,
                                                                             String memberStatus);

    /** Locks the row so an owner and a driver changing it at the same time are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TeamMember t where t.teamMemberId = :teamMemberId")
    Optional<TeamMember> findForUpdate(@Param("teamMemberId") Long teamMemberId);

    /** The open ACTIVE row of a pair, locked — assigning a job and removing the member are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TeamMember t where t.ownerUserId = :ownerUserId and t.memberUserId = :memberUserId "
            + "and t.memberStatus = 'ACTIVE'")
    Optional<TeamMember> findActiveForUpdate(@Param("ownerUserId") Integer ownerUserId,
                                             @Param("memberUserId") Integer memberUserId);

    /** Whether the member is ACTIVE in the team of the owner with this username (jobs keep the owner's username). */
    @Query(value = "select exists (select 1 from team_members t join users u on u.user_id = t.owner_user_id "
            + "where u.username = :ownerUsername and t.member_user_id = :memberUserId and t.member_status = 'ACTIVE')",
            nativeQuery = true)
    boolean existsActiveMembership(@Param("ownerUsername") String ownerUsername,
                                   @Param("memberUserId") Integer memberUserId);

    /** Rows of these owners with these members in these statuses — one query for a whole job list. */
    List<TeamMember> findByOwnerUserIdInAndMemberUserIdInAndMemberStatusIn(Collection<Integer> ownerUserIds,
                                                                          Collection<Integer> memberUserIds,
                                                                          Collection<String> statuses);

    List<TeamMember> findByOwnerUserIdAndMemberStatusInOrderByCreatedAtAsc(Integer ownerUserId,
                                                                          Collection<String> memberStatuses);

    List<TeamMember> findByMemberUserIdAndMemberStatusOrderByCreatedAtDesc(Integer memberUserId, String memberStatus);
}
