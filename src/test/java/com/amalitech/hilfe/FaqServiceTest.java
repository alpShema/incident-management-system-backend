package com.amalitech.hilfe;

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
        when(faqRepository.findAllFiltered(isNull(), eq("shipping"), any(Pageable.class)))
                .thenReturn(pageOf(faq("1", "Shipping policy", "We ship worldwide")));

        PageResponse<FaqResponse> result = faqService.listFaqs(null, "  shipping  ", Pageable.unpaged());

        verify(faqRepository).findAllFiltered(null, "shipping", Pageable.unpaged());
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).question()).isEqualTo("Shipping policy");
    }

    @Test
    void listFaqs_withSearchAndActiveFilter_bothPassedToRepository() {
        when(faqRepository.findAllFiltered(eq(true), eq("refund"), any(Pageable.class)))
                .thenReturn(pageOf(faq("2", "Refund policy", "30 day returns")));

        PageResponse<FaqResponse> result = faqService.listFaqs(true, "refund", Pageable.unpaged());

        verify(faqRepository).findAllFiltered(true, "refund", Pageable.unpaged());
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void listFaqs_noMatchingSearch_returnsEmptyList() {
        when(faqRepository.findAllFiltered(isNull(), eq("zzznomatch"), any(Pageable.class)))
                .thenReturn(pageOf());

        PageResponse<FaqResponse> result = faqService.listFaqs(null, "zzznomatch", Pageable.unpaged());

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
