package com.search_recommendation_engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table( name = "products" )
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false )
    private String name;

    @Column( columnDefinition = "TEXT")
    private String description;

    private BigDecimal price;

    private int stockQuantity;

    @ManyToOne( fetch = FetchType.LAZY )
    private Category category;

    @ElementCollection
    @CollectionTable( name = "product_tags" , joinColumns = @JoinColumn( name = "product_id" ) )
    @Column( name = "tag" )
    private List<String> tags = new ArrayList<>();

    private long popularityScore;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}