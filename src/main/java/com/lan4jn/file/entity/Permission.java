package com.lan4jn.file.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "sys_permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    private String type; // menu, button, api

    private String url;

    private String parentCode;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission that)) {
            return false;
        }
        if (code != null && that.code != null) {
            return Objects.equals(code, that.code);
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        if (code != null) {
            return Objects.hash(code);
        }
        return Objects.hash(id);
    }
}

