package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.model.ParticipantMilestoneDTO;
import io.redlink.more.data.api.app.v1.webservices.MilestonesApi;
import io.redlink.more.data.configuration.AuthenticationFacade;
import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.model.RoutingInfo;
import io.redlink.more.data.service.GatewayUserDetailService;
import io.redlink.more.data.service.milestone.ParticipantMilestoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class MilestonesApiV1Controller implements MilestonesApi {
    private static final Logger LOG = LoggerFactory.getLogger(MilestonesApiV1Controller.class);
    private final AuthenticationFacade authenticationFacade;
    private final ParticipantMilestoneService service;

    public MilestonesApiV1Controller(AuthenticationFacade authenticationFacade, ParticipantMilestoneService service) {
        this.authenticationFacade = authenticationFacade;
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ParticipantMilestoneDTO>> listMilestones() {
        final GatewayUserDetails userDetails = authenticationFacade.assertAuthority(GatewayUserDetailService.APP_ROLE);
        final RoutingInfo routingInfo = userDetails.getRoutingInfo();
        final List<ParticipantMilestoneDTO> milestones = service.listParticipantMilestones(routingInfo.studyId(), routingInfo.participantId())
                .stream()
                .map(pm -> new ParticipantMilestoneDTO()
                        .participantMilestoneId(pm.participantMilestoneId())
                        .milestoneId(pm.milestoneId())
                        .name(pm.milestoneName())
                        .dateTime(pm.dateTime()))
                .toList();
        return ResponseEntity.ok(milestones);
    }
}
