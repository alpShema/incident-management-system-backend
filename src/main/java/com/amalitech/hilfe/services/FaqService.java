package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateFaqRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;
    private final EmbeddingService embeddingService;

    @Transactional
    public FaqResponse createFaq(CreateFaqRequest request) {
        Faq faq = Faq.builder()
                .id(UUID.randomUUID().toString())
                .question(sanitize(request.question()))
                .answer(sanitize(request.answer()))
                .active(true)
                .build();

        faq = faqRepository.save(faq);
        embedAndStore(faq);
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
        if (reEmbed) embedAndStore(faq);
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

    public PageResponse<FaqResponse> listFaqs(Boolean active, Pageable pageable) {
        return PageResponse.from(
                faqRepository.findAllFiltered(active, pageable).map(FaqResponse::from)
        );
    }

    private void embedAndStore(Faq faq) {
        try {
            float[] vector = embeddingService.embed(faq.getQuestion() + " " + faq.getAnswer());
            if (vector != null && vector.length > 0) {
                String literal = EmbeddingService.toVectorLiteral(vector);
                faqRepository.updateEmbedding(faq.getId(), literal);
                log.debug("Embedding stored for FAQ {}", faq.getId());
            } else {
                log.debug("No embedding generated for FAQ {} (stub mode)", faq.getId());
            }
        } catch (ServiceUnavailableException e) {
            log.warn("Embedding unavailable for FAQ {} — saved without vector, semantic search will not match it: {}", faq.getId(), e.getMessage());
        }
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
