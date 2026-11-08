package com.core.repository;

import com.core.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * The given user's own orders that can still be put on a job, PENDING (skipped on an earlier job) first, then
     * the nearest appointment date (none = last), then oldest first, so the {@code pageable} cut keeps the orders
     * that are due soonest or have waited longest.
     * Address and customer are fetched in the same query to avoid N+1 when ranking.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.address a LEFT JOIN FETCH o.customer "
            + "WHERE o.status = 'A' "
            + "AND o.createdBy = :createdBy "
            + "AND UPPER(o.orderStatus) IN ('NEW', 'PENDING') "
            + "AND a.provinceCode IN :provinceCodes "
            + "AND (:districtCode IS NULL OR a.districtCode = :districtCode) "
            + "AND (:subdistrictCode IS NULL OR a.subdistrictCode = :subdistrictCode) "
            + "ORDER BY CASE WHEN UPPER(o.orderStatus) = 'PENDING' THEN 0 ELSE 1 END, "
            + "o.appointmentDate ASC NULLS LAST, o.createdAt ASC, o.orderId ASC")
    List<Order> findCandidateOrdersForJob(@Param("provinceCodes") Collection<String> provinceCodes,
                                           @Param("districtCode") String districtCode,
                                           @Param("subdistrictCode") String subdistrictCode,
                                           @Param("createdBy") String createdBy,
                                           Pageable pageable);

    /**
     * Orders not on a job yet (NEW / PENDING) with an appointment from {@code from} to {@code to} (inclusive) that
     * have not been reminded for this appointment date yet, by owner then nearest appointment first.
     * The customer is fetched in the same query for the reminder text.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer "
            + "WHERE o.status = 'A' "
            + "AND UPPER(o.orderStatus) IN ('NEW', 'PENDING') "
            + "AND o.createdBy IS NOT NULL AND o.createdBy <> '' "
            + "AND o.appointmentDate BETWEEN :from AND :to "
            + "AND (o.appointmentRemindedDate IS NULL OR o.appointmentRemindedDate <> o.appointmentDate) "
            + "ORDER BY o.createdBy, o.appointmentDate ASC, o.orderId ASC")
    List<Order> findAppointmentsToRemind(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Admin order list (admin.md US-ADM-03): active orders whose code or owner contains {@code pattern}
     * (lower-case "%text%", "%" = all), newest first. Customer fetched in the same query.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer "
            + "WHERE o.status = 'A' "
            + "AND (LOWER(o.orderCode) LIKE :pattern OR LOWER(COALESCE(o.createdBy, '')) LIKE :pattern) "
            + "ORDER BY o.createdAt DESC, o.orderId DESC")
    List<Order> searchForAdmin(@Param("pattern") String pattern, Pageable pageable);

    /** Marks the orders as reminded for their current appointment date. */
    @Modifying
    @Query("UPDATE Order o SET o.appointmentRemindedDate = o.appointmentDate WHERE o.orderId IN :orderIds")
    int markAppointmentReminded(@Param("orderIds") Collection<Long> orderIds);

    /** แถวของ {@link #findCustomerSuggestions} — ลูกค้า + ที่อยู่จากออเดอร์เก่า (ชื่อพื้นที่ join จากตาราง master) */
    interface CustomerSuggestionRow {
        String getCustomerName();
        String getPhone();
        Long getPlatformId();
        String getAddressLine();
        String getProvinceCode();
        String getProvinceName();
        String getDistrictCode();
        String getDistrictName();
        String getSubdistrictCode();
        String getSubdistrictName();
        String getZipCode();
        String getMapLink();
        java.time.LocalDateTime getLastOrderAt();
    }

    /**
     * ลูกค้าเดิมของเจ้าของคนนี้ที่ตรงกับคำค้น ในช่องที่กำลังพิมพ์ ({@code field}: name / phone / address) —
     * ซ้ำกัน (ชื่อ + เบอร์ + ที่อยู่ + ตำบล เดียวกัน) เหลือแถวล่าสุดแถวเดียว แล้วเอาใหม่สุดก่อน · {@code pattern} ต้อง escape
     * {@code % _ \} มาแล้ว (ใช้ ESCAPE '\') · phone เทียบเฉพาะตัวเลข
     */
    @Query(value = "SELECT * FROM ("
            + " SELECT DISTINCT ON (lower(btrim(c.name)), coalesce(c.phone, ''), lower(btrim(a.address_line)),"
            + "                     coalesce(a.subdistrict_code, ''))"
            + "   c.name AS customerName, c.phone AS phone, o.platform_id AS platformId,"
            + "   a.address_line AS addressLine, a.province_code AS provinceCode, p.name AS provinceName,"
            + "   a.district_code AS districtCode, d.name AS districtName,"
            + "   a.subdistrict_code AS subdistrictCode, s.name AS subdistrictName,"
            + "   a.zip_code AS zipCode, a.map_link AS mapLink, o.created_at AS lastOrderAt"
            + " FROM orders o"
            + " JOIN customers c ON c.customer_id = o.customer_id"
            + " JOIN order_addresses a ON a.address_id = o.address_id"
            + " LEFT JOIN subdistricts s ON s.code = a.subdistrict_code"
            + " LEFT JOIN districts d ON d.code = a.district_code"
            + " LEFT JOIN provinces p ON p.code = a.province_code"
            + " WHERE o.created_by = :createdBy AND o.status = 'A'"
            + "   AND CASE :field"
            + "         WHEN 'name' THEN c.name ILIKE :pattern ESCAPE '\\'"
            + "         WHEN 'phone' THEN regexp_replace(coalesce(c.phone, ''), '\\D', '', 'g') LIKE :pattern ESCAPE '\\'"
            + "         ELSE a.address_line ILIKE :pattern ESCAPE '\\'"
            + "       END"
            + " ORDER BY lower(btrim(c.name)), coalesce(c.phone, ''), lower(btrim(a.address_line)),"
            + "          coalesce(a.subdistrict_code, ''), o.created_at DESC"
            + ") t ORDER BY lastOrderAt DESC LIMIT :limit", nativeQuery = true)
    List<CustomerSuggestionRow> findCustomerSuggestions(@Param("createdBy") String createdBy,
                                                        @Param("field") String field,
                                                        @Param("pattern") String pattern,
                                                        @Param("limit") int limit);
}
