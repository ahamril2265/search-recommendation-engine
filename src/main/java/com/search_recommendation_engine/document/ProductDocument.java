package com.search_recommendation_engine.document;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String categoryName;
    private List<String> tags;
    private long popularityScore;
    private int stockQuantity;
    private List<String> nameSuggest;

}