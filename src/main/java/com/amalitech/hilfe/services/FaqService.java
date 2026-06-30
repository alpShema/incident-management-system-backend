package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqBulkUploadResult;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateFaqRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;
    private final FaqEmbeddingService faqEmbeddingService;

    @Transactional
    public FaqResponse createFaq(CreateFaqRequest request) {
        Faq faq = Faq.builder()
                .id(UUID.randomUUID().toString())
                .question(sanitize(request.question()))
                .answer(sanitize(request.answer()))
                .active(true)
                .build();

        faq = faqRepository.save(faq);
        scheduleEmbedAfterCommit(faq);
        return FaqResponse.from(faq);
    }

    @Transactional
    public FaqResponse updateFaq(String id, UpdateFaqRequest request) {
        Faq faq = findOrThrow(id);

        boolean reEmbed = false;
        if (StringUtils.hasText(request.question())) {
            faq.setQuestion(sanitize(request.question()));
            reEmbed = true;
        }
        if (StringUtils.hasText(request.answer())) {
            faq.setAnswer(sanitize(request.answer()));
            reEmbed = true;
        }
        faq = faqRepository.save(faq);
        if (reEmbed) scheduleEmbedAfterCommit(faq);
        return FaqResponse.from(faq);
    }

    @Transactional
    public FaqResponse toggleActive(String id, boolean active) {
        Faq faq = findOrThrow(id);
        faq.setActive(active);
        return FaqResponse.from(faqRepository.save(faq));
    }

    @Transactional
    public void deleteFaq(String id) {
        findOrThrow(id);
        faqRepository.deleteById(id);
    }

    public FaqResponse getFaq(String id) {
        return FaqResponse.from(findOrThrow(id));
    }

    public PageResponse<FaqResponse> listFaqs(Boolean active, String search, Pageable pageable) {
        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%" : null;
        return PageResponse.from(
                faqRepository.findAllFiltered(active, searchPattern, pageable).map(FaqResponse::from)
        );
    }

    public int reEmbedAll() {
        List<Faq> faqs = faqRepository.findAllWithoutEmbedding();
        log.info("Re-embed triggered | {} FAQs missing embeddings", faqs.size());
        if (faqs.isEmpty()) return 0;
        int succeeded = 0;
        for (Faq faq : faqs) {
            log.debug("Re-embedding FAQ {} | question=\"{}\"", faq.getId(), faq.getQuestion());
            if (faqEmbeddingService.embedAndStore(faq)) {
                succeeded++;
                log.info("Re-embed OK | faqId={} | {}/{}", faq.getId(), succeeded, faqs.size());
            } else {
                log.warn("Re-embed FAILED | faqId={} | question=\"{}\"", faq.getId(), faq.getQuestion());
            }
        }
        log.info("Re-embed complete | {}/{} succeeded", succeeded, faqs.size());
        return succeeded;
    }

    @Transactional
    public FaqBulkUploadResult bulkImport(MultipartFile file) {
        List<FaqBulkUploadResult.RowError> errors = new ArrayList<>();
        int created = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            int rowNumber = 1;
            for (CSVRecord csvRecord : csvParser) {
                rowNumber++;
                String question = csvRecord.isMapped("question") ? csvRecord.get("question") : "";
                String answer = csvRecord.isMapped("answer") ? csvRecord.get("answer") : "";
                String validationError = validateRow(question, answer);
                if (validationError != null) {
                    errors.add(new FaqBulkUploadResult.RowError(rowNumber, validationError));
                } else {
                    created += saveRow(question, answer, rowNumber, errors);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse CSV file: " + e.getMessage());
        }

        return new FaqBulkUploadResult(created, errors.size(), errors);
    }

    private String validateRow(String question, String answer) {
        if (!StringUtils.hasText(question)) return "question is blank";
        if (!StringUtils.hasText(answer)) return "answer is blank";
        return null;
    }

    private int saveRow(String question, String answer, int rowNumber, List<FaqBulkUploadResult.RowError> errors) {
        try {
            Faq faq = Faq.builder()
                    .id(UUID.randomUUID().toString())
                    .question(sanitize(question))
                    .answer(sanitize(answer))
                    .active(true)
                    .build();
            faq = faqRepository.save(faq);
            faqEmbeddingService.embedAndStore(faq);
            return 1;
        } catch (Exception e) {
            log.warn("Failed to create FAQ at row {}: {}", rowNumber, e.getMessage());
            errors.add(new FaqBulkUploadResult.RowError(rowNumber, "failed to save: " + e.getMessage()));
            return 0;
        }
    }

    // Registers the embedding update to run after the current transaction commits,
    // so a vector-store failure cannot roll back the FAQ save. The actual embed call
    // must go through an @Async proxy method (FaqEmbeddingService) — calling a
    // @Transactional repository method directly inside afterCommit() on this thread
    // binds to the original transaction's not-yet-unbound resources instead of
    // opening a fresh one, causing "No active transaction" errors.
    private void scheduleEmbedAfterCommit(Faq faq) {
        log.debug("Scheduling post-commit embed | faqId={} | syncActive={}",
                faq.getId(), TransactionSynchronizationManager.isSynchronizationActive());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Post-commit embed firing | faqId={}", faq.getId());
                faqEmbeddingService.embedAndStoreAsync(faq);
            }
        });
    }

    private Faq findOrThrow(String id) {
        return faqRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("FAQ not found: " + id, 404));
    }

    private String sanitize(String input) {
        if (input == null) return null;
        // Strip HTML/script tags to prevent XSS stored in FAQ content
        return input.replaceAll("<[^>]*>", "").trim();
    }
}
