package com.lan4jn.file.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "sys_role_bucket_permission",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"role_id", "bucket_name", "action"})
        }
)
public class RoleBucketPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(nullable = false)
    private String action;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
