package com.grash.dto.operator;

import lombok.Data;

import java.util.Date;

@Data
public class CustomerCompanyDTO {
    private Long id;
    private String name;
    private Date createdAt;
    private String adminEmail;
    private String adminName;
    private long usersCount;
    private boolean demo;
}
