package com.master.repository;

import com.master.entity.GoogleRoutesUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GoogleRoutesUsageRepository extends JpaRepository<GoogleRoutesUsage, LocalDate> {

    /**
     * เพิ่ม call_count ของวันนั้นอีก 1 แบบ atomic เฉพาะเมื่อยังต่ำกว่า {@code limit} — คืน 1 ถ้าจองโควตาได้,
     * 0 ถ้าเต็มแล้ว (แถวของวันนั้นมีอยู่และ call_count >= limit) ผู้เรียกต้องส่ง {@code limit >= 1}
     */
    @Modifying
    @Query(value = "INSERT INTO google_routes_usage (usage_date, call_count, rejected_count, created_at, updated_at) "
            + "VALUES (:date, 1, 0, now(), now()) "
            + "ON CONFLICT (usage_date) DO UPDATE SET call_count = google_routes_usage.call_count + 1, updated_at = now() "
            + "WHERE google_routes_usage.call_count < :limit", nativeQuery = true)
    int incrementCallCountIfBelowLimit(@Param("date") LocalDate date, @Param("limit") int limit);

    @Modifying
    @Query(value = "INSERT INTO google_routes_usage (usage_date, call_count, rejected_count, created_at, updated_at) "
            + "VALUES (:date, 0, 1, now(), now()) "
            + "ON CONFLICT (usage_date) DO UPDATE SET rejected_count = google_routes_usage.rejected_count + 1, updated_at = now()",
            nativeQuery = true)
    int incrementRejectedCount(@Param("date") LocalDate date);

    List<GoogleRoutesUsage> findByUsageDateGreaterThanEqualOrderByUsageDateDesc(LocalDate since);
}
