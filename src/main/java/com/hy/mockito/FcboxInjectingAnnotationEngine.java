/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package com.hy.mockito;

import org.mockito.MockitoAnnotations;
import org.mockito.internal.configuration.IndependentAnnotationEngine;
import org.mockito.internal.configuration.SpyAnnotationEngine;
import org.mockito.internal.configuration.injection.ConstructorInjection;
import org.mockito.internal.configuration.injection.MockInjection;
import org.mockito.internal.configuration.injection.MockInjectionStrategy;
import org.mockito.internal.configuration.injection.SpyOnInjectedFieldsHandler;
import org.mockito.internal.configuration.injection.scanner.InjectMocksScanner;
import org.mockito.internal.configuration.injection.scanner.MockScanner;
import org.mockito.plugins.AnnotationEngine;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.internal.util.Checks.checkItemsNotNull;
import static org.mockito.internal.util.Checks.checkNotNull;
import static org.mockito.internal.util.collections.Sets.newMockSafeHashSet;

/**
 * See {@link MockitoAnnotations}
 * @author 003664
 */
public class FcboxInjectingAnnotationEngine implements AnnotationEngine {
    private final AnnotationEngine delegate = new IndependentAnnotationEngine();
    private final AnnotationEngine spyAnnotationEngine = new SpyAnnotationEngine();

    /**
     * Process the fields of the test instance and create Mocks, Spies, Captors and inject them on fields
     * annotated &#64;InjectMocks.
     *
     * <p>
     * This code process the test class and the super classes.
     * <ol>
     * <li>First create Mocks, Spies, Captors.</li>
     * <li>Then try to inject them.</li>
     * </ol>
     *
     * @param clazz        Not used
     * @param testInstance The instance of the test, should not be null.
     * @see AnnotationEngine#process(Class, Object)
     */
    @Override
    public void process(Class<?> clazz, Object testInstance) {
        processIndependentAnnotations(testInstance.getClass(), testInstance);
        processInjectMocks(testInstance.getClass(), testInstance);
    }

    private void processInjectMocks(final Class<?> clazz, final Object testInstance) {
        Class<?> classContext = clazz;
        while (classContext != Object.class) {
            injectMocks(testInstance);
            classContext = classContext.getSuperclass();
        }
    }

    private void processIndependentAnnotations(final Class<?> clazz, final Object testInstance) {
        Class<?> classContext = clazz;
        while (classContext != Object.class) {
            //this will create @Mocks, @Captors, etc:
            delegate.process(classContext, testInstance);
            //this will create @Spies:
            spyAnnotationEngine.process(classContext, testInstance);

            classContext = classContext.getSuperclass();
        }
    }

    /**
     * Initializes mock/spies dependencies for objects annotated with
     * &#064;InjectMocks for given testClassInstance.
     * <p>
     * See examples in javadoc for {@link MockitoAnnotations} class.
     *
     * @param testClassInstance Test class, usually <code>this</code>
     */
    public void injectMocks(final Object testClassInstance) {
        Class<?> clazz = testClassInstance.getClass();
        Set<Field> mockDependentFields = new HashSet<>();
        Set<Object> mocks = newMockSafeHashSet();

        while (clazz != Object.class) {
            new InjectMocksScanner(clazz).addTo(mockDependentFields);
            new MockScanner(testClassInstance, clazz).addPreparedMocks(mocks);
            clazz = clazz.getSuperclass();
        }

        new FcboxOngoingMockInjection(mockDependentFields, testClassInstance)
                .withMocks(mocks)
                .tryConstructorInjection()
                .tryFcboxPropertyOrFieldInjection()
                .handleSpyAnnotation()
                .apply();
    }

    /**
     * @see MockInjection.OngoingMockInjection
     */
    public static class FcboxOngoingMockInjection {
        private final Set<Field> fields = new HashSet<>();
        private final Set<Object> mocks = newMockSafeHashSet();
        private final Object fieldOwner;
        private final MockInjectionStrategy injectionStrategies = MockInjectionStrategy.nop();
        private final MockInjectionStrategy postInjectionStrategies = MockInjectionStrategy.nop();

        private FcboxOngoingMockInjection(Set<Field> fields, Object fieldOwner) {
            this.fieldOwner = checkNotNull(fieldOwner, "fieldOwner");
            this.fields.addAll(checkItemsNotNull(fields, "fields"));
        }

        public FcboxOngoingMockInjection withMocks(Set<Object> mocks) {
            this.mocks.addAll(checkNotNull(mocks, "mocks"));
            return this;
        }

        public FcboxOngoingMockInjection tryConstructorInjection() {
            injectionStrategies.thenTry(new ConstructorInjection());
            return this;
        }

        public FcboxOngoingMockInjection tryFcboxPropertyOrFieldInjection() {
            injectionStrategies.thenTry(new FcboxPropertyAndSetterInjection());
            return this;
        }

        public FcboxOngoingMockInjection handleSpyAnnotation() {
            postInjectionStrategies.thenTry(new SpyOnInjectedFieldsHandler());
            return this;
        }

        public void apply() {
            for (Field field : fields) {
                injectionStrategies.process(field, fieldOwner, mocks);
                postInjectionStrategies.process(field, fieldOwner, mocks);
            }
        }
    }
}