package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.CreateFaqRequest;
import com.amalitech.hilfe.dto.FaqResponse;
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
    public PageResponse<FaqResponse> faqs(@Argument Boolean active, @Argument PageInput page) {
        return faqService.listFaqs(active, PageInput.toPageable(page));
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('faq.read')")
    public FaqResponse faq(@Argument String id) {
        return faqService.getFaq(id);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.create')")
    public FaqResponse createFaq(@Argument CreateFaqRequest input) {
        return faqService.createFaq(input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.update')")
    public FaqResponse updateFaq(@Argument String id, @Argument UpdateFaqRequest input) {
        return faqService.updateFaq(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.update')")
    public FaqResponse toggleFaqActive(@Argument String id, @Argument boolean active) {
        return faqService.toggleActive(id, active);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('faq.delete')")
    public boolean deleteFaq(@Argument String id) {
        faqService.deleteFaq(id);
        return true;
    }
}
