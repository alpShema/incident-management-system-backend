package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateFaqRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/faqs")
@RequiredArgsConstructor
@Tag(name = "FAQs", description = "Admin FAQ management")
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_READ + "')")
    @Operation(summary = "List FAQs", description = "Paginated, filterable list of all FAQ entries")
    public ResponseEntity<ApiResponse<PageResponse<FaqResponse>>> listFaqs(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success("FAQs retrieved", faqService.listFaqs(active, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_READ + "')")
    @Operation(summary = "Get FAQ", description = "Retrieve a single FAQ by ID")
    public ResponseEntity<ApiResponse<FaqResponse>> getFaq(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("FAQ retrieved", faqService.getFaq(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_CREATE + "')")
    @Operation(summary = "Create FAQ", description = "Add a new FAQ entry. Triggers embedding generation.")
    public ResponseEntity<ApiResponse<FaqResponse>> createFaq(@Valid @RequestBody CreateFaqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("FAQ created", faqService.createFaq(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_UPDATE + "')")
    @Operation(summary = "Update FAQ", description = "Update FAQ fields. Re-embeds if question or answer changes.")
    public ResponseEntity<ApiResponse<FaqResponse>> updateFaq(
            @PathVariable String id,
            @Valid @RequestBody UpdateFaqRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("FAQ updated", faqService.updateFaq(id, request)));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_UPDATE + "')")
    @Operation(summary = "Toggle FAQ active state", description = "Activate or deactivate a FAQ entry")
    public ResponseEntity<ApiResponse<FaqResponse>> toggleActive(
            @PathVariable String id,
            @RequestParam boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                active ? "FAQ activated" : "FAQ deactivated",
                faqService.toggleActive(id, active)
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_DELETE + "')")
    @Operation(summary = "Delete FAQ", description = "Permanently remove a FAQ entry")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(@PathVariable String id) {
        faqService.deleteFaq(id);
        return ResponseEntity.ok(ApiResponse.success("FAQ deleted", null));
    }
}
