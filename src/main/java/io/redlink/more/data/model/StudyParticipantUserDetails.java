package io.redlink.more.data.model;

import io.redlink.more.data.exception.NotAuthorizedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

public class StudyParticipantUserDetails implements UserDetails {

    private final StudyParticipantReference reference;
    private final Collection<? extends GrantedAuthority> authorities;

    // You can add more fields as needed (email, language, etc.)

    public StudyParticipantUserDetails(long studyId, int participantId,
                                       Collection<? extends GrantedAuthority> authorities) {

        this.reference = new StudyParticipantReference(studyId, participantId);
        this.authorities = authorities != null ? Collections.unmodifiableCollection(authorities) : Collections.emptyList();
    }

    // ==================== UserDetails methods ====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "unused";
    }

    @Override
    public String getUsername() {
        return String.format("study_%s-participant_%s", reference.studyId(), reference.participantId());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ==================== Custom getters ====================

    public StudyParticipantReference getStudyParticipantReference() {
        return reference;
    }

    // Optional: Helper method to get current user details easily
    public static StudyParticipantUserDetails getCurrent() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof StudyParticipantUserDetails details) {
            return details;
        }
        throw new NotAuthorizedException("This user is not authorized!");
    }

    public record StudyParticipantReference(
            long studyId,
            int participantId
    ) implements Serializable {
    }

    ;

}