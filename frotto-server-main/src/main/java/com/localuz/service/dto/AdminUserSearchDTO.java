package com.localuz.service.dto;

import com.localuz.domain.User;

/**
 * Minimal user shape for GET /api/admin/billing/users/search - just enough for an admin to
 * find and pick a user in the Billing panel. Deliberately not AdminUserDTO: no authorities,
 * activation state, langKey, imageUrl, or audit fields - none of that is this endpoint's
 * business.
 */
public class AdminUserSearchDTO {

    private final Long id;
    private final String login;
    private final String email;
    private final String firstName;
    private final String lastName;

    public AdminUserSearchDTO(Long id, String login, String email, String firstName, String lastName) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public static AdminUserSearchDTO from(User user) {
        return new AdminUserSearchDTO(user.getId(), user.getLogin(), user.getEmail(), user.getFirstName(), user.getLastName());
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
