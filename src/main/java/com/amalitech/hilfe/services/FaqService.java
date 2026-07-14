package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqBulkUploadResult;
import com.amalitech.hilfe.dto.FaqInspectionResult;
import com.amalitech.hilfe.dto.FaqInspectionRow;
import com.amalitech.hilfe.dto.FaqInspectionSummary;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.dto.FaqRowStatus;
import com.amalitech.hilfe.dto.FaqUpsertResult;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
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

    // A submitted question that matches an existing FAQ (see findByNormalizedQuestion)
    // updates that FAQ in place instead of creating a duplicate entry for the question.
    @Transactional
    public FaqUpsertResult createFaq(CreateFaqRequest request) {
        UpsertOutcome outcome = upsertByQuestion(request.question(), request.answer());
        scheduleEmbedAfterCommit(outcome.faq());
        return new FaqUpsertResult(FaqResponse.from(outcome.faq()), outcome.created());
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

    // Intentionally not @Transactional: each row is saved and embedded through its
    // own independently-transactional repository/service calls (like reEmbedAll()),
    // so one bad row rolls back only itself instead of aborting the whole Postgres
    // transaction and taking down every other row's commit (and the CSV file as a
    // whole) with it.
    public FaqBulkUploadResult bulkImport(MultipartFile file) {
        List<FaqBulkUploadResult.RowError> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;
        boolean anyRows = false;

        try (BufferedReader reader = openCsvReader(file);
             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .setIgnoreHeaderCase(true)
                     .build()
                     .parse(reader)) {

            validateHeaders(csvParser.getHeaderNames());

            int rowNumber = 1;
            for (CSVRecord csvRecord : csvParser) {
                anyRows = true;
                rowNumber++;
                String question = extractField(csvRecord, "question");
                String answer = extractField(csvRecord, "answer");
                String validationError = validateRow(question, answer);
                if (validationError != null) {
                    errors.add(new FaqBulkUploadResult.RowError(rowNumber, validationError));
                    continue;
                }
                switch (saveOrUpdateRow(question, answer, rowNumber, errors)) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case FAILED -> { /* error already recorded */ }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse CSV file: " + e.getMessage());
        }

        if (!anyRows) {
            throw new IllegalArgumentException("The provided CSV has no data rows to import.");
        }

        return new FaqBulkUploadResult(created, updated, errors.size(), errors);
    }

    // Parses the same CSV shape as bulkImport() but only previews it: every row is
    // reported back with its own missing-question/missing-answer flags instead of
    // being saved, so the caller can show a "ready vs. needs attention" preview
    // before committing to the actual import.
    public FaqInspectionResult inspectBulkImport(MultipartFile file, FaqRowStatus statusFilter, Pageable pageable) {
        List<FaqInspectionRow> allRows = new ArrayList<>();

        try (BufferedReader reader = openCsvReader(file);
             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .setIgnoreHeaderCase(true)
                     .build()
                     .parse(reader)) {

            validateHeaders(csvParser.getHeaderNames());

            int rowNumber = 1;
            for (CSVRecord csvRecord : csvParser) {
                rowNumber++;
                String question = extractField(csvRecord, "question");
                String answer = extractField(csvRecord, "answer");
                boolean questionMissing = !StringUtils.hasText(question);
                boolean answerMissing = !StringUtils.hasText(answer);
                FaqRowStatus status = (questionMissing || answerMissing) ? FaqRowStatus.NEEDS_ATTENTION : FaqRowStatus.READY;
                allRows.add(new FaqInspectionRow(
                        rowNumber, blankToNull(question), blankToNull(answer), questionMissing, answerMissing, status));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse CSV file: " + e.getMessage());
        }

        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("The provided CSV has no data rows to inspect.");
        }

        int needsAttentionCount = (int) allRows.stream().filter(row -> row.status() == FaqRowStatus.NEEDS_ATTENTION).count();
        FaqInspectionSummary summary = new FaqInspectionSummary(
                allRows.size(), allRows.size() - needsAttentionCount, needsAttentionCount);

        List<FaqInspectionRow> filteredRows = statusFilter == null
                ? allRows
                : allRows.stream().filter(row -> row.status() == statusFilter).toList();

        return new FaqInspectionResult(summary, PageResponse.from(paginate(filteredRows, pageable)));
    }

    private static final List<String> REQUIRED_CSV_HEADERS = List.of("question", "answer");

    // Excel/Sheets CSV exports commonly prepend a UTF-8 BOM, which decodes to a
    // leading U+FEFF character that would otherwise glue itself onto the first
    // header name (e.g. "question" becoming "<BOM>question") and make a
    // legitimately-headed file look headerless to validateHeaders().
    private BufferedReader openCsvReader(MultipartFile file) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        reader.mark(1);
        if (reader.read() != 0xFEFF) {
            reader.reset();
        }
        return reader;
    }

    // Without this check, a CSV missing its header row (or with unrecognized column
    // names) has its first data row silently consumed as the header by the CSV
    // parser, producing a bogus column mapping instead of a clear error.
    private void validateHeaders(List<String> headerNames) {
        boolean missingAny = REQUIRED_CSV_HEADERS.stream()
                .anyMatch(required -> headerNames.stream().noneMatch(h -> h != null && h.trim().equalsIgnoreCase(required)));
        if (missingAny) {
            throw new IllegalArgumentException(
                    "The provided CSV is missing required headers. Expected columns: question, answer.");
        }
    }

    private String extractField(CSVRecord csvRecord, String column) {
        return csvRecord.isMapped(column) ? csvRecord.get(column) : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Page<FaqInspectionRow> paginate(List<FaqInspectionRow> items, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(items, pageable, items.size());
        }
        int start = (int) pageable.getOffset();
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    private String validateRow(String question, String answer) {
        if (!StringUtils.hasText(question)) return "question is blank";
        if (!StringUtils.hasText(answer)) return "answer is blank";
        return null;
    }

    private enum RowOutcome { CREATED, UPDATED, FAILED }

    private RowOutcome saveOrUpdateRow(String question, String answer, int rowNumber, List<FaqBulkUploadResult.RowError> errors) {
        try {
            UpsertOutcome outcome = upsertByQuestion(question, answer);
            faqEmbeddingService.embedAndStore(outcome.faq());
            return outcome.created() ? RowOutcome.CREATED : RowOutcome.UPDATED;
        } catch (Exception e) {
            log.warn("Failed to save FAQ at row {}: {}", rowNumber, e.getMessage());
            errors.add(new FaqBulkUploadResult.RowError(rowNumber, "failed to save: " + e.getMessage()));
            return RowOutcome.FAILED;
        }
    }

    private record UpsertOutcome(Faq faq, boolean created) {}

    // Shared by single-FAQ creation and bulk CSV import: a question that matches an
    // existing FAQ (case- and punctuation-insensitively, see findByNormalizedQuestion)
    // is updated in place rather than duplicated. If two requests for the same new
    // question race past the lookup at once, the unique index from
    // V61__add_faq_question_unique_index.sql rejects the loser's insert (surfaced via
    // the flush below) instead of allowing a duplicate row; that failure is already
    // translated into a 409 by GlobalExceptionHandler/GraphQlExceptionResolver.
    private UpsertOutcome upsertByQuestion(String question, String answer) {
        String sanitizedQuestion = sanitize(question);
        Faq existing = faqRepository.findByNormalizedQuestion(sanitizedQuestion).orElse(null);

        if (existing != null) {
            existing.setAnswer(sanitize(answer));
            return new UpsertOutcome(faqRepository.save(existing), false);
        }

        Faq faq = Faq.builder()
                .id(UUID.randomUUID().toString())
                .question(sanitizedQuestion)
                .answer(sanitize(answer))
                .active(true)
                .build();
        return new UpsertOutcome(faqRepository.saveAndFlush(faq), true);
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
