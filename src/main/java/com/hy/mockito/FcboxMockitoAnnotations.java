package com.hy.mockito;

import org.mockito.exceptions.base.MockitoException;

/**
 * @Author: huangyin
 * @Date: 2020/12/10 17:37
 */
public class FcboxMockitoAnnotations {

    public static void initMocks(Object testClass) {
        if (testClass == null) {
            throw new MockitoException("testClass cannot be null. For info how to use @Mock annotations see examples in javadoc for MockitoAnnotations class");
        }

        new FcboxInjectingAnnotationEngine().process(testClass.getClass(), testClass);
    }

}