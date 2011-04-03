package ch.qos.logback.access.db.mongo;

import ch.qos.logback.access.dummy.DummyRequest;
import ch.qos.logback.access.dummy.DummyResponse;
import ch.qos.logback.access.dummy.DummyServerAdapter;
import ch.qos.logback.access.spi.AccessContext;
import ch.qos.logback.access.spi.AccessEvent;
import ch.qos.logback.core.status.StatusChecker;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;

/**
 * Requires MongoDB running on the same computer with default port (27017).
 *
 * @author Tomasz Nurkiewicz
 * @since 03.04.11, 17:37
 */
public class MongoDBAppenderTest {

  private final MongoDBAppender appender = new MongoDBAppender();
  private final AccessContext ac = new AccessContext();

  private Mongo mongo;
  private DBCollection mongoAccessEvents;

  @Before
  public void startMongoClient() throws UnknownHostException {
    mongo = new Mongo();
    mongoAccessEvents = mongo.getDB("db").getCollection("accessEvents");
    mongoAccessEvents.drop();
  }

  @Before
  public void setup() {
    appender.setContext(ac);
    appender.setW(1);
    appender.start();
  }

  @After
  public void cleanAndStopMongoClient() {
    mongoAccessEvents.drop();
    mongo.close();
  }

  @After
  public void tearDown() {
    appender.stop();
  }

  @Test
  public void smokeTest() throws Exception {
    //given
    final DummyRequest request = new DummyRequest();
    final DummyResponse response = new DummyResponse();

    //when
    appender.doAppend(new AccessEvent(request, response, new DummyServerAdapter(request, response)));

    //then
    assertThat(new StatusChecker(ac).isErrorFree()).isTrue();
  }

  @Test
  public void shouldSaveAccessEventInMongoDBWithCorrectTimeStamp() throws Exception {
    //given
    final DummyRequest request = new DummyRequest();
    final DummyResponse response = new DummyResponse();
    Date before = new Date();

    //when
    appender.doAppend(new AccessEvent(request, response, new DummyServerAdapter(request, response)));

    //then
    Date after = new Date();
    final DBObject access = loadAccessEventFromMongo();
    assertThat(access.keySet()).containsOnly("_id", "timeStamp", "server", "remote", "request", "response");
    assertThat(((Date) access.get("timeStamp")).getTime())
        .isGreaterThanOrEqualTo(before.getTime())
        .isLessThanOrEqualTo(after.getTime());
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void shouldSaveAccessEvent() throws Exception {
    final DummyRequest request = new DummyRequest();
    final String URI = "/a?b=c";
    request.setRequestUri(URI);
    final DummyResponse response = new DummyResponse();
    response.setStatus(404);

    //when
    appender.doAppend(new AccessEvent(request, response, new DummyServerAdapter(request, response)));

    //then
    final DBObject access = loadAccessEventFromMongo();
    assertThat(access.keySet()).containsOnly("_id", "timeStamp", "server", "remote", "request", "response");
    assertThat(access.get("server")).isEqualTo("testServerName");
    assertThat((Map<String, Object>) access.get("remote"))
        .hasSize(3)
        .includes(entry("host", "testHost"))
        .includes(entry("user", "testUser"))
        .includes(entry("addr", "testRemoteAddress"));
    assertThat((Map<String, Object>) access.get("request"))
        .hasSize(4)
        .includes(entry("uri", URI))
        .includes(entry("protocol", "testProtocol"))
        .includes(entry("method", "testMethod"))
        .includes(entry("postContent", "request contents"));
    assertThat((Map<String, Object>) access.get("response"))
        .hasSize(2)
        .includes(entry("contentLength", 1000L))
        .includes(entry("statusCode", 404));
  }

  private DBObject loadAccessEventFromMongo() throws InterruptedException {
    final DBCursor cursor = mongoAccessEvents.find();
    assertThat(cursor.count()).isEqualTo(1);
    return cursor.next();
  }

  @Test
  public void shouldStoreOnlyIdAndTimeStampWhenAllOtherItemsAreDisabled() throws Exception {
    //given
    final DummyRequest request = new DummyRequest();
    final DummyResponse response = new DummyResponse();

    appender.setServer(false);
    appender.setRequestProtocol(false);
    appender.setRequestProtocol(false);
    appender.setRequestMethod(false);
    appender.setRequestPostContent(false);
    appender.setRequestSessionId(false);
    appender.setRequestUserAgent(false);
    appender.setRequestReferer(false);
    appender.setRemoteHost(false);
    appender.setRemoteUser(false);
    appender.setRemoteAddr(false);
    appender.setResponseContentLength(false);
    appender.setResponseStatusCode(false);

    //when
    appender.doAppend(new AccessEvent(request, response, new DummyServerAdapter(request, response)));

    //then
    final DBObject access = loadAccessEventFromMongo();
    assertThat(access.keySet()).containsOnly("_id", "timeStamp");
  }


}
