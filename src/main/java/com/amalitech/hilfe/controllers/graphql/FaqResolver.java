package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqResponse;
import com.amalitech.hilfe.dto.FaqUpsertResult;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateFaqRequest;
import com.amalitech.hilfe.services.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class FaqResolver {

    private final FaqService faqService;

    @QueryMapping
    @PreAuthorize("hasAuthority('faq.read')")
    public PageResponse<FaqResponse> faqs(@Argument Boolean active, @Argument String search, @Argument PageInput page) {
        return faqService.listFaqs(active, search, PageInput.toPageable(page));
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('faq.read')")
    public FaqResponse faq(@Argument String id) {
        return faqService.getFaq(id);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.create')")
    public FaqUpsertResult createFaq(@Argument CreateFaqRequest input) {
        FaqUpsertResult result = faqService.createFaq(input);
        GraphQlResponseMessage.set(result.created() ? "FAQ created successfully" : "FAQ updated successfully");
        return result;
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.update')")
    public FaqResponse updateFaq(@Argument String id, @Argument UpdateFaqRequest input) {
        GraphQlResponseMessage.set("FAQ updated successfully");
        return faqService.updateFaq(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.update')")
    public FaqResponse toggleFaqActive(@Argument String id, @Argument boolean active) {
        GraphQlResponseMessage.set(active ? "FAQ activated successfully" : "FAQ deactivated successfully");
        return faqService.toggleActive(id, active);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.delete')")
    public boolean deleteFaq(@Argument String id) {
        faqService.deleteFaq(id);
        GraphQlResponseMessage.set("FAQ deleted successfully");
        return true;
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.update')")
    public int reEmbedFaqs() {
        int count = faqService.reEmbedAll();
        GraphQlResponseMessage.set("FAQs re-embedded successfully");
        return count;
    }
}
