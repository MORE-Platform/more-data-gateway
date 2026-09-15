package io.redlink.more.data.service.milestone;

import io.redlink.more.data.model.ParticipantMilestone;
import io.redlink.more.data.repository.ParticipantMilestoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantMilestoneServiceTest {

    @Mock
    private ParticipantMilestoneRepository repository;

    private ParticipantMilestoneService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantMilestoneService(repository);
    }

    @Test
    void findParticipantMilestone_returns_milestone_when_present() {
        long studyId = 1L;
        int participantId = 101;
        int milestoneId = 1;
        Instant dateTime = Instant.parse("2024-06-15T14:00:00Z");
        ParticipantMilestone milestone = new ParticipantMilestone(
                studyId, participantId, milestoneId, 1, dateTime, dateTime, dateTime, "Baseline");

        when(repository.getByIds(studyId, participantId, milestoneId))
                .thenReturn(Optional.of(milestone));

        Optional<ParticipantMilestone> result = service.findParticipantMilestone(studyId, participantId, milestoneId);

        assertThat(result).isPresent();
        assertThat(result.get().milestoneId()).isEqualTo(milestoneId);
        assertThat(result.get().milestoneName()).isEqualTo("Baseline");
        verify(repository).getByIds(studyId, participantId, milestoneId);
    }

    @Test
    void findParticipantMilestone_returns_empty_when_not_found() {
        long studyId = 1L;
        int participantId = 101;
        int milestoneId = 1;

        when(repository.getByIds(studyId, participantId, milestoneId))
                .thenReturn(Optional.empty());

        Optional<ParticipantMilestone> result = service.findParticipantMilestone(studyId, participantId, milestoneId);

        assertThat(result).isEmpty();
        verify(repository).getByIds(studyId, participantId, milestoneId);
    }

    @Test
    void listParticipantMilestones_returns_all_milestones_for_participant() {
        long studyId = 1L;
        int participantId = 101;
        Instant now = Instant.now();

        List<ParticipantMilestone> milestones = List.of(
                new ParticipantMilestone(studyId, participantId, 1, 1, now, now, now, "Baseline"),
                new ParticipantMilestone(studyId, participantId, 2, 2, now.plusSeconds(86400), now, now, "Follow-up")
        );

        when(repository.listByParticipant(studyId, participantId))
                .thenReturn(milestones);

        List<ParticipantMilestone> result = service.listParticipantMilestones(studyId, participantId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).milestoneName()).isEqualTo("Baseline");
        assertThat(result.get(1).milestoneName()).isEqualTo("Follow-up");
        verify(repository).listByParticipant(studyId, participantId);
    }

    @Test
    void listParticipantMilestones_returns_empty_when_no_milestones() {
        long studyId = 1L;
        int participantId = 101;

        when(repository.listByParticipant(studyId, participantId))
                .thenReturn(List.of());

        List<ParticipantMilestone> result = service.listParticipantMilestones(studyId, participantId);

        assertThat(result).isEmpty();
        verify(repository).listByParticipant(studyId, participantId);
    }
}
