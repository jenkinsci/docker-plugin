package com.nirima.jenkins.plugins.docker.strategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import hudson.model.Executor;
import hudson.model.OneOffExecutor;
import hudson.model.Queue.FlyweightTask;
import hudson.model.Queue.Task;
import io.jenkins.docker.DockerComputer;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.junit.Test;

public class DockerOnceRetentionStrategyTest {
    @Test
    public void isAcceptingTasksGivenTerminateOnceDoneIsFalseThenReturnsTrue() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(0);
        instance.setTerminateOnceDone(false);
        final DockerComputer mockComputer = mock(DockerComputer.class);

        // When
        final boolean actual = instance.isAcceptingTasks(mockComputer);

        // Then
        assertThat(actual, equalTo(true));
    }

    @Test
    public void isAcceptingTasksGivenTerminateOnceDoneIsTrueThenReturnsFalse() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(0);
        instance.setTerminateOnceDone(true);
        final DockerComputer mockComputer = mock(DockerComputer.class);

        // When
        final boolean actual = instance.isAcceptingTasks(mockComputer);

        // Then
        assertThat(actual, equalTo(false));
    }

    @Test
    public void constructorGivenNoDataThenDefaults() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(0);

        // When
        final int actualIdleMinutes = instance.getIdleMinutes();
        final int actualNumberOfTasksInProgress = instance.getNumberOfTasksInProgress();
        final boolean actualTerminateOnceDone = instance.getTerminateOnceDone();

        // Then
        assertThat(actualIdleMinutes, equalTo(10)); // the default if we have no configuration
        assertThat(actualNumberOfTasksInProgress, equalTo(0));
        assertThat(actualTerminateOnceDone, equalTo(false));
    }

    @Test
    public void settersSetData() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(0);

        // When
        instance.setNumberOfTasksInProgress(123);
        instance.setTerminateOnceDone(true);
        final int actual1NumberOfTasksInProgress = instance.getNumberOfTasksInProgress();
        final boolean actual1TerminateOnceDone = instance.getTerminateOnceDone();
        instance.setNumberOfTasksInProgress(0);
        instance.setTerminateOnceDone(false);
        final int actual2NumberOfTasksInProgress = instance.getNumberOfTasksInProgress();
        final boolean actual2TerminateOnceDone = instance.getTerminateOnceDone();
        instance.setNumberOfTasksInProgress(null);
        instance.setTerminateOnceDone(null);
        final int actual3NumberOfTasksInProgress = instance.getNumberOfTasksInProgress();
        final boolean actual3TerminateOnceDone = instance.getTerminateOnceDone();

        // Then
        assertThat(actual1NumberOfTasksInProgress, equalTo(123));
        assertThat(actual1TerminateOnceDone, equalTo(true));
        assertThat(actual2NumberOfTasksInProgress, equalTo(0));
        assertThat(actual2TerminateOnceDone, equalTo(false));
        assertThat(actual3NumberOfTasksInProgress, equalTo(0));
        assertThat(actual3TerminateOnceDone, equalTo(false));
    }

    @Test
    public void checkGivenNonIdleComputerThenReturnsMaxIdleTime() {
        // Given
        final ClassUnderTest instance1 = new ClassUnderTest(1);
        final ClassUnderTest instance123 = new ClassUnderTest(123);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(instance1.mock.computerIsIdle(mockComputer)).thenReturn(false);
        when(instance123.mock.computerIsIdle(mockComputer)).thenReturn(false);

        // When
        final long actual1 = instance1.check(mockComputer);
        final long actual123 = instance123.check(mockComputer);

        // Then
        assertThat(actual1, equalTo(1L));
        assertThat(actual123, equalTo(123L));
        verify(instance123.mock, never()).terminateContainer(mockComputer);
    }

    @Test
    public void checkGivenRecentlyIdleComputerThenReturnsMinutesUntilNextCheck() {
        // Given
        final long idleMinutes = 123;
        final long msInAminute = 60L * 1000L;
        final long idleMilliseconds = idleMinutes * msInAminute;
        final long idleStartTime = 1000000000000L;
        final ClassUnderTest instance = new ClassUnderTest((int) idleMinutes);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(instance.mock.computerIsIdle(mockComputer)).thenReturn(true);
        when(instance.mock.computerIdleStartMilliseconds(mockComputer)).thenReturn(idleStartTime);
        final long clockWithAllIdleTimeToRun = idleStartTime;
        final long clockWithJustOverOneMinuteToGo = idleStartTime + idleMilliseconds - msInAminute - 1;
        final long clockWithExactlyOneMinuteToGo = idleStartTime + idleMilliseconds - msInAminute;
        final long clockWithOneMillisecondToGo = idleStartTime + idleMilliseconds - 1;
        when(instance.mock.currentMilliseconds())
                .thenReturn(
                        clockWithAllIdleTimeToRun,
                        clockWithJustOverOneMinuteToGo,
                        clockWithExactlyOneMinuteToGo,
                        clockWithOneMillisecondToGo);

        // When
        final long actual1 = instance.check(mockComputer);
        final long actual2 = instance.check(mockComputer);
        final long actual3 = instance.check(mockComputer);
        final long actual4 = instance.check(mockComputer);

        // Then
        assertThat(actual1, equalTo(idleMinutes));
        assertThat(actual2, equalTo(2L));
        assertThat(actual3, equalTo(1L));
        assertThat(actual4, equalTo(1L));
        verify(instance.mock, never()).terminateContainer(mockComputer);
    }

    @Test
    public void checkGivenVeryIdleComputerThenTerminates() {
        // Given
        final long idleMinutes = 234;
        final long msInAminute = 60L * 1000L;
        final long idleMilliseconds = idleMinutes * msInAminute;
        final long idleStartTime = 2000000000000L;
        final ClassUnderTest instance = new ClassUnderTest((int) idleMinutes);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(instance.mock.computerIsIdle(mockComputer)).thenReturn(true);
        when(instance.mock.computerIdleStartMilliseconds(mockComputer)).thenReturn(idleStartTime);
        final long clockWithZeroMillisecondsToGo = idleStartTime + idleMilliseconds;
        final long clockWithAMinuteExtra = idleStartTime + idleMilliseconds + msInAminute;
        when(instance.mock.currentMilliseconds()).thenReturn(clockWithZeroMillisecondsToGo, clockWithAMinuteExtra);

        // When
        final long actual1 = instance.check(mockComputer);
        final long actual2 = instance.check(mockComputer);

        // Then
        assertThat(actual1, equalTo(1L));
        assertThat(actual2, equalTo(1L));
        verify(instance.mock, times(2)).terminateContainer(mockComputer);
    }

    @Test
    public void taskAcceptedGivenTrivialTasksThenRecordsStartButDoesNotEnableTermination() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(1);
        final Executor mockLightweightExecutor = mock(OneOffExecutor.class);
        final Executor mockContinuingExecutor =
                mock(Executor.class, withSettings().extraInterfaces(ContinuableExecutable.class));
        when(((ContinuableExecutable) mockContinuingExecutor).willContinue()).thenReturn(true);
        final Executor mockHeavyExecutor = mock(Executor.class);
        final Task mockLightweightTask = mock(FlyweightTask.class);
        final Task mockHeavyTask = mock(Task.class);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(mockHeavyExecutor.getOwner()).thenReturn(mockComputer);
        when(mockLightweightExecutor.getOwner()).thenReturn(mockComputer);
        when(mockContinuingExecutor.getOwner()).thenReturn(mockComputer);

        // When
        instance.taskAccepted(mockHeavyExecutor, mockLightweightTask);
        instance.taskAccepted(mockLightweightExecutor, mockHeavyTask);
        instance.taskAccepted(mockContinuingExecutor, mockHeavyTask);

        // Then
        assertThat(instance.getNumberOfTasksInProgress(), equalTo(3));
        assertThat(instance.getTerminateOnceDone(), equalTo(false));
    }

    @Test
    public void taskAcceptedGivenHeavyweightThenRecordsStartAndEnablesTermination() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(1);
        final Executor mockExecutor = mock(Executor.class);
        final Task mockTask = mock(Task.class);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(mockExecutor.getOwner()).thenReturn(mockComputer);

        // When
        instance.taskAccepted(mockExecutor, mockTask);

        // Then
        assertThat(instance.getNumberOfTasksInProgress(), equalTo(1));
        assertThat(instance.getTerminateOnceDone(), equalTo(true));
    }

    @Test
    public void taskAcceptedGivenNonContinuingContinuableThenRecordsStartAndEnablesTermination() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(1);
        final Executor mockExecutor = mock(Executor.class, withSettings().extraInterfaces(ContinuableExecutable.class));
        when(((ContinuableExecutable) mockExecutor).willContinue()).thenReturn(false);
        final Task mockTask = mock(Task.class);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(mockExecutor.getOwner()).thenReturn(mockComputer);

        // When
        instance.taskAccepted(mockExecutor, mockTask);

        // Then
        assertThat(instance.getNumberOfTasksInProgress(), equalTo(1));
        assertThat(instance.getTerminateOnceDone(), equalTo(true));
    }

    @Test
    public void taskCompletedGivenTasksStillInProgressThenRecordsCompletionOnly() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(1);
        final Executor mockExecutor = mock(Executor.class);
        final Task mockTask = mock(Task.class);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(mockExecutor.getOwner()).thenReturn(mockComputer);
        instance.setNumberOfTasksInProgress(3);
        instance.setTerminateOnceDone(true);

        // When
        instance.taskCompleted(mockExecutor, mockTask, 123L);
        instance.taskCompletedWithProblems(mockExecutor, mockTask, 234L, new Throwable());

        // Then
        assertThat(instance.getNumberOfTasksInProgress(), equalTo(1));
        verify(instance.mock, never()).terminateContainer(mockComputer);
    }

    @Test
    public void taskCompletedGivenLastTaskAndTerminationNotEnabledThenRecordsCompletionOnly() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(1);
        final Executor mockExecutor = mock(Executor.class);
        final Task mockTask = mock(Task.class);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(mockExecutor.getOwner()).thenReturn(mockComputer);
        instance.setNumberOfTasksInProgress(1);
        instance.setTerminateOnceDone(false);

        // When
        instance.taskCompleted(mockExecutor, mockTask, 123L);

        // Then
        assertThat(instance.getNumberOfTasksInProgress(), equalTo(0));
        verify(instance.mock, never()).terminateContainer(mockComputer);
    }

    @Test
    public void taskCompletedGivenLastTaskAndTerminationEnabledThenRecordsCompletionAndTerminates() {
        // Given
        final ClassUnderTest instance = new ClassUnderTest(1);
        final Executor mockExecutor = mock(Executor.class);
        final Task mockTask = mock(Task.class);
        final DockerComputer mockComputer = mock(DockerComputer.class);
        when(mockExecutor.getOwner()).thenReturn(mockComputer);
        instance.setNumberOfTasksInProgress(1);
        instance.setTerminateOnceDone(true);

        // When
        instance.taskCompleted(mockExecutor, mockTask, 123L);

        // Then
        assertThat(instance.getNumberOfTasksInProgress(), equalTo(0));
        verify(instance.mock, times(1)).terminateContainer(mockComputer);
    }

    @Test
    public void testHashCodeAndEquals() {
        // hashCode & equals need to ignore the dynamic config and only pay attention to
        // the user-configured idle-time.

        // Given
        final ClassUnderTest same1 = new ClassUnderTest(123);
        final ClassUnderTest same2 = new ClassUnderTest(123);
        same2.setNumberOfTasksInProgress(234);
        final ClassUnderTest same3 = new ClassUnderTest(123);
        same3.setTerminateOnceDone(true);
        final ClassUnderTest same4 = new ClassUnderTest(123);
        same4.setNumberOfTasksInProgress(345);
        same4.setTerminateOnceDone(true);
        final ClassUnderTest diff1 = new ClassUnderTest(12);
        final ClassUnderTest diff2 = new ClassUnderTest(124);
        final ClassUnderTest diff3 = new ClassUnderTest(122);
        final ClassUnderTest diff4 = new ClassUnderTest(0);
        final ClassUnderTest[] same = {same1, same2, same3, same4};
        final ClassUnderTest[] diff = {diff1, diff2, diff3, diff4};

        // When/Then - hashCode
        for (final ClassUnderTest s1 : same) {
            for (final ClassUnderTest s2 : same) {
                assertThat(s1.hashCode(), equalTo(s2.hashCode()));
            }
            for (final ClassUnderTest d2 : diff) {
                assertThat(s1.hashCode(), not(equalTo(d2.hashCode())));
            }
        }
        // When/Then - equals
        for (final ClassUnderTest s1 : same) {
            for (final ClassUnderTest s2 : same) {
                assertThat(s1.equals(s2), equalTo(true));
            }
            for (final ClassUnderTest d2 : diff) {
                assertThat(s1.equals(d2), equalTo(false));
            }
            assertThat(s1.equals(null), equalTo(false));
            assertThat(s1.equals(new Object()), equalTo(false));
        }
    }

    public interface IClassUnderTest {
        long currentMilliseconds();

        boolean computerIsIdle(DockerComputer c);

        void terminateContainer(DockerComputer c);

        long computerIdleStartMilliseconds(DockerComputer c);

        String computerName(DockerComputer c);
    }

    public static class ClassUnderTest extends DockerOnceRetentionStrategy {
        private final IClassUnderTest mock;

        public ClassUnderTest(int idleMinutes) {
            super(idleMinutes);
            mock = mock(IClassUnderTest.class);
        }

        @Override
        protected long currentMilliseconds() {
            return mock.currentMilliseconds();
        }

        @Override
        protected boolean computerIsIdle(DockerComputer c) {
            return mock.computerIsIdle(c);
        }

        @Override
        protected void terminateContainer(DockerComputer c) {
            mock.terminateContainer(c);
        }

        @Override
        protected long computerIdleStartMilliseconds(DockerComputer c) {
            return mock.computerIdleStartMilliseconds(c);
        }

        @Override
        protected String computerName(DockerComputer c) {
            return mock.computerName(c);
        }
    }
}
