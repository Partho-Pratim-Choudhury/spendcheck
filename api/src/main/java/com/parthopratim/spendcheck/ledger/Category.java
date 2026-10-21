package com.parthopratim.spendcheck.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryKind kind;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> aliases = new ArrayList<>();

    @Column(nullable = false)
    private boolean archived;

    protected Category() {
    }

    public Category(Long userId, String name, CategoryKind kind, List<String> aliases) {
        this.userId = userId;
        this.name = name;
        this.kind = kind;
        setAliases(aliases);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public CategoryKind getKind() { return kind; }
    public List<String> getAliases() { return aliases; }
    public boolean isArchived() { return archived; }

    public void setName(String name) { this.name = name; }
    public void setKind(CategoryKind kind) { this.kind = kind; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
    }
}
