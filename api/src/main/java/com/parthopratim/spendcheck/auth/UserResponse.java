package com.parthopratim.spendcheck.auth;

public record UserResponse(Long id, String email, String name, int salaryDay) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getSalaryDay());
    }
}
