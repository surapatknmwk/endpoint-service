package com.core.service;

import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * พื้นที่ (จังหวัด / อำเภอ / ตำบล) ที่ผู้ดูแลระบบปิดไว้ (admin.md US-ADM-06) เลือกใหม่ไม่ได้ แต่ <b>ค่าเดิมเก็บไว้ได้</b>
 * <ul>
 *   <li>ระเบียนใหม่ หรือเปลี่ยนพื้นที่ → พื้นที่ที่เลือก และจังหวัด / อำเภอแม่ของมัน ต้องเปิดอยู่ทั้งหมด ไม่งั้น 400</li>
 *   <li>บันทึกระเบียนเดิมโดยพื้นที่ไม่เปลี่ยน → ผ่านเสมอ แม้พื้นที่นั้นถูกปิดไปแล้ว (ฟอร์มแสดง "(ปิดใช้งาน)")</li>
 *   <li>รหัสที่ไม่มีในข้อมูลเลย → ไม่ตรวจที่นี่ (เหมือนเดิมก่อนมีการปิด)</li>
 * </ul>
 * ใช้กับ ที่อยู่ออเดอร์ ({@link OrderService}), ที่อยู่ประจำ (ProfileService), ต้นทางงาน ({@link JobService})
 */
@Component
@RequiredArgsConstructor
public class AreaSelectionGuard {

    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final SubdistrictRepository subdistrictRepository;

    /** รหัสพื้นที่ 3 ระดับของระเบียน (ว่าง / null = ไม่ได้เลือกระดับนั้น) */
    public record AreaCodes(String provinceCode, String districtCode, String subdistrictCode) {

        public static final AreaCodes NONE = new AreaCodes(null, null, null);

        boolean sameAs(AreaCodes other) {
            return Objects.equals(norm(provinceCode), norm(other.provinceCode))
                    && Objects.equals(norm(districtCode), norm(other.districtCode))
                    && Objects.equals(norm(subdistrictCode), norm(other.subdistrictCode));
        }
    }

    /** ที่อยู่ออเดอร์ — {@code current} = ค่าที่บันทึกไว้ ({@link AreaCodes#NONE} ตอนสร้าง) */
    public void requireSelectable(AreaCodes chosen, AreaCodes current) {
        if (chosen.sameAs(current == null ? AreaCodes.NONE : current)) {
            return;
        }
        String sub = norm(chosen.subdistrictCode());
        if (sub != null) {
            subdistrictRepository.findByCode(sub).ifPresent(AreaSelectionGuard::requireActive);
            return;
        }
        String dist = norm(chosen.districtCode());
        if (dist != null) {
            districtRepository.findByCode(dist).ifPresent(AreaSelectionGuard::requireActive);
            return;
        }
        String prov = norm(chosen.provinceCode());
        if (prov != null) {
            provinceRepository.findByCode(prov).ifPresent(AreaSelectionGuard::requireActive);
        }
    }

    /** ต้นทางงาน / ที่อยู่ประจำ (เลือกแค่ตำบล) — {@code currentCode} = ตำบลที่บันทึกไว้ (null ตอนสร้าง) */
    public static void requireSelectable(Subdistrict chosen, String currentCode) {
        if (chosen == null || Objects.equals(norm(chosen.getCode()), norm(currentCode))) {
            return;
        }
        requireActive(chosen);
    }

    static void requireActive(Province province) {
        if (!province.isActive()) {
            throw new IllegalArgumentException(closed("จังหวัด" + province.getName()));
        }
    }

    static void requireActive(District district) {
        requireActive(district.getProvince());
        if (!district.isActive()) {
            throw new IllegalArgumentException(closed("อำเภอ" + district.getName()));
        }
    }

    static void requireActive(Subdistrict subdistrict) {
        requireActive(subdistrict.getDistrict());
        if (!subdistrict.isActive()) {
            throw new IllegalArgumentException(closed("ตำบล" + subdistrict.getName()));
        }
    }

    private static String closed(String area) {
        return area + " ถูกปิดใช้งานแล้ว กรุณาเลือกพื้นที่อื่น";
    }

    private static String norm(String code) {
        return code == null || code.isBlank() ? null : code.trim();
    }
}
