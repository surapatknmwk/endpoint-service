package com.core.repository;

import com.core.entity.OrderAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderAddressRepository extends JpaRepository<OrderAddress, Long>, JpaSpecificationExecutor<OrderAddress> {

    List<OrderAddress> findByProvinceCode(String provinceCode);

    List<OrderAddress> findByDistrictCode(String districtcode);

    List<OrderAddress> findBySubdistrictCode(String subdistrictCode);
}
