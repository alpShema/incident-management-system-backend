package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateLocationRequest;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.dto.UpdateLocationRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.LocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock LocationRepository locationRepository;
    @Mock ActivityLogService activityLogService;
    @InjectMocks LocationService locationService;

    private Location loc(String id, String name, boolean status) {
        return Location.builder().id(id).name(name).status(status).build();
    }

    @Test
    void listLocations_noNameFilter_passesNullPattern() {
        when(locationRepository.findFiltered(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(loc("loc-1", "Accra", true))));

        var result = locationService.listLocations(null, null, Pageable.unpaged());

        assertThat(result.items()).hasSize(1).first().extracting(LocationResponse::name).isEqualTo("Accra");
    }

    @Test
    void listLocations_withName_buildsLowercaseLikePattern() {
        when(locationRepository.findFiltered(eq("%accra%"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        locationService.listLocations("Accra", null, Pageable.unpaged());

        verify(locationRepository).findFiltered("%accra%", null, Pageable.unpaged());
    }

    @Test
    void listLocations_withStatusFilter_passesStatus() {
        when(locationRepository.findFiltered(isNull(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        locationService.listLocations(null, true, Pageable.unpaged());

        verify(locationRepository).findFiltered(null, true, Pageable.unpaged());
    }

    @Test
    void getLocation_found_returnsResponse() {
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(loc("loc-1", "Accra", true)));

        LocationResponse response = locationService.getLocation("loc-1");

        assertThat(response.id()).isEqualTo("loc-1");
        assertThat(response.name()).isEqualTo("Accra");
    }

    @Test
    void getLocation_notFound_throws404() {
        when(locationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getLocation("missing"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void createLocation_success_setsStatusTrueAndTrimsName() {
        when(locationRepository.findByNameIgnoreCase("  Accra  ")).thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

        locationService.createLocation(new CreateLocationRequest("  Accra  ", null, null, null, null));

        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Accra");
        assertThat(captor.getValue().getStatus()).isTrue();
    }

    @Test
    void createLocation_withDescription_trimsDescription() {
        when(locationRepository.findByNameIgnoreCase("Accra")).thenReturn(Optional.empty());
        when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

        locationService.createLocation(new CreateLocationRequest("Accra", "  Regional office  ", null, null, null));

        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Regional office");
    }

    @Test
    void createLocation_duplicateName_throws409() {
        when(locationRepository.findByNameIgnoreCase("Accra")).thenReturn(Optional.of(loc("existing", "Accra", true)));

        var request = new CreateLocationRequest("Accra", null, null, null, null);
        assertThatThrownBy(() -> locationService.createLocation(request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateLocation_newName_updatesAndTrims() {
        Location existing = loc("loc-1", "Old", true);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.findByNameIgnoreCase("  New Name  ")).thenReturn(Optional.empty());
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1", new UpdateLocationRequest("  New Name  ", null, null, null, null));

        assertThat(existing.getName()).isEqualTo("New Name");
    }

    @Test
    void updateLocation_duplicateNameDifferentId_throws409() {
        Location existing = loc("loc-1", "Old", true);
        Location conflict = loc("loc-2", "New", true);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.findByNameIgnoreCase("New")).thenReturn(Optional.of(conflict));

        var updateRequest = new UpdateLocationRequest("New", null, null, null, null);
        assertThatThrownBy(() -> locationService.updateLocation("user-1", "loc-1", updateRequest))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateLocation_sameNameSameId_noConflictThrown() {
        Location existing = loc("loc-1", "Accra", true);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.findByNameIgnoreCase("Accra")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1", new UpdateLocationRequest("Accra", null, null, null, null));

        verify(locationRepository).save(existing);
    }

    @Test
    void updateLocation_descriptionUpdate_trimsAndSaves() {
        Location existing = loc("loc-1", "Accra", true);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1", new UpdateLocationRequest(null, "  Branch office  ", null, null, null));

        assertThat(existing.getDescription()).isEqualTo("Branch office");
    }

    @Test
    void updateLocation_notFound_throws404() {
        when(locationRepository.findById("missing")).thenReturn(Optional.empty());

        var updateRequest = new UpdateLocationRequest("X", null, null, null, null);
        assertThatThrownBy(() -> locationService.updateLocation("user-1", "missing", updateRequest))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateLocation_descriptionOnly_logsNoActivity() {
        Location existing = loc("loc-1", "Accra", true);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1", new UpdateLocationRequest(null, "  Branch office  ", null, null, null));

        verifyNoInteractions(activityLogService);
    }

    @Test
    void updateLocation_timezoneChanged_logsTimezoneChangeOnly() {
        Location existing = loc("loc-1", "Accra", true);
        existing.setTimezone("Africa/Accra");
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1", new UpdateLocationRequest(null, null, "Africa/Kigali", null, null));

        verify(activityLogService).logLocationTimezoneChanged("user-1", "loc-1", "Africa/Accra", "Africa/Kigali");
        verify(activityLogService, never()).logLocationBusinessHoursChanged(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateLocation_businessHoursChanged_logsBusinessHoursChangeOnly() {
        Location existing = loc("loc-1", "Accra", true);
        existing.setBusinessHoursStart(LocalTime.of(8, 0));
        existing.setBusinessHoursEnd(LocalTime.of(17, 30));
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1",
                new UpdateLocationRequest(null, null, null, LocalTime.of(9, 0), null));

        verify(activityLogService).logLocationBusinessHoursChanged(
                "user-1", "loc-1", LocalTime.of(8, 0), LocalTime.of(17, 30), LocalTime.of(9, 0), LocalTime.of(17, 30));
        verify(activityLogService, never()).logLocationTimezoneChanged(any(), any(), any(), any());
    }

    @Test
    void updateLocation_timezoneAndHoursChanged_logsBothDistinctly() {
        Location existing = loc("loc-1", "Accra", true);
        existing.setTimezone("Africa/Accra");
        existing.setBusinessHoursStart(LocalTime.of(8, 0));
        existing.setBusinessHoursEnd(LocalTime.of(17, 30));
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1",
                new UpdateLocationRequest(null, null, "Africa/Kigali", LocalTime.of(9, 0), LocalTime.of(18, 0)));

        verify(activityLogService).logLocationTimezoneChanged("user-1", "loc-1", "Africa/Accra", "Africa/Kigali");
        verify(activityLogService).logLocationBusinessHoursChanged(
                "user-1", "loc-1", LocalTime.of(8, 0), LocalTime.of(17, 30), LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    @Test
    void updateLocation_resubmittingSameTimezone_logsNothing() {
        Location existing = loc("loc-1", "Accra", true);
        existing.setTimezone("Africa/Accra");
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(existing));
        when(locationRepository.save(existing)).thenReturn(existing);

        locationService.updateLocation("user-1", "loc-1", new UpdateLocationRequest(null, null, "Africa/Accra", null, null));

        verifyNoInteractions(activityLogService);
    }

    @Test
    void updateStatus_deactivate_setsStatusFalseAndReturns() {
        Location loc = loc("loc-1", "Accra", true);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(loc));
        when(locationRepository.save(loc)).thenReturn(loc);

        LocationResponse response = locationService.updateStatus("loc-1", false);

        assertThat(loc.getStatus()).isFalse();
        assertThat(response).isNotNull();
    }

    @Test
    void updateStatus_activate_setsStatusTrue() {
        Location loc = loc("loc-1", "Accra", false);
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(loc));
        when(locationRepository.save(loc)).thenReturn(loc);

        locationService.updateStatus("loc-1", true);

        assertThat(loc.getStatus()).isTrue();
    }

    @Test
    void updateStatus_alreadyActive_throws409() {
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(loc("loc-1", "Accra", true)));

        assertThatThrownBy(() -> locationService.updateStatus("loc-1", true))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("already active")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateStatus_alreadyInactive_throws409() {
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(loc("loc-1", "Accra", false)));

        assertThatThrownBy(() -> locationService.updateStatus("loc-1", false))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("already inactive")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }
}
