package ch.qos.logback.classic.db.mongo;

import ch.qos.logback.classic.ClassicTestConstants;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.StatusChecker;
import com.mongodb.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.MDC;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.fest.assertions.MapAssert.entry;

/**
 * Requires MongoDB running on the same computer with default port (27017).
 *
 * @author Tomasz Nurkiewicz
 * @since 02.04.11, 15:22
 */
public class MongoDBAppenderTest {

  @Rule
  public TestName testName = new TestName();

  static String MONGODB_FOLDER_PREFIX = ClassicTestConstants.JORAN_INPUT_PREFIX + "mongodb/";

  private LoggerContext lc = new LoggerContext();
  private Logger root = lc.getLogger(Logger.ROOT_LOGGER_NAME);
  private Logger log = lc.getLogger(this.getClass().getName());
  private StatusChecker sc = new StatusChecker(lc);

  private Mongo mongo;
  private DBCollection mongoEvents;

  @Before
  public void setup() throws UnknownHostException {
    mongo = new Mongo();
    mongoEvents = mongo.getDB("db").getCollection("loggingEvents");
    mongoEvents.drop();
  }

  @After
  public void tearDown() {
    MDC.clear();
    mongoEvents.drop();
    mongo.close();
    lc.stop();
  }

  private void configure(String file) throws JoranException {
    JoranConfigurator jc = new JoranConfigurator();
    jc.setContext(lc);
    jc.doConfigure(MONGODB_FOLDER_PREFIX + file);
  }

  @Test
  public void shouldNotStartAppenderWhenMongoServerNotFound() throws Exception {
    //given

    //when
    configure("server_failure.xml");

    //then
    assertThat(mongoAppender().isStarted()).isFalse();
  }

  private Appender<ILoggingEvent> mongoAppender() {
    return root.getAppender("MONGODB");
  }

  @Test
  public void shouldFailWhenLoggingAndMongoDBNotAvailable() throws Exception {
    //given
    configure("conn_failure.xml");
    assertThat(mongoAppender().isStarted()).isTrue();

    //when
    log.info(testName.getMethodName());

    //then
    assertThat(sc.isErrorFree()).isFalse();
    sc.containsException(IOException.class);
  }

  @Test
  public void shouldSaveInfoLogInMongoDB() throws Exception {
    //given
    configure("default.xml");

    //when
    log.info("Test: " + testName.getMethodName());

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "timeStamp", "level", "logger", "thread");
    assertThat(log.get("level")).isEqualTo("INFO");
    assertThat(log.get("thread")).isEqualTo("main");
    assertThat(log.get("logger")).isEqualTo("ch.qos.logback.classic.db.mongo.MongoDBAppenderTest");
    assertThat(log.get("message")).isEqualTo("Test: shouldSaveInfoLogInMongoDB");
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void shouldSaveLogIncludingCallerData() throws Exception {
    //given
    configure("caller_data.xml");

    //when
    log.info("Test: " + testName.getMethodName());

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "timeStamp", "callerData", "level", "logger", "thread");
    final List<Map<String, Object>> callerData = (List<Map<String, Object>>) log.get("callerData");
    assertThat(callerData.size()).isGreaterThan(1);
    assertThat(callerData.get(0))
        .includes(entry("file", "MongoDBAppenderTest.java"))
        .includes(entry("file", "MongoDBAppenderTest.java"))
        .includes(entry("class", "ch.qos.logback.classic.db.mongo.MongoDBAppenderTest"))
        .includes(entry("method", "shouldSaveLogIncludingCallerData"))
        .includes(entry("native", false));
  }

  @Test
  public void shouldSaveProperDate() throws Exception {
    //given
    configure("default.xml");
    final Date before = new Date();

    //when
    log.info("Test: " + testName.getMethodName());

    //then
    final Date after = new Date();
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "timeStamp", "level", "logger", "thread");
    assertThat(((Date) log.get("timeStamp")).getTime())
        .isGreaterThanOrEqualTo(before.getTime())
        .isLessThanOrEqualTo(after.getTime());
  }

  @Test
  public void shouldSaveExplicitLogArgument() throws Exception {
    configure("default.xml");

    //when
    log.warn("Test: {}", testName.getMethodName());

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "arguments", "timeStamp", "level", "logger", "thread");
    assertThat(log.get("level")).isEqualTo("WARN");
    assertThat(log.get("message")).isEqualTo("Test: shouldSaveExplicitLogArgument");
  }

  @Test
  @SuppressWarnings({"unchecked"})
  public void shouldSaveArgumentsOfDifferentTypes() throws Exception {
    configure("default.xml");
    final Date date = new Date();

    //when
    log.error("Test: {}, {} and {}", new Object[]{42, "foo", date});

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.get("level")).isEqualTo("ERROR");
    assertThat((List<Object>) log.get("arguments")).containsExactly(42, "foo", date);
  }

  @Test
  @SuppressWarnings({"unchecked"})
  public void shouldSaveMdcMap() throws Exception {
    //given
    configure("default.xml");
    MDC.put("sessionId", "XYZ");
    MDC.put("userId", "354");

    //when
    log.info("Test: " + testName.getMethodName());

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "mdc", "timeStamp", "level", "logger", "thread");
    assertThat((Map<String, Object>) log.get("mdc"))
        .hasSize(2)
        .includes(entry("sessionId", "XYZ"))
        .includes(entry("userId", "354"));
  }

  @Test
  @SuppressWarnings({"unchecked"})
  public void shouldSaveThrowableDetails() throws Exception {
    //given
    configure("default.xml");

    //when

    try {
      throw new IllegalArgumentException("Something went wrong");
    } catch (IllegalArgumentException e) {
      log.info(":-(", e);
    }

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "timeStamp", "level", "logger", "thread", "throwable");
    assertThat(log.get("message")).isEqualTo(":-(");
    final Map<String, Object> throwable = (Map<String, Object>) log.get("throwable");
    assertThrowableAndReturnStackTrace(throwable, "java.lang.IllegalArgumentException", "Something went wrong");
    assertThat(throwable.containsKey("cause")).isFalse();
  }

  @SuppressWarnings({"unchecked"})
  private List<String> assertThrowableAndReturnStackTrace(Map<String, Object> throwable, final String throwableClass, final String message) {
    assertThat(throwable)
        .isNotNull()
        .includes(entry("class", throwableClass))
        .includes(entry("message", message));
    return (List<String>) throwable.get("stackTrace");
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void shouldSaveThrowableStackTrace() throws Exception {
    //given
    configure("default.xml");

    //when
    try {
      throw new IllegalArgumentException("Something went wrong");
    } catch (IllegalArgumentException e) {
      log.info(":-(", e);
    }

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();
    assertThat(log.keySet()).containsOnly("_id", "message", "timeStamp", "level", "logger", "thread", "throwable");
    final Map<String, Object> throwable = (Map<String, Object>) log.get("throwable");
    final List<String> stackTrace = (List<String>) throwable.get("stackTrace");
    assertThat(stackTrace.size()).isGreaterThan(10);    //long JUnit stack...
    assertStackLineMethod(stackTrace.get(0), "shouldSaveThrowableStackTrace");
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void shouldSaveThrowableWithCauseStackTrace() throws Exception {
    //given
    configure("default.xml");
    final String FILE = "foo.db";

    //when
    try {
      try {
        throwSqlException(FILE);
      } catch (SQLException e) {
        throw new IllegalStateException("Persistence unavailable", e);
      }
    } catch (IllegalStateException e) {
      log.error(":-(", e);
    }

    //then
    final DBCursor cursor = loadEventsFromMongo(1);
    final DBObject log = cursor.next();

    final Map<String, Object> throwable = (Map<String, Object>) log.get("throwable");
    final List<String> stackTrace = assertThrowableAndReturnStackTrace(throwable, "java.lang.IllegalStateException", "Persistence unavailable");
    assertThat(stackTrace.size()).isGreaterThan(10);    //long JUnit stack...
    assertStackLineMethod(stackTrace.get(0), "shouldSaveThrowableWithCauseStackTrace");

    final Map<String, Object> cause = getCause(throwable);
    final List<String> causeStackTrace = assertThrowableAndReturnStackTrace(cause, "java.sql.SQLException", "Cannot open database: foo.db");
    assertThat(causeStackTrace).hasSize(2);
    assertStackLineMethod(causeStackTrace.get(0), "throwSqlException");
    assertStackLineMethod(causeStackTrace.get(1), "shouldSaveThrowableWithCauseStackTrace");

    final Map<String, Object> rootCause = getCause(cause);
    final List<String> rootCauseStackTrace = assertThrowableAndReturnStackTrace(rootCause, "java.io.FileNotFoundException", FILE);
    assertThat(rootCauseStackTrace).hasSize(3);
    assertStackLineMethod(rootCauseStackTrace.get(0), "innerThrowFileNotFoundException");
    assertStackLineMethod(rootCauseStackTrace.get(1), "throwFileNotFoundException");
    assertStackLineMethod(rootCauseStackTrace.get(2), "throwSqlException");

    assertThat(rootCause.containsKey("cause")).isFalse();
  }

  @SuppressWarnings({"unchecked"})
  private Map<String, Object> getCause(Map<String, Object> throwable) {
    return (Map<String, Object>) throwable.get("cause");
  }

  private void assertStackLineMethod(String stackLine, final String method) {
    assertThat(stackLine)
        .matches("ch\\.qos\\.logback\\.classic\\.db\\.mongo\\.MongoDBAppenderTest." + method + "\\(MongoDBAppenderTest\\.java:\\d+\\)");
  }

  private void throwSqlException(String file) throws SQLException {
    try {
      throwFileNotFoundException(file);
    } catch (FileNotFoundException e) {
      throw new SQLException("Cannot open database: " + file, e);
    }
  }

  private void throwFileNotFoundException(final String file) throws FileNotFoundException {
    innerThrowFileNotFoundException(file);
  }

  private void innerThrowFileNotFoundException(String file) throws FileNotFoundException {
    throw new FileNotFoundException(file);
  }


  private DBCursor loadEventsFromMongo(int expectedCount) throws InterruptedException {
    final DBCursor cursor = mongoEvents.find().sort(new BasicDBObject().append("message", 1));
    assertThat(cursor.count()).isEqualTo(expectedCount);
    return cursor;
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void shouldSaveMultipleEventsOnDifferentLevels() throws Exception {
    //given
    configure("default.xml");

    //when
    log.debug("A");
    log.info("B");
    log.warn("C");
    log.error("D");

    //then
    final DBCursor cursor = loadEventsFromMongo(4);
    assertLog(cursor.next(), "A", "DEBUG");
    assertLog(cursor.next(), "B", "INFO");
    assertLog(cursor.next(), "C", "WARN");
    assertLog(cursor.next(), "D", "ERROR");
  }

  private void assertLog(DBObject log, final String expectedMessage, final String expectedLevel) {
    assertThat(log.get("message")).isEqualTo(expectedMessage);
    assertThat(log.get("level")).isEqualTo(expectedLevel);
  }

  @Test
  public void allAppenderParametersSEtSmokeTest() throws Exception {
    //given

    //when
    configure("all_params.xml");

    //then
    assertThat(mongoAppender().isStarted()).isTrue();
  }

}
