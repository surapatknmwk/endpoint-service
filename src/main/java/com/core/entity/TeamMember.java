package com.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One invitation of a driver into an owner's team, and the membership that follows it
 * (see {@link com.core.constant.TeamMemberStatus}). New invitations are created only through
 * {@link com.core.repository.TeamMemberRepository#insertInvitation} so the one-open-row-per-pair index is respected.
 */
@Entity
@Table(name = "team_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "team_members_seq")
    @SequenceGenerator(name = "team_members_seq", sequenceName = "team_members_team_member_id_seq", allocationSize = 1)
    @Column(name = "team_member_id")
    private Long teamMemberId;

    @Column(name = "owner_user_id", nullable = false)
    private Integer ownerUserId;

    @Column(name = "member_user_id", nullable = false)
    private Integer memberUserId;

    @Column(name = "member_role", nullable = false, length = 20)
    private String memberRole;

    @Column(name = "member_status", nullable = false, length = 20)
    private String memberStatus;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
