package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AdminResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.repositories.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdminRepository adminRepository;

    public AdminResponse updateAvailability(String userId, boolean available) {
        Admin admin = adminRepository.findByUserIdWithUser(userId)
                .orElseThrow(() -> new ArmsAuthException("Admin not found", 404));
        admin.setStatus(available);
        Admin saved = adminRepository.save(admin);
        return AdminResponse.from(saved);
    }
}
