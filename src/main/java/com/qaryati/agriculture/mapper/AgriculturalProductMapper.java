
package com.qaryati.agriculture.mapper;

import com.qaryati.agriculture.dto.AgriculturalProductRequest;
import com.qaryati.agriculture.dto.AgriculturalProductResponse;
import com.qaryati.agriculture.entity.AgriculturalProduct;
import org.springframework.stereotype.Component;

@Component
public class AgriculturalProductMapper {

    public AgriculturalProduct toEntity(
            AgriculturalProductRequest request) {

        AgriculturalProduct product = new AgriculturalProduct();

        product.setNameAr(request.nameAr());
        product.setNameEn(request.nameEn());
        product.setProductType(request.productType());

        return product;
    }

    public AgriculturalProductResponse toResponse(
            AgriculturalProduct product) {

        return new AgriculturalProductResponse(
                product.getId(),
                product.getNameAr(),
                product.getNameEn(),
                product.getProductType()
        );
    }

    public void updateEntity(
            AgriculturalProductRequest request,
            AgriculturalProduct product) {

        product.setNameAr(request.nameAr());
        product.setNameEn(request.nameEn());
        product.setProductType(request.productType());
    }
}
