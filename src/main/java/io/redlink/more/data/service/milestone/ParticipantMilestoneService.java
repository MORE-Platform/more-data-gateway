package io.redlink.more.data.service.milestone;

import io.redlink.more.data.model.ParticipantMilestone;
import io.redlink.more.data.repository.ParticipantMilestoneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class ParticipantMilestoneService {

    private final ParticipantMilestoneRepository participantMilestoneRepository;

    public ParticipantMilestoneService(ParticipantMilestoneRepository participantMilestoneRepository) {
        this.participantMilestoneRepository = participantMilestoneRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ParticipantMilestone> findParticipantMilestone(long studyId, int participantId, int milestoneId) {
        return participantMilestoneRepository.getByIds(studyId, participantId, milestoneId);
    }

    @Transactional(readOnly = true)
    public List<ParticipantMilestone> listParticipantMilestones(long studyId, int participantId) {
        return participantMilestoneRepository.listByParticipant(studyId, participantId);
    }
}
