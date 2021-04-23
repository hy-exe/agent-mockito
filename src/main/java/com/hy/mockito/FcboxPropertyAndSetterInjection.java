package com.hy.mockito;

import com.hy.mockito.utils.ProxyUtils;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.configuration.injection.MockInjectionStrategy;
import org.mockito.internal.configuration.injection.filter.MockCandidateFilter;
import org.mockito.internal.configuration.injection.filter.NameBasedCandidateFilter;
import org.mockito.internal.configuration.injection.filter.TerminalMockCandidateFilter;
import org.mockito.internal.configuration.injection.filter.TypeBasedCandidateFilter;
import org.mockito.internal.util.collections.ListUtil;
import org.mockito.internal.util.reflection.FieldInitializationReport;
import org.mockito.internal.util.reflection.FieldInitializer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.mockito.internal.exceptions.Reporter.cannotInitializeForInjectMocksAnnotation;
import static org.mockito.internal.exceptions.Reporter.fieldInitialisationThrewException;
import static org.mockito.internal.util.collections.Sets.newMockSafeHashSet;
import static org.mockito.internal.util.reflection.SuperTypesLastSorter.sortSuperTypesLast;

/**
 * @Author: huangyin
 * @Date: 2020/12/11 11:00
 */
public class FcboxPropertyAndSetterInjection extends MockInjectionStrategy {

    private final MockCandidateFilter mockCandidateFilter =
            new TypeBasedCandidateFilter(
                    new NameBasedCandidateFilter(
                            new TerminalMockCandidateFilter()));

    private final ListUtil.Filter<Field> notFinalOrStatic = object -> Modifier.isFinal(object.getModifiers()) || Modifier.isStatic(object.getModifiers());

    @Override
    public boolean processInjection(Field injectMocksField, Object injectMocksFieldOwner, Set<Object> mockCandidates) {
        FieldInitializationReport report = initializeInjectMocksField(injectMocksField, injectMocksFieldOwner);

        // for each field in the class hierarchy
        boolean injectionOccurred = false;
        Class<?> fieldClass = report.fieldClass();
        Object fieldInstanceNeedingInjection = report.fieldInstance();
        // TODO: 如果是代理对象，替换成真实对象 chnage to targetInstance
        Object originalFieldInstance;
        try {
            originalFieldInstance = ProxyUtils.getTarget(fieldInstanceNeedingInjection);
        } catch (Exception e) {
            e.printStackTrace();
            originalFieldInstance = fieldInstanceNeedingInjection;
        }
        while (fieldClass != Object.class) {
            injectionOccurred |= injectMockCandidates(fieldClass, originalFieldInstance, newMockSafeHashSet(mockCandidates));
            fieldClass = fieldClass.getSuperclass();
        }
        return injectionOccurred;
    }

    private FieldInitializationReport initializeInjectMocksField(Field field, Object fieldOwner) {
        try {
            return new FieldInitializer(fieldOwner, field).initialize();
        } catch (MockitoException e) {
            if (e.getCause() instanceof InvocationTargetException) {
                Throwable realCause = e.getCause().getCause();
                throw fieldInitialisationThrewException(field, realCause);
            }
            throw cannotInitializeForInjectMocksAnnotation(field.getName(), e.getMessage());
        }
    }

    private boolean injectMockCandidates(Class<?> awaitingInjectionClazz, Object injectee, Set<Object> mocks) {
        boolean injectionOccurred;
        List<Field> orderedCandidateInjecteeFields = orderedInstanceFieldsFrom(awaitingInjectionClazz);
        // pass 1
        injectionOccurred = injectMockCandidatesOnFields(mocks, injectee, false, orderedCandidateInjecteeFields);
        // pass 2
        injectionOccurred |= injectMockCandidatesOnFields(mocks, injectee, injectionOccurred, orderedCandidateInjecteeFields);
        return injectionOccurred;
    }

    private boolean injectMockCandidatesOnFields(Set<Object> mocks,
                                                 Object injectee,
                                                 boolean injectionOccurred,
                                                 List<Field> orderedCandidateInjecteeFields) {
        for (Iterator<Field> it = orderedCandidateInjecteeFields.iterator(); it.hasNext(); ) {
            Field candidateField = it.next();
            Object injected = mockCandidateFilter.filterCandidate(mocks, candidateField, orderedCandidateInjecteeFields, injectee)
                    .thenInject();
            if (injected != null) {
                injectionOccurred |= true;
                mocks.remove(injected);
                it.remove();
            }
        }
        return injectionOccurred;
    }

    private List<Field> orderedInstanceFieldsFrom(Class<?> awaitingInjectionClazz) {
        List<Field> declaredFields = Arrays.asList(awaitingInjectionClazz.getDeclaredFields());
        declaredFields = ListUtil.filter(declaredFields, notFinalOrStatic);

        return sortSuperTypesLast(declaredFields);
    }

}
