package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ParticipantMilestoneDTO;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.ParticipantMilestone;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.milestone.ParticipantMilestoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilestonesApiV1ControllerTest {

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private ParticipantMilestoneService service;

    private MilestonesApiV1Controller controller;

    @BeforeEach
    void setUp() {
        controller = new MilestonesApiV1Controller(authenticationFacade, service);
    }

    @Test
    void listMilestones_returns_ordered_milestones_for_authenticated_user() {
        long studyId = 1L;
        int participantId = 101;
        Instant now = Instant.now();

        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);

        List<ParticipantMilestone> milestones = List.of(
                new ParticipantMilestone(studyId, participantId, 1, 1, now, now, now, "Baseline"),
                new ParticipantMilestone(studyId, participantId, 2, 2, now.plusSeconds(86400), now, now, "Follow-up")
        );

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);
        when(service.listParticipantMilestones(studyId, participantId))
                .thenReturn(milestones);

        ResponseEntity<List<ParticipantMilestoneDTO>> response = controller.listMilestones();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);

        List<ParticipantMilestoneDTO> body = response.getBody();
        assertThat(body.get(0).getName()).isEqualTo("Baseline");
        assertThat(body.get(0).getMilestoneId()).isEqualTo(1);
        assertThat(body.get(0).getParticipantMilestoneId()).isEqualTo(1);
        assertThat(body.get(0).getDateTime()).isEqualTo(now);

        assertThat(body.get(1).getName()).isEqualTo("Follow-up");
        assertThat(body.get(1).getMilestoneId()).isEqualTo(2);
        assertThat(body.get(1).getParticipantMilestoneId()).isEqualTo(2);

        verify(authenticationFacade).assertAuthority(GatewayUserDetailService.APP_ROLE);
        verify(service).listParticipantMilestones(studyId, participantId);
    }

    @Test
    void listMilestones_returns_empty_list_when_no_milestones() {
        long studyId = 1L;
        int participantId = 101;

        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("user", "pass", Set.of(), routingInfo);

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);
        when(service.listParticipantMilestones(studyId, participantId))
                .thenReturn(List.of());

        ResponseEntity<List<ParticipantMilestoneDTO>> response = controller.listMilestones();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();

        verify(authenticationFacade).assertAuthority(GatewayUserDetailService.APP_ROLE);
        verify(service).listParticipantMilestones(studyId, participantId);
    }

    @Test
    void listMilestones_maps_all_fields_correctly() {
        long studyId = 42L;
        int participantId = 999;
        Instant dateTime = Instant.parse("2024-06-15T14:30:45Z");

        RoutingInfo routingInfo = new RoutingInfo(studyId, participantId, OptionalInt.empty(), Set.of(), true, true);
        GatewayUserDetails userDetails = new GatewayUserDetails("testuser", "pass", Set.of(), routingInfo);

        ParticipantMilestone milestone = new ParticipantMilestone(
                studyId, participantId, 5, 123, dateTime, dateTime.minusSeconds(3600), dateTime.minusSeconds(1800), "Mid-Study");

        when(authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE))
                .thenReturn(userDetails);
        when(service.listParticipantMilestones(studyId, participantId))
                .thenReturn(List.of(milestone));

        ResponseEntity<List<ParticipantMilestoneDTO>> response = controller.listMilestones();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ParticipantMilestoneDTO> body = response.getBody();

        ParticipantMilestoneDTO dto = body.get(0);
        assertThat(dto.getParticipantMilestoneId()).isEqualTo(123);
        assertThat(dto.getMilestoneId()).isEqualTo(5);
        assertThat(dto.getName()).isEqualTo("Mid-Study");
        assertThat(dto.getDateTime()).isEqualTo(dateTime);
    }
}
