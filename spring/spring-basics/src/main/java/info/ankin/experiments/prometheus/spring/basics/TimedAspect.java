/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.ankin.experiments.prometheus.spring.basics;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.Nullable;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * <p>
 * AspectJ aspect for intercepting types or methods annotated with
 * {@link Timed @Timed}.<br>
 * The aspect supports programmatic customizations through constructor-injectable custom
 * logic.
 * </p>
 * <p>
 * You might want to add tags programmatically to the {@link Timer}.<br>
 * In this case, the tags provider function
 * (<code>Function&lt;ProceedingJoinPoint, Iterable&lt;Tag&gt;&gt;</code>) can help. It
 * receives a {@link ProceedingJoinPoint} and returns the {@link Tag}s that will be
 * attached to the {@link Timer}.
 * </p>
 * <p>
 * You might also want to skip the {@link Timer} creation programmatically.<br>
 * One use-case can be having another component in your application that already processes
 * the {@link Timed @Timed} annotation in some cases so that {@code TimedAspect} should
 * not intercept these methods. E.g.: Spring Boot does this for its controllers. By using
 * the skip predicate (<code>Predicate&lt;ProceedingJoinPoint&gt;</code>) you can tell the
 * {@code TimedAspect} when not to create a {@link Timer}.
 *
 * Here's an example to disable {@link Timer} creation for Spring controllers:
 * </p>
 * <pre>
 * &#064;Bean
 * public TimedAspect timedAspect(MeterRegistry meterRegistry) {
 *     return new TimedAspect(meterRegistry, this::skipControllers);
 * }
 *
 * private boolean skipControllers(ProceedingJoinPoint pjp) {
 *     Class&lt;?&gt; targetClass = pjp.getTarget().getClass();
 *     return targetClass.isAnnotationPresent(RestController.class) || targetClass.isAnnotationPresent(Controller.class);
 * }
 * </pre>
 *
 * @author David J. M. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nejc Korasa
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
@Aspect
@NonNullApi
@Incubating(since = "1.0.0")
public class TimedAspect {

    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    public static final String DEFAULT_METRIC_NAME = "method.timed";

    public static final String DEFAULT_EXCEPTION_TAG_VALUE = "none";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry registry;

    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    private final Predicate<ProceedingJoinPoint> shouldSkip;

    /**
     * Creates a {@code TimedAspect} instance with {@link Metrics#globalRegistry}.
     *
     * @since 1.2.0
     */
    public TimedAspect() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}.
     * @param registry Where we're going to register metrics.
     */
    public TimedAspect(MeterRegistry registry) {
        this(registry, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and tags
     * provider function.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this(registry, tagsBasedOnJoinPoint, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and skip
     * predicate.
     * @param registry Where we're going to register metrics.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or
     * not.
     * @since 1.7.0
     */
    public TimedAspect(MeterRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry, pjp -> Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(), "method",
                pjp.getStaticPart().getSignature().getName()), shouldSkip);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}, tags
     * provider function and skip predicate.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or
     * not.
     * @since 1.7.0
     */
    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint,
                       Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
        this.shouldSkip = shouldSkip;
    }

    @Around("@within(io.micrometer.core.annotation.Timed)")
    @Nullable
    public Object timedClass(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isAnnotationPresent(Timed.class)) {
            declaringClass = pjp.getTarget().getClass();
        }
        Timed timed = declaringClass.getAnnotation(Timed.class);

        return perform(pjp, timed, method);
    }

    @Around("execution (@io.micrometer.core.annotation.Timed * *.*(..))")
    @Nullable
    public Object timedMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        if (timed == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            timed = method.getAnnotation(Timed.class);
        }

        return perform(pjp, timed, method);
    }

    private Object perform(ProceedingJoinPoint pjp, Timed timed, Method method) throws Throwable {
        final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();
        final StopType stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType())
                ? StopType.ASYNC
                : Publisher.class.isAssignableFrom(method.getReturnType())
                ? StopType.RX
                : StopType.NONE;

        if (!timed.longTask()) {
            return processWithTimer(pjp, timed, metricName, stopWhenCompleted);
        }
        else {
            return processWithLongTaskTimer(pjp, timed, metricName, stopWhenCompleted);
        }
    }

    private Object processWithTimer(ProceedingJoinPoint pjp, Timed timed, String metricName, StopType stopType)
            throws Throwable {


        if (stopType == StopType.RX) {
            Publisher<?> publisher = (Publisher<?>) pjp.proceed();
            Publisher<?> wrapped = new TimingPublisher<>(publisher, () -> {
                Timer.Sample sample = Timer.start(registry);
                return t -> record(pjp, timed, metricName, sample, t == null ? DEFAULT_EXCEPTION_TAG_VALUE : t.getClass().getSimpleName());
            });
            //noinspection ReactiveStreamsUnusedPublisher
            return publisher instanceof Mono ? Mono.from(wrapped) : publisher;
        }

        Timer.Sample sample = Timer.start(registry);

        if (stopType == StopType.ASYNC) {
            try {
                return ((CompletionStage<?>) pjp.proceed()).whenComplete(
                        (result, throwable) -> record(pjp, timed, metricName, sample, getExceptionTag(throwable)));
            }
            catch (Exception ex) {
                record(pjp, timed, metricName, sample, ex.getClass().getSimpleName());
                throw ex;
            }
        }

        String exceptionClass = DEFAULT_EXCEPTION_TAG_VALUE;
        try {
            return pjp.proceed();
        }
        catch (Exception ex) {
            exceptionClass = ex.getClass().getSimpleName();
            throw ex;
        }
        finally {
            record(pjp, timed, metricName, sample, exceptionClass);
        }
    }

    private void record(ProceedingJoinPoint pjp, Timed timed, String metricName, Timer.Sample sample,
            String exceptionClass) {
        try {
            sample.stop(
                    Timer.builder(metricName).description(timed.description().isEmpty() ? null : timed.description())
                            .tags(timed.extraTags()).tags(EXCEPTION_TAG, exceptionClass)
                            .tags(tagsBasedOnJoinPoint.apply(pjp)).publishPercentileHistogram(timed.histogram())
                            .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
                            .register(registry));
        }
        catch (Exception e) {
            // ignoring on purpose
        }
    }

    private String getExceptionTag(Throwable throwable) {

        // this is the throwable passed in by CompletableFuture#whenComplete, so it can be null
        //noinspection ConstantConditions
        if (throwable == null) {
            return DEFAULT_EXCEPTION_TAG_VALUE;
        }

        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getCause().getClass().getSimpleName();
    }

    private Object processWithLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName, StopType stopType) throws Throwable {
        Optional<LongTaskTimer> timer = buildLongTaskTimer(pjp, timed, metricName);

        if (stopType == StopType.RX) {
            Publisher<?> publisher = (Publisher<?>) pjp.proceed();
            Publisher<?> wrapped = new TimingPublisher<>(publisher,
                    () -> {
                        Optional<LongTaskTimer.Sample> sample = timer.map(LongTaskTimer::start);
                        return throwable -> sample.ifPresent(this::stopTimer);
                    });
            return publisher instanceof Mono ? Mono.from(wrapped) : wrapped;
        }

        Optional<LongTaskTimer.Sample> sample = timer.map(LongTaskTimer::start);
        if (stopType == StopType.ASYNC) {
            try {
                return ((CompletionStage<?>) pjp.proceed())
                        .whenComplete((result, throwable) -> sample.ifPresent(this::stopTimer));
            }
            catch (Exception ex) {
                sample.ifPresent(this::stopTimer);
                throw ex;
            }
        }

        try {
            return pjp.proceed();
        }
        finally {
            sample.ifPresent(this::stopTimer);
        }
    }

    private void stopTimer(LongTaskTimer.Sample sample) {
        try {
            sample.stop();
        }
        catch (Exception e) {
            // ignoring on purpose
        }
    }

    /**
     * Secure long task timer creation - it should not disrupt the application flow in
     * case of exception
     */
    private Optional<LongTaskTimer> buildLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) {
        try {
            return Optional.of(LongTaskTimer.builder(metricName)
                    .description(timed.description().isEmpty() ? null : timed.description()).tags(timed.extraTags())
                    .tags(tagsBasedOnJoinPoint.apply(pjp)).register(registry));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    enum StopType {
        NONE, // synchronous method call
        ASYNC, // CompletableFuture
        RX, // publisher
    }

    @SuppressWarnings("ReactiveStreamsPublisherImplementation")
    @AllArgsConstructor
    public static class TimingPublisher<T> implements Publisher<T> {
        private final Publisher<T> delegate;
        private final Supplier<Consumer<Throwable>> sampler;

        @Override
        public void subscribe(Subscriber<? super T> s) {
            delegate.subscribe(new TimingSubscriber<>(s, sampler.get()));
        }

        @SuppressWarnings("ReactiveStreamsSubscriberImplementation")
        @AllArgsConstructor
        static class TimingSubscriber<S> implements Subscriber<S> {
            private final Subscriber<S> delegate;
            private final Consumer<Throwable> callback;

            @Override
            public void onSubscribe(Subscription s) {
                delegate.onSubscribe(s);
            }

            @Override
            public void onNext(S s) {
                delegate.onNext(s);
            }

            @Override
            public void onError(Throwable t) {
                delegate.onError(t);
                callback.accept(t);
            }

            @Override
            public void onComplete() {
                delegate.onComplete();
                callback.accept(null);
            }
        }
    }
}
