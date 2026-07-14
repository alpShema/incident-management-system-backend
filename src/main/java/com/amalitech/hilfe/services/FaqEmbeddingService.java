package com.amalitech.hilfe.services;

import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.FaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaqEmbeddingService {

    private final FaqRepository faqRepository;
    private final EmbeddingService embeddingService;

    // Runs on a fresh thread with no inherited transaction context — calling a
    // @Transactional repository method from within TransactionSynchronization.afterCommit()
    // on the original thread binds to the already-committed transaction's leftover
    // resources instead of opening a new one, causing "No active transaction" errors.
    @Async("applicationTaskExecutor")
    public void embedAndStoreAsync(Faq faq) {
        embedAndStore(faq);
    }

    public boolean embedAndStore(Faq faq) {
        log.debug("embedAndStore start | faqId={} | question=\"{}\"", faq.getId(), faq.getQuestion());
        try {
            float[] vector = embeddingService.embed(faq.getQuestion() + " " + faq.getAnswer());
            if (vector != null && vector.length > 0) {
                String literal = EmbeddingService.toVectorLiteral(vector);
                faqRepository.updateEmbedding(faq.getId(), literal);
                log.debug("Embedding stored | faqId={} | dims={}", faq.getId(), vector.length);
                return true;
            } else {
                log.warn("Embedding returned empty vector | faqId={} | stub mode or misconfigured model", faq.getId());
                return false;
            }
        } catch (ServiceUnavailableException e) {
            log.warn("Embedding service unavailable | faqId={} | {}", faq.getId(), e.getMessage());
            return false;
        } catch (DataAccessException e) {
            log.error("Failed to persist embedding | faqId={} | {}", faq.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error embedding FAQ | faqId={} | {}", faq.getId(), e.getMessage(), e);
            return false;
        }
    }
}
