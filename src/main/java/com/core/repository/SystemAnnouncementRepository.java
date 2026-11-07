package com.core.repository;

import com.core.entity.SystemAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, Long> {

    /** ประกาศที่ถึงเวลาส่ง ยังไม่ส่ง ยังไม่ยกเลิก และยังไม่หมดอายุ — เก่าก่อน */
    @Query("select a.announcementId from SystemAnnouncement a "
            + "where a.status = 'A' and a.publishedAt is null and a.publishAt <= :now "
            + "and (a.expiresAt is null or a.expiresAt > :now) "
            + "order by a.publishAt, a.announcementId")
    List<Long> findDueIds(@Param("now") LocalDateTime now);

    /** ล็อกประกาศที่ยังรอส่งไว้ระหว่างสร้างแจ้งเตือน · ไม่เจอ = ถูกส่ง / ยกเลิก / ล็อกโดยอีกรอบไปแล้ว */
    @Query(value = "select * from system_announcements "
            + "where announcement_id = :id and status = 'A' and published_at is null "
            + "for update skip locked", nativeQuery = true)
    Optional<SystemAnnouncement> lockUnpublished(@Param("id") Long id);
}
