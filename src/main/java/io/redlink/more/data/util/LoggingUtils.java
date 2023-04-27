/*
 * Copyright (c) 2023 Redlink GmbH.
 */
package io.redlink.more.data.util;

import io.redlink.more.data.model.RoutingInfo;
import java.util.Map;
import org.slf4j.MDC;

public final class LoggingUtils {

    private LoggingUtils() {
    }

    public static LoggingContext createContext() {
        return new LoggingContext();
    }

    public static LoggingContext createContext(RoutingInfo routingInfo) {
        final LoggingContext ctx = new LoggingContext();
        ctx.putRoutingInfo(routingInfo);
        return ctx;
    }

    public static class LoggingContext implements AutoCloseable {

        public static final String MDC_KEY_STUDY = "studyId";
        public static final String MDC_KEY_PARTICIPANT = "participantId";
        public static final String MDC_KEY_ACTION = "actionId";
        public static final String MDC_KEY_ACTION_TYPE = "actionType";
        public static final String MDC_KEY_STUDYGROUP = "studyGroupId";
        public static final String MDC_KEY_INTERVENTION = "interventionId";

        private final Map<String, String> oldMap;

        public LoggingContext() {
            oldMap = MDC.getCopyOfContextMap();
        }


        public void putStudy(Long study) {
            MDC.put(MDC_KEY_STUDY, String.valueOf(study));
        }


        public void putParticipant(Integer participantId) {
            MDC.put(MDC_KEY_PARTICIPANT, String.valueOf(participantId));
        }


        public void putStudyGroup(Integer studyGroupId) {
            MDC.put(MDC_KEY_STUDYGROUP, String.valueOf(studyGroupId));
        }


        public void putAction(Integer actionId, String actionType) {
            MDC.put(MDC_KEY_ACTION, String.valueOf(actionId));
            MDC.put(MDC_KEY_ACTION_TYPE, actionType);
        }


        public void putIntervention(Integer interventionId) {
            MDC.put(MDC_KEY_INTERVENTION, String.valueOf(interventionId));
        }

        @Override
        public void close() {
            if (oldMap != null) {
                MDC.setContextMap(oldMap);
            } else {
                MDC.clear();
            }
        }

        public void putRoutingInfo(RoutingInfo routingInfo) {
            putStudy(routingInfo.studyId());
            putParticipant(routingInfo.participantId());
            routingInfo.studyGroupId().ifPresent(this::putStudyGroup);
        }
    }

}
