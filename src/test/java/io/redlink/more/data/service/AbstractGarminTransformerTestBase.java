package io.redlink.more.data.service;

import io.redlink.more.data.repository.StudyRepository;
import io.redlink.more.data.transformers.garmin.AbstractGarminTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
abstract class AbstractGarminTransformerTestBase<T extends AbstractGarminTransformer> {

    @Mock
    protected StudyRepository studyRepository;
    protected T transformer;

    protected abstract T createTransformer();

    @BeforeEach
    void baseSetUp() throws Exception {
        transformer = createTransformer();

        Field repoField = AbstractGarminTransformer.class.getDeclaredField("studyRepository");
        repoField.setAccessible(true);
        repoField.set(transformer, studyRepository);

        // default: safe for tests that don't hit it
        lenient().when(studyRepository.getAllParticpantObservationProperties(
                nullable(Long.class), nullable(Integer.class)
        )).thenReturn(Collections.emptyList());
    }
}