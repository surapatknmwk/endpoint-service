package com.master;

import com.master.entity.District;
import com.master.entity.Province;
import com.master.entity.Subdistrict;

import java.math.BigDecimal;

/** Shared test data builders for the master-data module. */
public final class MasterFixtures {

    private MasterFixtures() {
    }

    public static Province province(long id, String code, String name) {
        return Province.builder().provinceId(id).code(code).name(name).build();
    }

    public static District district(long id, String code, String name, Province province) {
        return District.builder().districtId(id).code(code).name(name).province(province).build();
    }

    public static Subdistrict subdistrict(long id, String code, String name, District district) {
        return Subdistrict.builder().subdistrictId(id).code(code).name(name).district(district)
                .zipCode("47000").lat(new BigDecimal("17.15")).lng(new BigDecimal("104.14")).build();
    }
}
