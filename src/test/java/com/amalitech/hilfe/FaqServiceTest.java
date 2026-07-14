package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqBulkUploadResult;
import com.amalitech.hilfe.dto.FaqInspectionResult;
import com.amalitech.hilfe.dto.FaqRowStatus;
import com.amalitech.hilfe.dto.FaqUpsertResult;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.FaqRepository;
import com.amalitech.hilfe.services.FaqEmbeddingService;
import com.amalitech.hilfe.services.FaqService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock FaqRepository faqRepository;
    @Mock FaqEmbeddingService faqEmbeddingService;
    @InjectMocks FaqService faqService;

    private Faq faq(String id, String question, String answer) {
        return Faq.builder().id(id).question(question).answer(answer).active(true).build();
    }

    private Page<Faq> pageOf(Faq... faqs) {
        return new PageImpl<>(List.of(faqs));
    }

    @BeforeEach
    void initTransactionSynchronization() {
        // createFaq() registers a post-commit embed callback via
        // TransactionSynchronizationManager, which requires an active synchronization
        // even outside a real Spring transaction.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void createFaq_questionDoesNotExist_createsNewFaqAndReturnsCreatedTrue() {
        when(faqRepository.findByNormalizedQuestion("New question")).thenReturn(Optional.empty());
        when(faqRepository.saveAndFlush(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaqUpsertResult result = faqService.createFaq(new CreateFaqRequest("New question", "New answer"));

        assertThat(result.created()).isTrue();
        assertThat(result.faq().question()).isEqualTo("New question");
        assertThat(result.faq().answer()).isEqualTo("New answer");
        verify(faqRepository).saveAndFlush(any(Faq.class));
    }

    @Test
    void createFaq_questionAlreadyExists_updatesExistingFaqAndReturnsCreatedFalse() {
        Faq existing = faq("existing-id", "What is your name?", "Old answer");
        when(faqRepository.findByNormalizedQuestion("what is your name?")).thenReturn(Optional.of(existing));
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Different casing of the same question should still match the existing FAQ
        // and update it in place instead of creating a duplicate.
        FaqUpsertResult result = faqService.createFaq(new CreateFaqRequest("what is your name?", "New answer"));

        assertThat(result.created()).isFalse();
        assertThat(result.faq().id()).isEqualTo("existing-id");
        assertThat(result.faq().answer()).isEqualTo("New answer");
        assertThat(existing.getAnswer()).isEqualTo("New answer");
        verify(faqRepository).save(argThat(f -> "existing-id".equals(f.getId())));
    }

    @Test
    void createFaq_matchIsDrivenByNormalizedQuestionLookup_notRawQuestionText() {
        // The DB-level match (and its backing unique index, see
        // V61__add_faq_question_unique_index.sql) is punctuation- and
        // whitespace-insensitive, so a question missing its trailing "?" or with
        // extra spacing still resolves to the same FAQ. The native query owns that
        // normalization; here we only confirm the service defers the "is this a
        // duplicate" decision to it rather than comparing raw question strings itself.
        Faq existing = faq("existing-id", "What is your name?", "Old answer");
        when(faqRepository.findByNormalizedQuestion("What   is your name")).thenReturn(Optional.of(existing));
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaqUpsertResult result = faqService.createFaq(new CreateFaqRequest("What   is your name", "New answer"));

        assertThat(result.created()).isFalse();
        assertThat(result.faq().id()).isEqualTo("existing-id");
        verify(faqRepository, never()).saveAndFlush(any());
    }

    @Test
    void listFaqs_noSearch_passesNullToRepository() {
        when(faqRepository.findAllFiltered(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pageOf());

        faqService.listFaqs(null, null, Pageable.unpaged());

        verify(faqRepository).findAllFiltered(null, null, Pageable.unpaged());
    }

    @Test
    void listFaqs_emptySearch_treatedAsNull() {
        when(faqRepository.findAllFiltered(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(pageOf());

        faqService.listFaqs(null, "   ", Pageable.unpaged());

        verify(faqRepository).findAllFiltered(null, null, Pageable.unpaged());
    }

    @Test
    void listFaqs_withSearch_trimmedAndPassedToRepository() {
        when(faqRepository.findAllFiltered(isNull(), eq("%shipping%"), any(Pageable.class)))
                .thenReturn(pageOf(faq("1", "Shipping policy", "We ship worldwide")));

        PageResponse<FaqResponse> result = faqService.listFaqs(null, "  shipping  ", Pageable.unpaged());

        verify(faqRepository).findAllFiltered(null, "%shipping%", Pageable.unpaged());
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).question()).isEqualTo("Shipping policy");
    }

    @Test
    void listFaqs_withSearchAndActiveFilter_bothPassedToRepository() {
        when(faqRepository.findAllFiltered(eq(true), eq("%refund%"), any(Pageable.class)))
                .thenReturn(pageOf(faq("2", "Refund policy", "30 day returns")));

        PageResponse<FaqResponse> result = faqService.listFaqs(true, "refund", Pageable.unpaged());

        verify(faqRepository).findAllFiltered(true, "%refund%", Pageable.unpaged());
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void listFaqs_noMatchingSearch_returnsEmptyList() {
        when(faqRepository.findAllFiltered(isNull(), eq("%zzznomatch%"), any(Pageable.class)))
                .thenReturn(pageOf());

        PageResponse<FaqResponse> result = faqService.listFaqs(null, "zzznomatch", Pageable.unpaged());

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void bulkImport_isNotTransactional() throws NoSuchMethodException {
        // Wrapping the whole CSV loop in one @Transactional method means a single
        // row-level DB error aborts the underlying Postgres transaction, so every
        // later statement -- including the final commit -- fails with an unhandled
        // exception (surfaced to clients as a generic 500). Each row must instead
        // commit through its own independently-transactional repository call.
        Method bulkImport = FaqService.class.getMethod("bulkImport", org.springframework.web.multipart.MultipartFile.class);

        assertThat(bulkImport.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void bulkImport_oneRowFailsToSave_othersStillCreatedAndReported() {
        when(faqRepository.saveAndFlush(any(Faq.class))).thenAnswer(invocation -> {
            Faq faq = invocation.getArgument(0);
            if ("Q2".equals(faq.getQuestion())) {
                throw new org.springframework.dao.DataIntegrityViolationException("simulated row failure");
            }
            return faq;
        });
        when(faqEmbeddingService.embedAndStore(any(Faq.class))).thenReturn(true);

        String csv = "question,answer\nQ1,A1\nQ2,A2\nQ3,A3\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "faqs.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FaqBulkUploadResult result = faqService.bulkImport(file);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).row()).isEqualTo(3);
    }

    @Test
    void bulkImport_capitalizedHeadersWithExtraColumn_stillMapsQuestionAndAnswer() {
        when(faqRepository.saveAndFlush(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(faqEmbeddingService.embedAndStore(any(Faq.class))).thenReturn(true);

        // Real-world exports (e.g. from spreadsheet tools) commonly use
        // capitalized headers and may include extra columns we don't use.
        String csv = "Question,Answer,Status\nQ1,A1,Active\nQ2,A2,Active\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "faqs.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FaqBulkUploadResult result = faqService.bulkImport(file);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void bulkImport_questionAlreadyExists_updatesExistingFaqInsteadOfDuplicating() {
        Faq existing = faq("existing-id", "What is your name?", "Old answer");
        when(faqRepository.findByNormalizedQuestion("what is your name?")).thenReturn(Optional.of(existing));
        when(faqRepository.findByNormalizedQuestion("New question")).thenReturn(Optional.empty());
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(faqRepository.saveAndFlush(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(faqEmbeddingService.embedAndStore(any(Faq.class))).thenReturn(true);

        // "what is your name?" (different case) should match the existing FAQ
        // and update it in place rather than creating a duplicate.
        String csv = "question,answer\nwhat is your name?,New answer\nNew question,New answer\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "faqs.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FaqBulkUploadResult result = faqService.bulkImport(file);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(existing.getAnswer()).isEqualTo("New answer");
        verify(faqRepository).save(argThat(f -> "existing-id".equals(f.getId())));
    }

    private MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "faqs.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void inspectBulkImport_mixedRows_summaryCountsReadyAndNeedsAttention() {
        MockMultipartFile file = csvFile("question,answer\nQ1,A1\n,A2\nQ3,\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, Pageable.unpaged());

        assertThat(result.summary().totalRows()).isEqualTo(3);
        assertThat(result.summary().readyCount()).isEqualTo(1);
        assertThat(result.summary().needsAttentionCount()).isEqualTo(2);
        assertThat(result.rows().totalElements()).isEqualTo(3);
    }

    @Test
    void inspectBulkImport_missingQuestion_flagsQuestionMissingAndNullsIt() {
        MockMultipartFile file = csvFile("question,answer\n,A2\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, Pageable.unpaged());

        var row = result.rows().items().get(0);
        assertThat(row.row()).isEqualTo(2);
        assertThat(row.question()).isNull();
        assertThat(row.answer()).isEqualTo("A2");
        assertThat(row.questionMissing()).isTrue();
        assertThat(row.answerMissing()).isFalse();
        assertThat(row.status()).isEqualTo(FaqRowStatus.NEEDS_ATTENTION);
    }

    @Test
    void inspectBulkImport_missingAnswer_flagsAnswerMissingAndNullsIt() {
        MockMultipartFile file = csvFile("question,answer\nQ1,\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, Pageable.unpaged());

        var row = result.rows().items().get(0);
        assertThat(row.question()).isEqualTo("Q1");
        assertThat(row.answer()).isNull();
        assertThat(row.questionMissing()).isFalse();
        assertThat(row.answerMissing()).isTrue();
        assertThat(row.status()).isEqualTo(FaqRowStatus.NEEDS_ATTENTION);
    }

    @Test
    void inspectBulkImport_completeRow_isReady() {
        MockMultipartFile file = csvFile("question,answer\nQ1,A1\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, Pageable.unpaged());

        var row = result.rows().items().get(0);
        assertThat(row.questionMissing()).isFalse();
        assertThat(row.answerMissing()).isFalse();
        assertThat(row.status()).isEqualTo(FaqRowStatus.READY);
    }

    @Test
    void inspectBulkImport_filterNeedsAttention_returnsOnlyProblemRowsButSummaryCoversWholeFile() {
        MockMultipartFile file = csvFile("question,answer\nQ1,A1\n,A2\nQ3,\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, FaqRowStatus.NEEDS_ATTENTION, Pageable.unpaged());

        assertThat(result.rows().items()).hasSize(2);
        assertThat(result.rows().items()).allMatch(r -> r.status() == FaqRowStatus.NEEDS_ATTENTION);
        assertThat(result.rows().totalElements()).isEqualTo(2);
        // Summary always reflects the whole file, regardless of the filter applied to `rows`.
        assertThat(result.summary().totalRows()).isEqualTo(3);
    }

    @Test
    void inspectBulkImport_filterReady_returnsOnlyCleanRows() {
        MockMultipartFile file = csvFile("question,answer\nQ1,A1\n,A2\nQ3,\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, FaqRowStatus.READY, Pageable.unpaged());

        assertThat(result.rows().items()).hasSize(1);
        assertThat(result.rows().items().get(0).question()).isEqualTo("Q1");
    }

    @Test
    void inspectBulkImport_pagination_returnsRequestedPageOfRows() {
        MockMultipartFile file = csvFile("question,answer\nQ1,A1\nQ2,A2\nQ3,A3\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, PageRequest.of(1, 2));

        assertThat(result.rows().items()).hasSize(1);
        assertThat(result.rows().items().get(0).question()).isEqualTo("Q3");
        assertThat(result.rows().totalElements()).isEqualTo(3);
        assertThat(result.rows().totalPages()).isEqualTo(2);
    }

    @Test
    void inspectBulkImport_neverPersistsAnything() {
        MockMultipartFile file = csvFile("question,answer\nQ1,A1\n,A2\n");

        faqService.inspectBulkImport(file, null, Pageable.unpaged());

        verify(faqRepository, never()).save(any());
        verify(faqRepository, never()).saveAndFlush(any());
        verify(faqEmbeddingService, never()).embedAndStore(any());
    }

    @Test
    void inspectBulkImport_headerlessCsv_rejectedInsteadOfTreatingFirstRowAsHeader() {
        // No "question,answer" header line -- the first data row must not be
        // silently consumed as the header by the CSV parser.
        MockMultipartFile file = csvFile("How do I reset my password?,Click the forgot password link\n");

        assertThatThrownBy(() -> faqService.inspectBulkImport(file, null, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required headers")
                .hasMessageContaining("question, answer");
    }

    @Test
    void inspectBulkImport_missingAnswerHeaderOnly_rejected() {
        MockMultipartFile file = csvFile("question,notes\nQ1,some note\n");

        assertThatThrownBy(() -> faqService.inspectBulkImport(file, null, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required headers");
    }

    @Test
    void inspectBulkImport_headersOnlyNoDataRows_rejectedWithDescriptiveError() {
        MockMultipartFile file = csvFile("question,answer\n");

        assertThatThrownBy(() -> faqService.inspectBulkImport(file, null, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data rows");
    }

    @Test
    void inspectBulkImport_blankContent_rejectedAsMissingHeaders() {
        // Non-zero-byte but content-free CSV (e.g. a stray newline) never
        // establishes a real header row, so it must fail the header check
        // rather than silently returning an empty "all good" result.
        MockMultipartFile file = csvFile("\n");

        assertThatThrownBy(() -> faqService.inspectBulkImport(file, null, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required headers");
    }

    @Test
    void inspectBulkImport_utf8BomBeforeHeader_stillRecognizesHeaders() {
        // Excel/Sheets CSV exports commonly prepend a UTF-8 BOM before the header
        // row; it must be stripped so "question" isn't seen as "<BOM>question".
        String csv = "﻿question,answer\nQ1,A1\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "faqs.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, Pageable.unpaged());

        assertThat(result.summary().totalRows()).isEqualTo(1);
        assertThat(result.rows().items().get(0).question()).isEqualTo("Q1");
    }

    @Test
    void inspectBulkImport_headerCaseAndWhitespaceVariants_stillRecognized() {
        MockMultipartFile file = csvFile(" Question , Answer \nQ1,A1\n");

        FaqInspectionResult result = faqService.inspectBulkImport(file, null, Pageable.unpaged());

        assertThat(result.summary().totalRows()).isEqualTo(1);
        assertThat(result.rows().items().get(0).question()).isEqualTo("Q1");
    }

    @Test
    void bulkImport_headerlessCsv_rejectedInsteadOfTreatingFirstRowAsHeader() {
        MockMultipartFile file = csvFile("How do I reset my password?,Click the forgot password link\n");

        assertThatThrownBy(() -> faqService.bulkImport(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required headers");

        verify(faqRepository, never()).save(any());
        verify(faqRepository, never()).saveAndFlush(any());
    }

    @Test
    void bulkImport_headersOnlyNoDataRows_rejectedWithDescriptiveError() {
        MockMultipartFile file = csvFile("question,answer\n");

        assertThatThrownBy(() -> faqService.bulkImport(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data rows");
    }
}
