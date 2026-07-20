/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.controller;

import io.redlink.more.data.api.app.v1.webservices.SignupApi;
import io.redlink.more.data.model.Study;
import io.redlink.more.data.service.RegistrationService;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.generic.CollectionTool;
import org.apache.velocity.tools.generic.ComparisonDateTool;
import org.apache.velocity.tools.generic.DisplayTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.apache.velocity.tools.generic.MathTool;
import org.apache.velocity.tools.generic.NumberTool;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.TEXT_HTML_VALUE)
public class SignupApiV1Controller implements SignupApi {
    private final RegistrationService registrationService;

    private final VelocityEngine velocityEngine;

    public SignupApiV1Controller(RegistrationService registrationService) {
        this.registrationService = registrationService;

        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader.classpath.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.setProperty("resource.loaders", "classpath");
        velocityEngine.init();
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getSignupInfo(String token) {

        return registrationService.loadStudyByRegistrationToken(token)
                .map(st -> createStudyRenderer(st, token, ResponseEntity.ok()))
                .orElseGet(() -> create404(token, ResponseEntity.status(HttpStatus.NOT_FOUND)));
    }

    private ResponseEntity<StreamingResponseBody> create404(String token, ResponseEntity.BodyBuilder builder) {
        final Context context = createContext();
        context.put("token", token);
        final String template = ("templates/vm/signup-token-404.html.vm");
        return createHtmlResponse(template, context, builder);
    }

    private ResponseEntity<StreamingResponseBody> createStudyRenderer(Study study, String token, ResponseEntity.BodyBuilder builder) {
        final Context context = createContext();
        context.put("token", token);
        context.put("registrationUrl", ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString());
        context.put("study", study);

        String template;
        try {
            // look for a study-specific template
            template = "templates/vm/signup-study-%d.html.vm".formatted(study.studyId());
            if (!velocityEngine.resourceExists(template)) {
                throw new ResourceNotFoundException(template);
            }
        } catch (ResourceNotFoundException e) {
            // but use the default as fallback
            template = "templates/vm/signup-study-default.html.vm";
        }

        return createHtmlResponse(template, context, builder);
    }

    private Context createContext() {
        VelocityContext context = new VelocityContext();
        context.put("esc", new EscapeTool());
        context.put("date", new ComparisonDateTool());
        context.put("math", new MathTool());
        context.put("number", new NumberTool());
        context.put("collection", new CollectionTool());
        context.put("display", new DisplayTool());
        return context;
    }

    private ResponseEntity<StreamingResponseBody> createHtmlResponse(String template, Context context, ResponseEntity.BodyBuilder builder) {
        return builder
                .contentType(MediaType.TEXT_HTML)
                .body(os -> {
                    try (Writer out = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                        velocityEngine.mergeTemplate(template, "UTF-8" , context, out);
                    }
                });
    }

}
