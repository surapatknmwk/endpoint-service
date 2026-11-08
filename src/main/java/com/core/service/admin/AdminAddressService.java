package com.core.service.admin;

import com.core.dto.admin.AdminAreaRequest;
import com.core.dto.admin.AdminAreaResponse;
import com.master.dto.PageResponse;
import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;
import com.master.repository.DistrictRepository;
import com.master.repository.ProvinceRepository;
import com.master.repository.SubdistrictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * ข้อมูลที่อยู่ (จังหวัด / อำเภอ / ตำบล + รหัสไปรษณีย์) สำหรับผู้ดูแลระบบ (admin.md US-ADM-06)
 * <ul>
 *   <li>แก้ได้: ชื่อ, พิกัด (อำเภอ / ตำบล), รหัสไปรษณีย์ (ตำบล) — แก้พิกัดแล้วตารางระยะทางเส้นตรงไม่ตามเอง ต้องรัน
 *   {@code db/scripts/district_distance_batch.py compute-distances / compute-subdistrict-distances} (ห้าม sync-* — ทับชื่อและพิกัดที่แก้ไว้) — แก้ code / จังหวัดแม่ / อำเภอแม่ ไม่ได้ เพราะออเดอร์
 *   โปรไฟล์ และต้นทางงานเก็บ code เป็นข้อความ เปลี่ยน code = ที่อยู่เดิมหาไม่เจอ</li>
 *   <li>ไม่มีลบ — ปิด / เปิดแทน ({@code status}) แถวที่ปิดหายจากตัวเลือกที่อยู่ แต่ค้นด้วย code ยังเจอ ที่อยู่เดิมจึงแสดงชื่อได้</li>
 *   <li>รหัสไปรษณีย์ไม่ต้องไม่ซ้ำ (หลายตำบลใช้รหัสเดียวกัน) · ชื่อห้ามซ้ำภายในแม่เดียวกัน · code ห้ามซ้ำทั้งตาราง</li>
 * </ul>
 * ไม่มี cache ฝั่ง backend · หน้าเว็บเก็บรายการไว้ตลอด session — ผู้ใช้เห็นของใหม่หลังโหลดหน้าใหม่ / เข้าสู่ระบบใหม่
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAddressService {

    static final int MAX_PAGE_SIZE = 100;
    static final String NOT_FOUND = "ไม่พบข้อมูลนี้";

    private static final Pattern ZIP = Pattern.compile("\\d{5}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final AdminService adminService;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final SubdistrictRepository subdistrictRepository;

    // ---- provinces ----

    @Transactional(readOnly = true)
    public PageResponse<AdminAreaResponse> listProvinces(String adminUsername, String query, int page, int size) {
        adminService.requireAdmin(adminUsername);
        return toPage(provinceRepository.searchForAdmin(AdminService.pattern(query), pageRequest(page, size)),
                AdminAddressService::toResponse);
    }

    @Transactional
    public AdminAreaResponse createProvince(String adminUsername, AdminAreaRequest request) {
        adminService.requireAdmin(adminUsername);
        String code = requireCode(request.getCode(), 2, null);
        requireNewCode(provinceRepository.findByCode(code).isPresent());
        String name = request.getName().trim();
        if (provinceRepository.existsNameForAdmin(name, null)) {
            throw new IllegalArgumentException("มีจังหวัดชื่อ " + name + " อยู่แล้ว");
        }
        Province saved = provinceRepository.save(Province.builder().code(code).name(name).build());
        log.info("admin {} added province {}", adminUsername, code);
        return toResponse(saved);
    }

    @Transactional
    public AdminAreaResponse updateProvince(String adminUsername, Long provinceId, AdminAreaRequest request) {
        adminService.requireAdmin(adminUsername);
        Province province = provinceRepository.findById(provinceId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));
        String name = request.getName().trim();
        if (provinceRepository.existsNameForAdmin(name, provinceId)) {
            throw new IllegalArgumentException("มีจังหวัดชื่อ " + name + " อยู่แล้ว");
        }
        province.setName(name);
        return toResponse(provinceRepository.save(province));
    }

    /** จังหวัดไม่มีแม่ — ปิด / เปิดได้เสมอ (อำเภอ / ตำบลข้างใต้ไม่ถูกแตะ แต่เลือกไม่ได้ไปด้วยเพราะเลือกจังหวัดไม่ได้) */
    @Transactional
    public AdminAreaResponse setProvinceActive(String adminUsername, Long provinceId, boolean active) {
        adminService.requireAdmin(adminUsername);
        Province province = provinceRepository.findById(provinceId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));
        province.setStatus(active ? "A" : "I");
        log.info("admin {} set province {} active={}", adminUsername, province.getCode(), active);
        return toResponse(provinceRepository.save(province));
    }

    // ---- districts ----

    @Transactional(readOnly = true)
    public PageResponse<AdminAreaResponse> listDistricts(String adminUsername, Long provinceId, String query,
                                                         int page, int size) {
        adminService.requireAdmin(adminUsername);
        return toPage(districtRepository.searchForAdmin(provinceId, AdminService.pattern(query), pageRequest(page, size)),
                AdminAddressService::toResponse);
    }

    @Transactional
    public AdminAreaResponse createDistrict(String adminUsername, AdminAreaRequest request) {
        adminService.requireAdmin(adminUsername);
        Province province = request.getParentId() == null ? null
                : provinceRepository.findById(request.getParentId()).orElse(null);
        if (province == null) {
            throw new IllegalArgumentException("กรุณาเลือกจังหวัด");
        }
        String code = requireCode(request.getCode(), 4, province.getCode());
        requireNewCode(districtRepository.findByCode(code).isPresent());
        String name = request.getName().trim();
        if (districtRepository.existsNameForAdmin(province.getProvinceId(), name, null)) {
            throw new IllegalArgumentException("จังหวัด" + province.getName() + " มีอำเภอชื่อ " + name + " อยู่แล้ว");
        }
        requireLatLng(request.getLat(), request.getLng());
        District saved = districtRepository.save(District.builder().province(province).code(code).name(name)
                .lat(request.getLat()).lng(request.getLng()).build());
        log.info("admin {} added district {}", adminUsername, code);
        return toResponse(saved);
    }

    @Transactional
    public AdminAreaResponse updateDistrict(String adminUsername, Long districtId, AdminAreaRequest request) {
        adminService.requireAdmin(adminUsername);
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));
        String name = request.getName().trim();
        if (districtRepository.existsNameForAdmin(district.getProvince().getProvinceId(), name, districtId)) {
            throw new IllegalArgumentException("จังหวัด" + district.getProvince().getName() + " มีอำเภอชื่อ " + name + " อยู่แล้ว");
        }
        requireLatLng(request.getLat(), request.getLng());
        district.setName(name);
        district.setLat(request.getLat());
        district.setLng(request.getLng());
        return toResponse(districtRepository.save(district));
    }

    @Transactional
    public AdminAreaResponse setDistrictActive(String adminUsername, Long districtId, boolean active) {
        adminService.requireAdmin(adminUsername);
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));
        if (active && !district.getProvince().isActive()) {
            throw new IllegalArgumentException("เปิดจังหวัด" + district.getProvince().getName() + " ก่อน");
        }
        district.setStatus(active ? "A" : "I");
        log.info("admin {} set district {} active={}", adminUsername, district.getCode(), active);
        return toResponse(districtRepository.save(district));
    }

    // ---- subdistricts ----

    @Transactional(readOnly = true)
    public PageResponse<AdminAreaResponse> listSubdistricts(String adminUsername, Long districtId, String query,
                                                            int page, int size) {
        adminService.requireAdmin(adminUsername);
        return toPage(subdistrictRepository.searchForAdmin(districtId, AdminService.pattern(query),
                pageRequest(page, size)), AdminAddressService::toResponse);
    }

    @Transactional
    public AdminAreaResponse createSubdistrict(String adminUsername, AdminAreaRequest request) {
        adminService.requireAdmin(adminUsername);
        District district = request.getParentId() == null ? null
                : districtRepository.findById(request.getParentId()).orElse(null);
        if (district == null) {
            throw new IllegalArgumentException("กรุณาเลือกอำเภอ");
        }
        String code = requireCode(request.getCode(), 6, district.getCode());
        requireNewCode(subdistrictRepository.findByCode(code).isPresent());
        String name = request.getName().trim();
        if (subdistrictRepository.existsNameForAdmin(district.getDistrictId(), name, null)) {
            throw new IllegalArgumentException("อำเภอ" + district.getName() + " มีตำบลชื่อ " + name + " อยู่แล้ว");
        }
        String zip = zipOrNull(request.getZipCode());
        requireLatLng(request.getLat(), request.getLng());
        Subdistrict saved = subdistrictRepository.save(Subdistrict.builder().district(district).code(code).name(name)
                .zipCode(zip).lat(request.getLat()).lng(request.getLng()).build());
        log.info("admin {} added subdistrict {}", adminUsername, code);
        return toResponse(saved);
    }

    /** รหัสไปรษณีย์ใหม่ใช้กับออเดอร์ที่สร้าง / แก้หลังจากนี้ — ออเดอร์เดิมเก็บรหัสไว้เอง (order_addresses.zip_code) ไม่เปลี่ยนตาม */
    @Transactional
    public AdminAreaResponse updateSubdistrict(String adminUsername, Long subdistrictId, AdminAreaRequest request) {
        adminService.requireAdmin(adminUsername);
        Subdistrict subdistrict = subdistrictRepository.findById(subdistrictId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));
        String name = request.getName().trim();
        District district = subdistrict.getDistrict();
        if (subdistrictRepository.existsNameForAdmin(district.getDistrictId(), name, subdistrictId)) {
            throw new IllegalArgumentException("อำเภอ" + district.getName() + " มีตำบลชื่อ " + name + " อยู่แล้ว");
        }
        String zip = zipOrNull(request.getZipCode());
        requireLatLng(request.getLat(), request.getLng());
        subdistrict.setName(name);
        subdistrict.setZipCode(zip);
        subdistrict.setLat(request.getLat());
        subdistrict.setLng(request.getLng());
        return toResponse(subdistrictRepository.save(subdistrict));
    }

    @Transactional
    public AdminAreaResponse setSubdistrictActive(String adminUsername, Long subdistrictId, boolean active) {
        adminService.requireAdmin(adminUsername);
        Subdistrict subdistrict = subdistrictRepository.findById(subdistrictId)
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));
        if (active && !subdistrict.getDistrict().isActive()) {
            throw new IllegalArgumentException("เปิดอำเภอ" + subdistrict.getDistrict().getName() + " ก่อน");
        }
        subdistrict.setStatus(active ? "A" : "I");
        log.info("admin {} set subdistrict {} active={}", adminUsername, subdistrict.getCode(), active);
        return toResponse(subdistrictRepository.save(subdistrict));
    }

    // ---- rules ----

    /** ตัวเลขล้วน ยาว {@code length} หลัก และขึ้นต้นด้วย code ของแม่ (รูปแบบรหัสพื้นที่ของกรมการปกครอง) */
    static String requireCode(String raw, int length, String parentCode) {
        String code = raw == null ? "" : raw.trim();
        if (code.length() != length || !DIGITS.matcher(code).matches()) {
            throw new IllegalArgumentException("รหัสต้องเป็นตัวเลข " + length + " หลัก");
        }
        if (parentCode != null && !code.startsWith(parentCode)) {
            throw new IllegalArgumentException("รหัสต้องขึ้นต้นด้วย " + parentCode);
        }
        return code;
    }

    private static void requireNewCode(boolean exists) {
        if (exists) {
            throw new IllegalArgumentException("รหัสนี้มีอยู่แล้ว");
        }
    }

    /** ว่าง = ไม่มีรหัส · ไม่ว่างต้องเป็นตัวเลข 5 หลัก (ซ้ำกับตำบลอื่นได้) */
    static String zipOrNull(String raw) {
        String zip = raw == null ? "" : raw.trim();
        if (zip.isEmpty()) {
            return null;
        }
        if (!ZIP.matcher(zip).matches()) {
            throw new IllegalArgumentException("รหัสไปรษณีย์ต้องเป็นตัวเลข 5 หลัก");
        }
        return zip;
    }

    /** ใส่ทั้งคู่หรือไม่ใส่เลย · อยู่ในช่วงพิกัดของโลก */
    static void requireLatLng(BigDecimal lat, BigDecimal lng) {
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("กรอกพิกัดให้ครบทั้งละติจูดและลองจิจูด");
        }
        if (lat != null && (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IllegalArgumentException("พิกัดไม่ถูกต้อง");
        }
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private static <E> PageResponse<AdminAreaResponse> toPage(Page<E> page, Function<E, AdminAreaResponse> mapper) {
        return PageResponse.of(page.map(mapper));
    }

    private static AdminAreaResponse toResponse(Province p) {
        return AdminAreaResponse.builder().id(p.getProvinceId()).code(p.getCode()).name(p.getName())
                .active(p.isActive()).build();
    }

    private static AdminAreaResponse toResponse(District d) {
        Province p = d.getProvince();
        return AdminAreaResponse.builder().id(d.getDistrictId()).code(d.getCode()).name(d.getName())
                .lat(d.getLat()).lng(d.getLng()).active(d.isActive())
                .parentId(p.getProvinceId()).parentName(p.getName()).parentActive(p.isActive())
                .provinceName(p.getName()).build();
    }

    private static AdminAreaResponse toResponse(Subdistrict s) {
        District d = s.getDistrict();
        Province p = d.getProvince();
        return AdminAreaResponse.builder().id(s.getSubdistrictId()).code(s.getCode()).name(s.getName())
                .zipCode(s.getZipCode()).lat(s.getLat()).lng(s.getLng()).active(s.isActive())
                .parentId(d.getDistrictId()).parentName(d.getName())
                .parentActive(d.isActive() && p.isActive()).provinceName(p.getName()).build();
    }
}
