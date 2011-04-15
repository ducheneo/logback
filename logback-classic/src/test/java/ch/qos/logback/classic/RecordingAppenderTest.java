package ch.qos.logback.classic;

import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Tomasz Nurkiewicz
 * @since 27.03.11, 21:38
 */
public class RecordingAppenderTest {

  private LoggerContext lc = new LoggerContext();
  private final Logger log = lc.getLogger(RecordingAppenderTest.class);
  private JoranConfigurator jc = new JoranConfigurator();

  @Before
  public void setup() throws JoranException {
    jc.setContext(lc);
  }

  @Test
  public void shouldNotLogAnythingWhenNoLogsCreated() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    //nothing

    //then
    assertThat(logMsgs()).isEmpty();
  }

  @Test
  public void shouldNotLogAnythingWhenSingleLogBelowError() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.warn("Test");

    //then
    assertThat(logMsgs()).isEmpty();
  }

  @Test
  public void shouldNotLogAnythingWhenMultipleLogsBelowError() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.debug("Test 1");
    log.info("Test 2");
    log.trace("Test 3");
    log.warn("Test 4");

    //then
    assertThat(logMsgs()).isEmpty();
  }

  @Test
  public void shouldLogOnlyErrorLogWhenNoPreviousLogs() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.error("Test");

    //then
    assertThat(logMsgs()).containsExactly("Test");
  }

  @Test
  public void shouldLogLastFewDebugLogsBeforeError() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.debug("Test 1");
    log.debug("Test 2");
    log.warn("Test 3");
    log.info("Test 4");
    log.error("Test 5");

    //then
    assertThat(logMsgs()).containsExactly("Test 2", "Test 3", "Test 4", "Test 5");
  }

  @Test
  public void shouldLimitOutputToLastThreeDetailedLogs() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.debug("Test 1");
    log.warn("Test 2");
    log.info("Test 3");
    log.debug("Test 4");
    log.warn("Test 5");
    log.error("Test 6");

    //then
    assertThat(logMsgs()).containsExactly("Test 3", "Test 4", "Test 5", "Test 6");
  }

  @Test
  public void shouldLimitOutputToRecentDetailedMessages() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.debug("Test 1");
    log.info("Test 2");
    TimeUnit.MILLISECONDS.sleep(150);
    log.debug("Test 3");
    log.info("Test 4");
    log.error("Test 5");

    //then
    assertThat(logMsgs()).containsExactly("Test 3", "Test 4", "Test 5");
  }

  @Test
  public void shouldCleanTheHistoryAfterDumping() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.debug("Test 1");
    log.info("Test 2");
    log.error("Test 3");

    log.error("Test 4");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 2", "Test 3", "Test 4");
  }

  @Test
  public void shouldRecordEventsAfterDumpProperly() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.info("Test 1");
    log.error("Test 2");

    log.info("Test 3");
    log.error("Test 4");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 2", "Test 3", "Test 4");
  }

  @Test
  public void shouldLimitRecordedEventsAfterFirstDumpBothAccordingToExpiry() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.info("Test 1");
    log.error("Test 2");

    log.info("Test 3");
    TimeUnit.MILLISECONDS.sleep(150);
    log.info("Test 4");
    log.info("Test 5");
    log.error("Test 6");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 2", "Test 4", "Test 5", "Test 6");
  }

  @Test
  public void shouldLimitRecordedEventsAfterFirstDumpBothAccordingToMaxSize() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    log.info("Test 1");
    log.error("Test 2");

    log.info("Test 3");
    log.info("Test 4");
    log.info("Test 5");
    log.info("Test 6");
    log.info("Test 7");
    log.error("Test 8");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 2", "Test 5", "Test 6", "Test 7", "Test 8");
  }

  @Test
  public void shouldAlsoDumpOnWarningIfConfiguredSo() throws Exception {
    //given
    configureFrom("recording-warn.xml");

    //when
    log.warn("Test");

    //then
    assertThat(logMsgs()).containsExactly("Test");
  }

  @Test
  public void shouldAlsoDumpOnWarningIncludingHistory() throws Exception {
    //given
    configureFrom("recording-warn.xml");

    //when
    log.info("Test 1");
    log.warn("Test 2");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 2");
  }

  @Test
  public void shouldWorkOnDefaultsSmokeTest() throws Exception {
    //given
    configureFrom("recording-defaults.xml");

    //when
    log.info("Test 1");
    log.warn("Test 2");
    log.error("Test 3");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 2", "Test 3");
  }

  @Test
  public void shouldDumpOnlyRecentLogsFromTriggeringThread() throws Exception {
    //given
    configureFrom("recording-defaults.xml");
    final ExecutorService executorService = Executors.newFixedThreadPool(10);

    //when
    log.debug("Test 1");
    logFewStatementsInDifferentThreads(executorService, 100);
    log.error("Test 3");

    //then
    assertThat(logMsgs()).containsExactly("Test 1", "Test 3");
  }

  private void logFewStatementsInDifferentThreads(ExecutorService executorService, final int count) throws InterruptedException {
    for (int i = 0; i < count; ++i)
      executorService.submit(new Runnable() {
        public void run() {
          log.info("Test 2");
        }
      });
    executorService.shutdown();
    executorService.awaitTermination(5, TimeUnit.SECONDS);
  }

  private void configureFrom(final String configFile) throws JoranException {
    jc.doConfigure(ClassicTestConstants.JORAN_INPUT_PREFIX + configFile);
  }

  private List<ILoggingEvent> logEvents() {
    return ((ListAppender<ILoggingEvent>) lc.getLogger("LIST_LOG").getAppender("LIST")).list;
  }

  private List<String> logMsgs() {
    final List<ILoggingEvent> events = logEvents();
    final ArrayList<String> msgs = new ArrayList<String>(events.size());
    for (ILoggingEvent event : events) {
      msgs.add(event.getMessage());
    }
    return msgs;
  }

  @Test
  public void shouldDumpLogMessagesComingFromDifferentMethodsAndClasses() throws Exception {
    //given
    configureFrom("recording.xml");

    //when
    someMethod("Testing");
    new RecordingAppenderHelper(log).otherMethod("Verifying");
    log.error("Oh, no!");

    //then
    assertThat(logMsgs()).containsExactly("Testing", "Verifying", "Oh, no!");

  }

  @Test
  public void shouldDumpCorrectCallerDataOfLogsComingFromDifferentMethodsAndClasses() throws Exception {
    configureFrom("recording-callerdata.xml");

    //when
    someMethod("Testing");
    new RecordingAppenderHelper(log).otherMethod("Verifying");
    log.error("Oh, no!");

    //then
    final List<ILoggingEvent> events = logEvents();
    assertThat(events).hasSize(3);
    assertCallerData(events.get(0), "ch.qos.logback.classic.RecordingAppenderTest", "RecordingAppenderTest.java", "someMethod");
    assertCallerData(events.get(1), "ch.qos.logback.classic.RecordingAppenderHelper", "RecordingAppenderHelper.java", "otherMethod");
    assertCallerData(events.get(2), "ch.qos.logback.classic.RecordingAppenderTest", "RecordingAppenderTest.java", "shouldDumpCorrectCallerDataOfLogsComingFromDifferentMethodsAndClasses");
  }

  @Test
  public void shouldDumpUnknownCallerDataWhenNotExplicitlyEnabled() throws Exception {
    configureFrom("recording-no-callerdata.xml");

    //when
    someMethod("Testing");
    new RecordingAppenderHelper(log).otherMethod("Verifying");
    log.error("Oh, no!");

    //then
    final List<ILoggingEvent> events = logEvents();
    assertThat(events).hasSize(3);
    assertThat(events.get(0).getCallerData()).isNull();
    assertThat(events.get(1).getCallerData()).isNull();
    assertThat(events.get(2).getCallerData()).isNull();
  }

  private void assertCallerData(final ILoggingEvent event, final String expectedClassName, final String expectedFileName, final String expectedMethodName) {
    final StackTraceElement[] callerData = event.getCallerData();
    assertThat(callerData).isNotNull();
    assertThat(callerData.length).isGreaterThan(0);

    final StackTraceElement ste = callerData[0];
    assertThat(ste.getClassName()).isEqualTo(expectedClassName);
    assertThat(ste.getFileName()).isEqualTo(expectedFileName);
    assertThat(ste.getMethodName()).isEqualTo(expectedMethodName);
  }

  private void someMethod(String msg) {
    log.debug(msg);
  }

}
