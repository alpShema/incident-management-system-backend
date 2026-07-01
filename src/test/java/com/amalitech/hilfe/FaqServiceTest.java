package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.FaqBulkUploadResult;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.FaqRepository;
import com.amalitech.hilfe.services.FaqEmbeddingService;
import com.amalitech.hilfe.services.FaqService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> {
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
}
