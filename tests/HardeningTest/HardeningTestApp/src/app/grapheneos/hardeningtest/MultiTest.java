package app.grapheneos.hardeningtest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MultiTest {
    MultiTests.Type type();

    int allowedReturnCode() default 0;

    int allowedReturnCodeIsolated() default 0;

    int blockedReturnCode() default Errno.EACCES;

    int blockedReturnCodeIsolated() default Errno.EACCES;

    int alwaysDeniedMinSdk() default 10000;

    int alwaysDeniedMinSdkIsolated() default 10000;

    boolean skipAllowedTest() default false;
}
