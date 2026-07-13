package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqBulkUploadResult;
import com.amalitech.hilfe.dto.FaqInspectionResult;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.dto.FaqRowStatus;
import com.amalitech.hilfe.dto.FaqUpsertResult;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateFaqRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success("FAQs retrieved", faqService.listFaqs(active, search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_READ + "')")
    @Operation(summary = "Get FAQ", description = "Retrieve a single FAQ by ID")
    public ResponseEntity<ApiResponse<FaqResponse>> getFaq(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("FAQ retrieved", faqService.getFaq(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_CREATE + "')")
    @Operation(summary = "Create or update FAQ",
            description = "Add a new FAQ entry, or update the existing FAQ with a matching question. "
                    + "Returns 201 when a new FAQ is created, 200 when an existing one is updated. "
                    + "Triggers embedding generation.")
    public ResponseEntity<ApiResponse<FaqResponse>> createFaq(@Valid @RequestBody CreateFaqRequest request) {
        FaqUpsertResult result = faqService.createFaq(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "FAQ created" : "FAQ updated";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.faq()));
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

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_CREATE + "')")
    @Operation(summary = "Download CSV template", description = "Returns a blank CSV file with the required headers (question, answer) for bulk upload")
    public ResponseEntity<Resource> downloadTemplate() {
        byte[] csvBytes = "question,answer\n".getBytes();
        ByteArrayResource resource = new ByteArrayResource(csvBytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("faq_template.csv").build().toString())
                .contentLength(csvBytes.length)
                .body(resource);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_CREATE + "')")
    @Operation(summary = "Bulk upload FAQs", description = "Upload a CSV file with question and answer columns to create multiple FAQs at once")
    public ResponseEntity<ApiResponse<FaqBulkUploadResult>> bulkUpload(
            @RequestParam("file") MultipartFile file
    ) {
        String fileError = validateCsvFile(file);
        if (fileError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.success(fileError, null));
        }
        FaqBulkUploadResult result = faqService.bulkImport(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        result.created() + " FAQ(s) created, " + result.updated() + " updated, "
                                + result.failed() + " row(s) skipped",
                        result
                ));
    }

    @PostMapping("/inspect")
    @PreAuthorize("hasAuthority('" + RbacPermissions.FAQ_CREATE + "')")
    @Operation(summary = "Inspect a bulk FAQ CSV",
            description = "Parses a CSV file and previews every row without creating or modifying any FAQs. "
                    + "Returns a summary of rows ready to import vs. rows needing attention (missing question "
                    + "and/or answer), plus the paginated rows themselves. Pass `status=NEEDS_ATTENTION` to "
                    + "return only the rows with issues, or `status=READY` for only the importable ones.")
    public ResponseEntity<ApiResponse<FaqInspectionResult>> inspect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) FaqRowStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String fileError = validateCsvFile(file);
        if (fileError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.success(fileError, null));
        }
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "CSV inspected", faqService.inspectBulkImport(file, status, pageable)));
    }

    private String validateCsvFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "Uploaded file is empty";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return "Only CSV files are accepted";
        }
        return null;
    }
}
