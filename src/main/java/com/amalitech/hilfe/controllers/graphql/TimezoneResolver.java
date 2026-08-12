package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import com.amalitech.hilfe.services.TimezoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class TimezoneResolver {

    private final TimezoneService timezoneService;

    @QueryMapping
    public List<TimezoneOptionResponse> timezones(@Argument String query) {
        return timezoneService.listTimezones(query);
    }
}
